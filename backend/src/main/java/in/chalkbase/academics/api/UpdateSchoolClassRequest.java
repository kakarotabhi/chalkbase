package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A class renamed, retired or brought back.
 *
 * <p><strong>{@code sequence} is deliberately absent</strong>, for the reason it is absent from
 * {@code CreateSchoolClassRequest}: moving one class to a position another class holds is a
 * conflict, and the operation a school actually wants — "put these in this order" — is a single
 * transaction over the whole ladder.
 *
 * <p>{@code active} is a boxed {@code Boolean} with {@code @NotNull} rather than a primitive. A
 * primitive would default a missing field to {@code false}, so a client that forgot to send it
 * would silently retire the class rather than be told it sent an incomplete form.
 */
public record UpdateSchoolClassRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 40) String name,

        @Classification(Tier.INTERNAL) @NotNull Boolean active) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
