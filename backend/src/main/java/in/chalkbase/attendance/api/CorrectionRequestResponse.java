package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceCorrectionRequest;
import in.chalkbase.attendance.domain.AttendanceStatus;
import in.chalkbase.attendance.domain.CorrectionDecision;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One correction request, as the admin queue and a teacher's own history both read it.
 *
 * <p>Carries the student and the date directly rather than only {@code attendanceMarkId}, because
 * every screen that lists these is a queue someone reads to decide something — "whose attendance,
 * for which day" has to be on the row, not behind a second call per item.
 */
public record CorrectionRequestResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID attendanceMarkId,
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String studentName,
        @Classification(Tier.INTERNAL) java.time.LocalDate attendanceDate,
        @Classification(Tier.CONFIDENTIAL) AttendanceStatus previousStatus,
        @Classification(Tier.CONFIDENTIAL) AttendanceStatus requestedStatus,
        @Classification(Tier.CONFIDENTIAL) String reason,
        @Classification(Tier.INTERNAL) Instant requestedAt,
        @Classification(Tier.INTERNAL) CorrectionDecision decision,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant decidedAt,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String decisionNote) {

    public static CorrectionRequestResponse of(
            AttendanceCorrectionRequest request,
            UUID studentId,
            String studentName,
            java.time.LocalDate attendanceDate) {
        return new CorrectionRequestResponse(
                request.getId(),
                request.getAttendanceMarkId(),
                studentId,
                studentName,
                attendanceDate,
                request.getPreviousStatus(),
                request.getRequestedStatus(),
                request.getReason(),
                request.getRequestedAt(),
                request.getDecision(),
                request.getDecidedAt(),
                request.getDecisionNote());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
