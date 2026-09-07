package in.chalkbase.document.api;

import in.chalkbase.document.domain.DocumentType;
import in.chalkbase.document.domain.VerificationStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * A document's metadata, corrected or verified. Never the file itself — replacing the bytes is a
 * new upload, on the grounds {@code Document}'s class Javadoc gives.
 *
 * <p>{@code issueDate} and {@code expiryDate} are both genuinely optional — a photo or a birth
 * certificate carries neither — so neither is {@code @NotNull}; a request omitting one simply clears
 * it, the same way {@code UpdateStudentGuardianRequest}'s optional fields behave.
 */
public record UpdateDocumentRequest(
        @Classification(Tier.CONFIDENTIAL) @NotNull DocumentType documentType,
        @Classification(Tier.CONFIDENTIAL) LocalDate issueDate,
        @Classification(Tier.CONFIDENTIAL) LocalDate expiryDate,
        @Classification(Tier.INTERNAL) @NotNull VerificationStatus verificationStatus) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
