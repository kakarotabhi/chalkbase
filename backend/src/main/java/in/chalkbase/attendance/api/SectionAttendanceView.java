package in.chalkbase.attendance.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A section's daily attendance for one date — the marking screen's whole read.
 *
 * @param locked true once this date's edit window has passed (end of day plus 24 hours). The
 *     screen disables direct editing and offers a correction request instead, per student.
 * @param entries the roster, in class-register order, each with its mark if one exists yet.
 */
public record SectionAttendanceView(
        @Classification(Tier.INTERNAL) UUID sectionId,
        @Classification(Tier.INTERNAL) String sectionName,
        @Classification(Tier.INTERNAL) String className,
        @Classification(Tier.INTERNAL) UUID academicSessionId,
        @Classification(Tier.INTERNAL) LocalDate attendanceDate,
        @Classification(Tier.INTERNAL) boolean locked,
        @Classification(Tier.CONFIDENTIAL) List<AttendanceStudentMark> entries) {

    public SectionAttendanceView {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
