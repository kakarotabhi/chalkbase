package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A teacher's request to change a locked mark. {@code reason} is required: an admin decides on it. */
public record RequestCorrectionRequest(
        @Classification(Tier.CONFIDENTIAL) @NotNull AttendanceStatus requestedStatus,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 500) String reason) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
