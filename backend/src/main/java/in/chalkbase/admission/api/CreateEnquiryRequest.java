package in.chalkbase.admission.api;

import in.chalkbase.admission.domain.EnquirySource;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Capturing one enquiry (FR-016).
 *
 * @param interestedClassId optional — a walk-in may not yet know, or care, which class; when given
 *     it must name a class this school still has ({@code AdmissionErrorCode} has no code of its
 *     own for this because it is a plain {@code NotFoundException}, the same as an unknown section
 *     id is elsewhere).
 * @param parentPhone loosely bounded rather than pattern-matched, the same choice
 *     {@code SaveGuardianRequest.phone} makes: Indian numbers arrive with a country code, spaces
 *     and dashes depending on who is typing.
 * @param assignedCounsellorId <strong>required, not optional.</strong> See {@code Enquiry}'s class
 *     Javadoc for why: an enquiry with nobody assigned to it is the mailbox this module exists to
 *     prevent, so the form that captures one asks who owns its follow-up in the same breath.
 */
public record CreateEnquiryRequest(
        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String childFullName,

        @Classification(Tier.CONFIDENTIAL) @Past LocalDate childDateOfBirth,
        @Classification(Tier.INTERNAL) UUID interestedClassId,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String parentName,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 20) String parentPhone,

        @Classification(Tier.CONFIDENTIAL) @Email @Size(max = 200) String parentEmail,

        @Classification(Tier.INTERNAL) @NotNull EnquirySource source,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 1000) String remarks,
        @Classification(Tier.INTERNAL) @NotNull UUID assignedCounsellorId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
