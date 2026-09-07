package in.chalkbase.document.api;

import in.chalkbase.document.application.DocumentContent;
import in.chalkbase.document.application.DocumentService;
import in.chalkbase.document.domain.DocumentType;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A student's certificates, photo, signature and other documents (FR-013, FR-032).
 *
 * <p>A top-level {@code /api/documents} prefix rather than nested under {@code /api/students/**} —
 * this is a different module's endpoint set, and module-map.md owns one prefix per module. A
 * student's documents are found by {@code ?studentId=}, the same shape {@code /api/audit} uses for
 * its own filters.
 *
 * <p><strong>{@code /content} is the one endpoint in this product that does not return
 * {@code ApiResponse<T>}.</strong> It answers with the file's own bytes and its own content type,
 * because that is what a browser needs to render or download it — ADR-0025 explains why this is a
 * proxied read rather than a signed URL redirect. Every other response here stays inside the
 * ADR-0007 envelope, failures included: a 403 or a 404 from this controller is exactly as JSON as
 * one from anywhere else.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PreAuthorize("hasAuthority('document:document:read')")
    @GetMapping
    public ApiResponse<List<DocumentSummary>> list(@RequestParam UUID studentId) {
        return ApiResponse.success(documents.list(studentId));
    }

    @PreAuthorize("hasAuthority('document:document:read')")
    @GetMapping("/{id}")
    public ApiResponse<DocumentSummary> get(@PathVariable UUID id) {
        return ApiResponse.success(documents.get(id));
    }

    /** The file itself. See the class Javadoc for why this is the one endpoint outside the envelope. */
    @PreAuthorize("hasAuthority('document:document:read')")
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        DocumentContent content = documents.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.filename())
                                .build()
                                .toString())
                .body(content.bytes());
    }

    /**
     * Uploads a new document. Multipart form fields rather than a JSON body, the same shape
     * {@code StudentImportController} uses for its file plus one field: a request that is mostly a
     * file does not want a nested JSON part for three scalars.
     */
    @PreAuthorize("hasAuthority('document:document:manage')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentSummary>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam UUID studentId,
            @RequestParam DocumentType documentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        DocumentSummary created = documents.upload(studentId, documentType, issueDate, expiryDate, file);
        return ResponseEntity.created(URI.create("/api/documents/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('document:document:manage')")
    @PutMapping("/{id}")
    public ApiResponse<DocumentSummary> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return ApiResponse.success(documents.update(id, request));
    }

    @PreAuthorize("hasAuthority('document:document:manage')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(id);
        return ResponseEntity.noContent().build();
    }
}
