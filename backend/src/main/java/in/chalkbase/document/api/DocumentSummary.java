package in.chalkbase.document.api;

import in.chalkbase.document.domain.Document;
import in.chalkbase.document.domain.DocumentType;
import in.chalkbase.document.domain.VerificationStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One document's metadata — never its bytes, which is what {@code GET /api/documents/{id}/content}
 * is for (ADR-0025).
 *
 * <p>Confidential under ADR-0014: a document's own type and original filename can be enough to
 * identify a child on their own. {@link #storageKey} is deliberately absent — a client has no
 * legitimate use for it, it is meaningless without the tenant prefix the storage adapter alone
 * supplies, and returning it would be handing out exactly the kind of location a signed URL would
 * have been, for no reason ADR-0025's proxied download does not already serve better.
 *
 * @param verified true once the office has checked this against the original; see
 *     {@link VerificationStatus}
 */
public record DocumentSummary(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) DocumentType documentType,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        LocalDate issueDate,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        LocalDate expiryDate,

        @Classification(Tier.INTERNAL) VerificationStatus verificationStatus,
        @Classification(Tier.CONFIDENTIAL) String originalFilename,
        @Classification(Tier.INTERNAL) String contentType,
        @Classification(Tier.INTERNAL) long sizeBytes,
        @Classification(Tier.INTERNAL) Instant createdAt) {

    public static DocumentSummary of(Document document) {
        return new DocumentSummary(
                document.getId(),
                document.getStudentId(),
                document.getDocumentType(),
                document.getIssueDate(),
                document.getExpiryDate(),
                document.getVerificationStatus(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getCreatedAt());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
