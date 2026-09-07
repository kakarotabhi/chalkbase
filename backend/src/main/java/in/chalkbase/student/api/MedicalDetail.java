package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentMedical;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A student's health record, <strong>unmasked</strong> (ADR-0014, ADR-0022, FR-034).
 *
 * <p>The answer to {@code GET /api/students/{id}/medical/restricted}, and nothing else returns
 * this. Every component here is Restricted and every one is decrypted from {@code student_medical}
 * on the way out; {@code EncryptionBindingTests} is what keeps that pairing honest. The emergency
 * contact is not repeated here — it is Confidential, not Restricted, and already sent in full by
 * {@link MedicalSummary}, which is nested in {@code StudentDetail} on the ordinary
 * {@code GET /api/students/{id}}.
 *
 * <p>Calling this endpoint is <strong>the read ADR-0014 requires to be audited</strong> — see
 * {@code StudentRecordService#revealMedical}. Opening a student's record does not audit anything by
 * itself; only fetching this does.
 */
public record MedicalDetail(
        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String bloodGroup,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String cwsnStatus,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String disabilityDetails,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String allergies,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String chronicConditions,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String medication) {

    public static MedicalDetail of(StudentMedical medical) {
        return new MedicalDetail(
                medical.getBloodGroup(),
                medical.getCwsnStatus(),
                medical.getDisabilityDetails(),
                medical.getAllergies(),
                medical.getChronicConditions(),
                medical.getMedication());
    }

    public static MedicalDetail empty() {
        return new MedicalDetail(null, null, null, null, null, null);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
