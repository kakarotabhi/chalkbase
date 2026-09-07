package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.CorrectionDecision;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An administrator's decision on a correction request.
 *
 * @param decision must be {@code APPROVED} or {@code REJECTED} — {@code PENDING} is refused by the
 *     service the same way a client sending {@code active: null} is refused elsewhere: this is a
 *     decision being recorded, not a request being reopened.
 */
public record DecideCorrectionRequest(
        @Classification(Tier.INTERNAL) @NotNull CorrectionDecision decision,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 500) String note) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
