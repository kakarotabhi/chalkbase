package in.chalkbase.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One certificate, photo, signature or other document attached to a student (FR-013, FR-032).
 *
 * <p><strong>{@link #studentId} is a plain {@code uuid}, not a {@code @ManyToOne}.</strong> That
 * row belongs to {@code student}, and mapping it as an association would put a {@code student.domain}
 * type inside this module's entities — a boundary crossing {@code ModularityTests} refuses, and
 * this module map forbids. The same shape {@code student_enrolment} uses for
 * {@code academic_session_id} and {@code section_id}. The foreign key is still in the database
 * ({@code fk_document_student}), where it belongs; what is absent is the Java coupling.
 *
 * <p><strong>{@link #storageKey} is tenant-relative.</strong> It never contains the school's schema
 * name — the row already lives inside that schema, so recording it again would be redundant and
 * could drift from it. {@code platform.storage.StorageService}'s adapter is what turns this into a
 * real location, by reading the tenant that is already bound to the request (ADR-0025).
 *
 * <p>Never built from a client-supplied filename: see {@code DocumentService#upload} for why the
 * key is a generated id plus a sniffed extension, never {@link #originalFilename}.
 *
 * <p>Confidential under ADR-0014, every field. {@link #documentType}'s own Javadoc says why no
 * Restricted-category type exists to be stored here at all.
 */
@Entity
@Table(name = "document")
public class Document {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 300)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64)
    private String checksumSha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Document() {
        // for JPA
    }

    public Document(
            UUID studentId,
            DocumentType documentType,
            LocalDate issueDate,
            LocalDate expiryDate,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String checksumSha256) {
        this.studentId = studentId;
        this.documentType = documentType;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
    }

    /** Edits the metadata a school may correct after the fact. Never the file itself — see the class Javadoc. */
    public void editDetails(DocumentType documentType, LocalDate issueDate, LocalDate expiryDate) {
        this.documentType = documentType;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.updatedAt = Instant.now();
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
