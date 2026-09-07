package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceMark;
import in.chalkbase.attendance.domain.AttendanceStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.api.EnrolledStudentRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One row of a section's attendance for one date: a student, and their mark if one exists yet.
 *
 * <p>Confidential under ADR-0014, the same tier as a guardian's phone number: {@code fullName} and
 * {@code admissionNumber} each identify a child, and a status of {@code ABSENT} repeated across a
 * term is a pattern about a family, not a bare fact about one day.
 *
 * @param markId null until this student has been marked at all today. The marking screen tells
 *     "not yet marked" from "marked present" apart by this, not by treating a null status as
 *     absent.
 * @param status null alongside {@code markId} for the same reason.
 * @param editable false once this date has locked (end of day plus 24 hours). The screen shows a
 *     correction action instead of letting the status be changed directly.
 */
public record AttendanceStudentMark(
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String rollNumber,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID markId,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        AttendanceStatus status,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String remarks,

        @Classification(Tier.INTERNAL) boolean editable) {

    public static AttendanceStudentMark unmarked(EnrolledStudentRef student, boolean editable) {
        return new AttendanceStudentMark(
                student.studentId(),
                student.admissionNumber(),
                student.fullName(),
                student.rollNumber(),
                null,
                null,
                null,
                editable);
    }

    public static AttendanceStudentMark of(EnrolledStudentRef student, AttendanceMark mark, boolean editable) {
        return new AttendanceStudentMark(
                student.studentId(),
                student.admissionNumber(),
                student.fullName(),
                student.rollNumber(),
                mark.getId(),
                mark.getStatus(),
                mark.getRemarks(),
                editable);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
