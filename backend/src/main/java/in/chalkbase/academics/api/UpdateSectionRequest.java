package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A section renamed, retired or brought back.
 *
 * <p>The class it divides is not editable. Moving "A" from Class 5 to Class 6 is not an edit to a
 * section — it is a different section, and treating it as an edit would carry every enrolment that
 * names it across without anyone asking for that.
 *
 * <p>{@code active} is boxed and {@code @NotNull} so that a client which omits it is told, rather
 * than silently retiring the section.
 */
public record UpdateSectionRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 20) String name,

        @Classification(Tier.INTERNAL) @NotNull Boolean active) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
