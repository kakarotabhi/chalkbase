package in.chalkbase.communication.api;

import in.chalkbase.communication.domain.CircularRecipient;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One student a published circular reached, as the recipient screen shows it — Confidential under
 * ADR-0014, the same tier every other name-carrying cross-module row in this codebase uses
 * ({@code attendance.api.AttendanceStudentMark}, {@code student.api.EnrolledStudentRef}).
 *
 * <p>Carries no acknowledger's name — only {@code acknowledgedAt} and the note, if any — the same
 * choice {@code attendance.api.CorrectionRequestResponse} makes for {@code decidedBy}: who recorded
 * it is audit-log material (ADR-0018), not a field on the row itself.
 */
public record CircularRecipientResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String studentFullName,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.INTERNAL) UUID sectionId,
        @Classification(Tier.INTERNAL) String sectionName,
        @Classification(Tier.INTERNAL) String className,
        @Classification(Tier.INTERNAL) Instant deliveredAt,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant acknowledgedAt,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String acknowledgementNote) {

    public static CircularRecipientResponse of(
            CircularRecipient recipient,
            String studentFullName,
            String admissionNumber,
            String sectionName,
            String className) {
        return new CircularRecipientResponse(
                recipient.getId(),
                recipient.getStudentId(),
                studentFullName,
                admissionNumber,
                recipient.getSectionId(),
                sectionName,
                className,
                recipient.getDeliveredAt(),
                recipient.getAcknowledgedAt(),
                recipient.getAcknowledgementNote());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
