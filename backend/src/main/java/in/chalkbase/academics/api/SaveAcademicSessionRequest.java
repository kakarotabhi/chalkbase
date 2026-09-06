package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * An academic year, created or edited.
 *
 * <p>One record for both, because the two forms are the same form: there is no field a school may
 * set once and never change, and a separate {@code CreateAcademicSessionRequest} identical to this
 * one would be two places to add the next field to.
 *
 * <p><strong>{@code current} is deliberately not here.</strong> Which year the school is in is
 * mutually exclusive across rows, so setting it is its own endpoint that clears the previous one in
 * the same transaction. If it were a field on this form, two edit screens saved a second apart
 * would disagree about which year the school is in, and the partial unique index would answer the
 * second one with a conflict nobody asked for.
 */
@EndsAfterStart
public record SaveAcademicSessionRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 40) String name,

        @Classification(Tier.INTERNAL) @NotNull LocalDate startsOn,
        @Classification(Tier.INTERNAL) @NotNull LocalDate endsOn) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
