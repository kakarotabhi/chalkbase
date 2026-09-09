package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.LeaveDecision;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An authorised person's decision on a leave request.
 *
 * @param decision must be {@code APPROVED} or {@code REJECTED} — {@code PENDING} is refused by the
 *     service, the same shape {@link DecideCorrectionRequest#decision} refuses it.
 */
public record DecideLeaveRequest(
        @Classification(Tier.INTERNAL) @NotNull LeaveDecision decision,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 500) String note) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
