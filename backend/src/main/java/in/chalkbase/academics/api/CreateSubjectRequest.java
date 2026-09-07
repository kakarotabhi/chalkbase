package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new subject.
 *
 * <p>No {@code active}: a subject is created active, the same as a class (see
 * {@code CreateSchoolClassRequest}). Creating something already retired is not a thing a school
 * does, and the edit form is where it is retired.
 */
public record CreateSubjectRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 80) String name,

        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 20) String code) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
