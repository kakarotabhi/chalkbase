package in.chalkbase.admission.api;

import in.chalkbase.admission.domain.EnquiryStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A counsellor's dated note against one enquiry (FR-017).
 *
 * @param nextFollowUpDate when to follow up next. Required unless {@code resultingStatus} closes
 *     the enquiry ({@link EnquiryStatus#CONVERTED} or {@link EnquiryStatus#LOST}) —
 *     {@code Enquiry.applyFollowUp} is what enforces that, because it is a rule about the
 *     enquiry's own state after this follow-up applies, not about this request in isolation.
 *     {@link FutureOrPresent} is this field's own refusal of a backdated one, the same relationship
 *     {@code MarkAttendanceRequest.attendanceDate}'s {@code @PastOrPresent} has to
 *     {@code AttendanceErrorCode.FUTURE_DATE_NOT_ALLOWED} — a scheduled date and a diary entry point
 *     in opposite directions.
 * @param resultingStatus the status this follow-up moves the enquiry to, or null to log a note with
 *     no status change — {@link EnquiryStatus#NEW} is refused by
 *     {@code AdmissionErrorCode.STATUS_CANNOT_REOPEN_TO_NEW}, since a follow-up can only move an
 *     enquiry forward or close it.
 */
public record LogFollowUpRequest(
        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 1000) String note,

        @Classification(Tier.INTERNAL) @FutureOrPresent LocalDate nextFollowUpDate,
        @Classification(Tier.INTERNAL) EnquiryStatus resultingStatus) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
