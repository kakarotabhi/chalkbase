package in.chalkbase.admission.api;

import in.chalkbase.admission.domain.EnquiryFollowUp;
import in.chalkbase.admission.domain.EnquiryStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One entry of an enquiry's follow-up history — who, when, what was said, and what happens next. */
public record EnquiryFollowUpResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String note,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate nextFollowUpDate,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        EnquiryStatus resultingStatus,

        @Classification(Tier.CONFIDENTIAL) String recordedByName,
        @Classification(Tier.INTERNAL) Instant recordedAt) {

    public static EnquiryFollowUpResponse of(EnquiryFollowUp followUp, String recordedByName) {
        return new EnquiryFollowUpResponse(
                followUp.getId(),
                followUp.getNote(),
                followUp.getNextFollowUpDate(),
                followUp.getResultingStatus(),
                recordedByName,
                followUp.getRecordedAt());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
