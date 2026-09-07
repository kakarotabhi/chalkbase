package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A subject renamed, recoded, retired or brought back.
 *
 * <p>{@code active} is a boxed {@code Boolean} with {@code @NotNull} rather than a primitive, as on
 * {@code UpdateSchoolClassRequest}: a primitive would default a missing field to {@code false}, so
 * a client that forgot to send it would silently retire the subject rather than be told it sent an
 * incomplete form.
 */
public record UpdateSubjectRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 80) String name,

        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 20) String code,

        @Classification(Tier.INTERNAL) @NotNull Boolean active) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
