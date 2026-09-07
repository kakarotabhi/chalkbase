package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Size;

/**
 * A student's health record, entered or corrected (FR-034). One shape for both.
 *
 * <p>Six Restricted fields and three Confidential ones, mixed in one request the way
 * {@code student_medical} mixes them in one table — see that migration's comment for why they share
 * a table despite the different tiers. Every field is optional: a school rarely has all nine on day
 * one, and a section that refused a partial answer would refuse the common case.
 *
 * <p><strong>Writing here does not require a prior reveal.</strong> An office that has a UDISE+ form
 * in hand types the blood group in without ever having asked to see what was there before — that is
 * an ordinary data-entry act, not a read, and {@code student:student:manage} is what gates it.
 * Pre-filling an edit form with what is already on file is a different action and goes through
 * {@code GET /api/students/{id}/medical/restricted} first, which is the one that is audited.
 */
public record SaveMedicalRequest(
        @Classification(Tier.RESTRICTED) @Size(max = 40) String bloodGroup,
        @Classification(Tier.RESTRICTED) @Size(max = 100) String cwsnStatus,
        @Classification(Tier.RESTRICTED) @Size(max = 2000) String disabilityDetails,
        @Classification(Tier.RESTRICTED) @Size(max = 2000) String allergies,
        @Classification(Tier.RESTRICTED) @Size(max = 2000) String chronicConditions,
        @Classification(Tier.RESTRICTED) @Size(max = 2000) String medication,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 200) String emergencyContactName,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 20) String emergencyContactPhone,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 60) String emergencyContactRelation) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
