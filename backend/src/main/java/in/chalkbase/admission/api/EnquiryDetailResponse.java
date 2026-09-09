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
import java.util.List;
import java.util.UUID;

/** One enquiry, in full, with its whole follow-up history — the detail screen's one read. */
public record EnquiryDetailResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String childFullName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        LocalDate childDateOfBirth,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID interestedClassId,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String interestedClassName,

        @Classification(Tier.CONFIDENTIAL) String parentName,
        @Classification(Tier.CONFIDENTIAL) String parentPhone,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String parentEmail,

        @Classification(Tier.INTERNAL) EnquirySource source,
        @Classification(Tier.INTERNAL) EnquiryStatus status,
        @Classification(Tier.INTERNAL) UUID assignedCounsellorId,
        @Classification(Tier.CONFIDENTIAL) String assignedCounsellorName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String remarks,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate nextFollowUpDate,

        @Classification(Tier.CONFIDENTIAL) String capturedByName,
        @Classification(Tier.INTERNAL) Instant createdAt,
        @Classification(Tier.INTERNAL) Instant updatedAt,
        @Classification(Tier.CONFIDENTIAL) List<EnquiryFollowUpResponse> followUps) {

    public EnquiryDetailResponse {
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }

    public static EnquiryDetailResponse of(
            Enquiry enquiry,
            String interestedClassName,
            String assignedCounsellorName,
            String capturedByName,
            List<EnquiryFollowUpResponse> followUps) {
        return new EnquiryDetailResponse(
                enquiry.getId(),
                enquiry.getChildFullName(),
                enquiry.getChildDateOfBirth(),
                enquiry.getInterestedClassId(),
                interestedClassName,
                enquiry.getParentName(),
                enquiry.getParentPhone(),
                enquiry.getParentEmail(),
                enquiry.getSource(),
                enquiry.getStatus(),
                enquiry.getAssignedCounsellorId(),
                assignedCounsellorName,
                enquiry.getRemarks(),
                enquiry.getNextFollowUpDate(),
                capturedByName,
                enquiry.getCreatedAt(),
                enquiry.getUpdatedAt(),
                followUps);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
