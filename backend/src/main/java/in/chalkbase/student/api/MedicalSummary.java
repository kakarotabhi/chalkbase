package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentMedical;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A student's health record, <strong>masked</strong> (ADR-0014, FR-034).
 *
 * <p>This is what {@code GET /api/students/{id}} answers with, nested in {@code StudentDetail}, and
 * it is deliberately not {@code MedicalDetail}. ADR-0014 says a Restricted value is "masked by
 * default in the UI, revealed by an explicit permission and a recorded action" — and a value the
 * browser has already received and is merely hiding with CSS is not masked, it is displayed. So the
 * six Restricted fields are not sent here at all; only whether each one has been recorded is, as a
 * boolean {@code hasX}, which is Internal — knowing that <em>something</em> was entered for blood
 * group is not itself a health fact about the child the way the value would be.
 *
 * <p>{@code GET /api/students/{id}/medical/restricted} is the second request that answers with the
 * real values, gated on {@code student:student:reveal_restricted} and audited on every call — see
 * {@code StudentRecordService#revealMedical}.
 *
 * <p>The emergency contact is Confidential, not Restricted, and is sent here in full: it is a name
 * and a phone number, not a health fact, and ADR-0014 does not ask for it to be masked.
 */
public record MedicalSummary(
        @Classification(Tier.INTERNAL) boolean hasBloodGroup,
        @Classification(Tier.INTERNAL) boolean hasCwsnStatus,
        @Classification(Tier.INTERNAL) boolean hasDisabilityDetails,
        @Classification(Tier.INTERNAL) boolean hasAllergies,
        @Classification(Tier.INTERNAL) boolean hasChronicConditions,
        @Classification(Tier.INTERNAL) boolean hasMedication,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String emergencyContactName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String emergencyContactPhone,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String emergencyContactRelation) {

    public static MedicalSummary of(StudentMedical medical) {
        return new MedicalSummary(
                hasText(medical.getBloodGroup()),
                hasText(medical.getCwsnStatus()),
                hasText(medical.getDisabilityDetails()),
                hasText(medical.getAllergies()),
                hasText(medical.getChronicConditions()),
                hasText(medical.getMedication()),
                medical.getEmergencyContactName(),
                medical.getEmergencyContactPhone(),
                medical.getEmergencyContactRelation());
    }

    /** Nothing recorded for this section at all — every presence flag false, no emergency contact. */
    public static MedicalSummary empty() {
        return new MedicalSummary(false, false, false, false, false, false, null, null, null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
