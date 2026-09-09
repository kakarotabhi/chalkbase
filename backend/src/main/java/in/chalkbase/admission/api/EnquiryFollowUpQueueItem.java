package in.chalkbase.admission.api;

import in.chalkbase.admission.domain.Enquiry;
import in.chalkbase.admission.domain.EnquiryStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the due-date follow-up queue — the thing that makes this module more than a mailbox.
 * See {@code EnquiryRepository.findDueFollowUps} for exactly which enquiries qualify.
 *
 * @param nextFollowUpDate never null here: {@code findDueFollowUps} only ever returns a row that has
 *     one, unlike {@link EnquirySummary#nextFollowUpDate}, which is null for a closed enquiry.
 * @param overdue true when {@code nextFollowUpDate} is strictly before today — due today reads as
 *     due, not yet overdue, the same distinction a fee dues screen draws between due and overdue.
 */
public record EnquiryFollowUpQueueItem(
        @Classification(Tier.INTERNAL) UUID enquiryId,
        @Classification(Tier.CONFIDENTIAL) String childFullName,
        @Classification(Tier.CONFIDENTIAL) String parentName,
        @Classification(Tier.CONFIDENTIAL) String parentPhone,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String interestedClassName,

        @Classification(Tier.INTERNAL) EnquiryStatus status,
        @Classification(Tier.CONFIDENTIAL) String assignedCounsellorName,
        @Classification(Tier.INTERNAL) LocalDate nextFollowUpDate,
        @Classification(Tier.INTERNAL) boolean overdue) {

    public static EnquiryFollowUpQueueItem of(
            Enquiry enquiry, String interestedClassName, String assignedCounsellorName, LocalDate today) {
        return new EnquiryFollowUpQueueItem(
                enquiry.getId(),
                enquiry.getChildFullName(),
                enquiry.getParentName(),
                enquiry.getParentPhone(),
                interestedClassName,
                enquiry.getStatus(),
                assignedCounsellorName,
                enquiry.getNextFollowUpDate(),
                enquiry.getNextFollowUpDate().isBefore(today));
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
