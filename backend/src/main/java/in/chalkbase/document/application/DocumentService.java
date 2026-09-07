package in.chalkbase.document.application;

import in.chalkbase.document.api.DocumentSummary;
import in.chalkbase.document.api.UpdateDocumentRequest;
import in.chalkbase.document.domain.Document;
import in.chalkbase.document.domain.DocumentAudit;
import in.chalkbase.document.domain.DocumentErrorCode;
import in.chalkbase.document.domain.DocumentType;
import in.chalkbase.document.infrastructure.DocumentRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.storage.ObjectNotFoundException;
import in.chalkbase.platform.storage.StorageService;
import in.chalkbase.platform.storage.StoredObject;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A student's certificates, photo, signature and other documents (FR-013, FR-032, ADR-0025).
 *
 * <p>Every method works in the school bound to this request and can reach no other: the
 * connection's {@code search_path} is what selects the schema (ADR-0011). {@link #upload} names no
 * check against the student register at all — {@code fk_document_student} is the check, enforced by
 * the database, which is what keeps this module free of any Java dependency on {@code student}
 * (see the module's {@code package-info}).
 *
 * <p><strong>Object-store-then-row, for create and for delete alike</strong> (ADR-0025): bytes are
 * written before the metadata row that would let anyone find them, and deleted before the row that
 * would stop pointing at them. Either way, a failure partway through leaves the row as the side that
 * can safely lag — see the class-level Javadoc on that decision in ADR-0025 for why that direction
 * and not the other.
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    /** The kinds of file this build accepts, identified by their first bytes rather than trusted from the client. */
    private enum SniffedType {
        PDF(new byte[] {0x25, 0x50, 0x44, 0x46}, "application/pdf", "pdf"),
        JPEG(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg", "jpg"),
        PNG(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "image/png", "png");

        private final byte[] magic;
        private final String contentType;
        private final String extension;

        SniffedType(byte[] magic, String contentType, String extension) {
            this.magic = magic;
            this.contentType = contentType;
            this.extension = extension;
        }

        static SniffedType detect(byte[] content) {
            for (SniffedType type : values()) {
                if (content.length >= type.magic.length
                        && Arrays.equals(content, 0, type.magic.length, type.magic, 0, type.magic.length)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final DocumentRepository documents;
    private final StorageService storage;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, StorageService storage, AuditService audit) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    /** Every document a student has, newest first. Bounded per student, so this is not paged. */
    public List<DocumentSummary> list(UUID studentId) {
        return documents.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(DocumentSummary::of)
                .toList();
    }

    public DocumentSummary get(UUID id) {
        return DocumentSummary.of(requireDocument(id));
    }

    /**
     * The bytes behind one document, for {@code GET /api/documents/{id}/content} to stream back —
     * the only way this product ever hands out a document's content (ADR-0025: no signed URL).
     *
     * <p>Audited as an export, the same as a masked CSV or Excel export would be under ADR-0014:
     * this is the moment a Confidential document leaves the application into whatever the caller
     * does with the response.
     */
    public DocumentContent content(UUID id) {
        Document document = requireDocument(id);
        byte[] bytes;
        try {
            bytes = storage.retrieve(document.getStorageKey());
        } catch (ObjectNotFoundException e) {
            throw new ChalkbaseException(DocumentErrorCode.CONTENT_MISSING);
        }
        audit.recordSecurityEvent(
                AuditAction.DATA_EXPORTED, AuditOutcome.SUCCESS, DocumentAudit.DOCUMENT, id.toString());
        return new DocumentContent(bytes, document.getContentType(), document.getOriginalFilename());
    }

    /**
     * Stores a new document against a student.
     *
     * <p><strong>The storage key is never built from the client's filename.</strong> A generated id
     * plus the sniffed extension only — a filename is attacker-controlled text and this key becomes
     * a filesystem or object-store path segment, so accepting one directly would be exactly the kind
     * of trust {@code FilesystemStorageService#resolve}'s containment check exists to catch, one
     * layer earlier and for free. The original filename is kept, but only ever as a display value in
     * {@link DocumentSummary} and as a suggested download name — never interpreted as a path.
     */
    @Transactional
    public DocumentSummary upload(
            UUID studentId, DocumentType documentType, LocalDate issueDate, LocalDate expiryDate, MultipartFile file) {
        validateDates(issueDate, expiryDate);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ChalkbaseException(DocumentErrorCode.FILE_UNREADABLE);
        }
        if (content.length == 0) {
            throw new ChalkbaseException(DocumentErrorCode.FILE_EMPTY);
        }
        SniffedType sniffed = SniffedType.detect(content);
        if (sniffed == null) {
            throw new ChalkbaseException(DocumentErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String relativeKey = "documents/" + UUID.randomUUID() + "." + sniffed.extension;
        StoredObject stored = storage.store(relativeKey, content, sniffed.contentType);

        Document created = documents.saveAndFlush(new Document(
                studentId,
                documentType,
                issueDate,
                expiryDate,
                stored.relativeKey(),
                originalFilename(file),
                sniffed.contentType,
                stored.sizeBytes(),
                stored.checksumSha256()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                DocumentAudit.DOCUMENT,
                created.getId().toString(),
                List.of(
                        "studentId",
                        "documentType",
                        "issueDate",
                        "expiryDate",
                        "originalFilename",
                        "contentType",
                        "sizeBytes"));

        return DocumentSummary.of(created);
    }

    /** Corrects a document's type, dates or verification status. Never the file itself. */
    @Transactional
    public DocumentSummary update(UUID id, UpdateDocumentRequest request) {
        Document document = requireDocument(id);
        validateDates(request.issueDate(), request.expiryDate());

        Set<String> changed = new LinkedHashSet<>();
        if (document.getDocumentType() != request.documentType()) {
            changed.add("documentType");
        }
        if (!Objects.equals(document.getIssueDate(), request.issueDate())) {
            changed.add("issueDate");
        }
        if (!Objects.equals(document.getExpiryDate(), request.expiryDate())) {
            changed.add("expiryDate");
        }
        if (document.getVerificationStatus() != request.verificationStatus()) {
            changed.add("verificationStatus");
        }
        if (changed.isEmpty()) {
            return DocumentSummary.of(document);
        }

        document.editDetails(request.documentType(), request.issueDate(), request.expiryDate());
        document.setVerificationStatus(request.verificationStatus());
        documents.saveAndFlush(document);

        audit.recordChange(AuditAction.ENTITY_UPDATED, DocumentAudit.DOCUMENT, id.toString(), changed);

        return DocumentSummary.of(document);
    }

    /**
     * Removes a document, bytes first (ADR-0025). A wrongly attached file — the wrong student, the
     * wrong certificate entirely — is the one case in this module with a real {@code DELETE}: unlike
     * the student record itself, nothing else in the product references a document, so there is no
     * legal-retention argument against removing one, the way there is for {@code Student}.
     */
    @Transactional
    public void delete(UUID id) {
        Document document = requireDocument(id);

        storage.delete(document.getStorageKey());
        documents.delete(document);
        documents.flush();

        audit.recordChange(AuditAction.ENTITY_DELETED, DocumentAudit.DOCUMENT, id.toString(), List.of());
    }

    private void validateDates(LocalDate issueDate, LocalDate expiryDate) {
        if (issueDate != null && expiryDate != null && !expiryDate.isAfter(issueDate)) {
            throw new ChalkbaseException(DocumentErrorCode.EXPIRY_BEFORE_ISSUE);
        }
    }

    private Document requireDocument(UUID id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("Document", id));
    }

    /**
     * The name to show and to suggest on download — trimmed and capped to the column width, never
     * used as a path. A missing or blank name (some clients omit it) falls back to something a
     * screen can still render rather than an empty string.
     */
    private static String originalFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "document";
        }
        name = name.strip();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
