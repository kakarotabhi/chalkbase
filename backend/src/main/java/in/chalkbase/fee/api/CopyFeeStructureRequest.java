package in.chalkbase.fee.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * "Copy last year's fee structure into this one" — the new-session convenience action ADR-0033
 * argues for over both "start empty" and a silent automatic copy.
 *
 * <p>Copies every class that has a current structure in {@code fromSessionId} and does not
 * already have one in {@code toSessionId}. A class that already has a structure in the
 * destination session is left exactly as it is — this action never overwrites anything a school
 * has already entered, which is also why it takes no per-class selection: skipping is always the
 * safe default, never a choice the caller has to get right.
 */
public record CopyFeeStructureRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID fromSessionId,
        @Classification(Tier.INTERNAL) @NotNull UUID toSessionId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
