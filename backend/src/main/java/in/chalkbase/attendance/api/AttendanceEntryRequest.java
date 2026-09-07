package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** One student's status within a {@link MarkAttendanceRequest}. */
public record AttendanceEntryRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID studentId,
        @Classification(Tier.CONFIDENTIAL) @NotNull AttendanceStatus status,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 500) String remarks) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
