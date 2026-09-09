package in.chalkbase.attendance.api;

import in.chalkbase.attendance.domain.AttendanceLeaveRequest;
import in.chalkbase.attendance.domain.LeaveDecision;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One leave request, as the queue and a single request's own screen both read it.
 *
 * <p>Carries the student's name and the section's class/section names directly rather than only
 * ids, the same reasoning {@link CorrectionRequestResponse} gives: every screen that lists these is
 * a queue someone reads to decide something. {@code requestedBy} and {@code decidedBy} are
 * deliberately absent, matching {@code CorrectionRequestResponse} exactly — this is a record of what
 * was asked and decided, not a directory of which staff account did it; that answer lives in the
 * audit log for whoever is allowed to ask it (ADR-0018).
 *
 * <p><strong>{@code reason} is Confidential, not Restricted.</strong> ADR-0014 tags health and
 * counselling records Restricted, and a leave reason may well name one — "fever", "chicken pox",
 * "minor surgery". But Restricted also means encrypted at rest and revealed only through a second,
 * audited endpoint (ADR-0022), machinery this lane does not build and does not need to: this field
 * is a free-text sentence a class teacher or the office writes down on a parent's behalf, the same
 * shape as {@link RequestCorrectionRequest#reason}, which sits at Confidential for the identical
 * reason and can equally well contain "she was unwell that morning". A school that wants
 * structured, Restricted-grade medical leave capture is asking for a different field with its own
 * encryption story, not a reclassification of this one.
 */
public record LeaveRequestResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String studentName,
        @Classification(Tier.INTERNAL) UUID sectionId,
        @Classification(Tier.INTERNAL) String sectionName,
        @Classification(Tier.INTERNAL) String className,
        @Classification(Tier.INTERNAL) LocalDate startDate,
        @Classification(Tier.INTERNAL) LocalDate endDate,
        @Classification(Tier.CONFIDENTIAL) String reason,
        @Classification(Tier.INTERNAL) Instant requestedAt,
        @Classification(Tier.INTERNAL) LeaveDecision decision,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant decidedAt,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String decisionNote) {

    public static LeaveRequestResponse of(
            AttendanceLeaveRequest request, String studentName, String sectionName, String className) {
        return new LeaveRequestResponse(
                request.getId(),
                request.getStudentId(),
                studentName,
                request.getSectionId(),
                sectionName,
                className,
                request.getStartDate(),
                request.getEndDate(),
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
