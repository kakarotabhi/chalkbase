package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceMark;
import in.chalkbase.attendance.domain.AttendanceStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** One day of one student's attendance history. */
public record StudentAttendanceRecord(
        @Classification(Tier.INTERNAL) UUID markId,
        @Classification(Tier.INTERNAL) LocalDate attendanceDate,
        @Classification(Tier.CONFIDENTIAL) AttendanceStatus status,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String remarks,

        @Classification(Tier.INTERNAL) boolean editable) {

    public static StudentAttendanceRecord of(AttendanceMark mark, boolean editable) {
        return new StudentAttendanceRecord(
                mark.getId(), mark.getAttendanceDate(), mark.getStatus(), mark.getRemarks(), editable);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
