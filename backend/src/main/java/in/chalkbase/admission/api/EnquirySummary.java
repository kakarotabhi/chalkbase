package in.chalkbase.admission.api;

import in.chalkbase.admission.domain.Enquiry;
import in.chalkbase.admission.domain.EnquirySource;
import in.chalkbase.admission.domain.EnquiryStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the enquiry list — enough to work the front desk from, without opening the record.
 *
 * @param interestedClassName null when the enquiry named no class, or named one this school has
 *     since retired past the point {@code AcademicsLookup.classes()} still resolves it (it does not
 *     — retired classes stay listed — so this is really only ever null for "no class given").
 * @param nextFollowUpDate null once the enquiry has closed ({@link EnquiryStatus#isClosed()}).
 */
public record EnquirySummary(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String childFullName,
        @Classification(Tier.CONFIDENTIAL) String parentName,
        @Classification(Tier.CONFIDENTIAL) String parentPhone,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String interestedClassName,

        @Classification(Tier.INTERNAL) EnquirySource source,
        @Classification(Tier.INTERNAL) EnquiryStatus status,
        @Classification(Tier.CONFIDENTIAL) String assignedCounsellorName,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate nextFollowUpDate,

        @Classification(Tier.INTERNAL) Instant createdAt) {

    public static EnquirySummary of(Enquiry enquiry, String interestedClassName, String assignedCounsellorName) {
        return new EnquirySummary(
                enquiry.getId(),
                enquiry.getChildFullName(),
                enquiry.getParentName(),
                enquiry.getParentPhone(),
                interestedClassName,
                enquiry.getSource(),
                enquiry.getStatus(),
                assignedCounsellorName,
                enquiry.getNextFollowUpDate(),
                enquiry.getCreatedAt());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
