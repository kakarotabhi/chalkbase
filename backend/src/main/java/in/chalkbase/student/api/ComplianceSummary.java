package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentCompliance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A student's UDISE+/board identifiers and statutory categories, <strong>masked</strong>
 * (ADR-0014, FR-029).
 *
 * <p>Nested in {@code StudentDetail}, the same design as {@link MedicalSummary}: the four Restricted
 * fields — caste and community, religion, EWS/BPL/RTE category, APAAR — are never sent here, only
 * whether each has been recorded, as an Internal {@code hasX} boolean. {@code GET
 * /api/students/{id}/compliance/restricted} answers with the real values and is the read ADR-0014
 * requires to be audited; see {@code StudentRecordService#revealCompliance}.
 *
 * <p>PEN/UDISE and the board registration number are Confidential, not Restricted — identifiers,
 * like the admission number — and are sent here in full. The APAAR consent record
 * ({@code apaarConsentGiven}, who and when) is sent in full too: it is metadata about a decision,
 * not the APAAR id itself.
 */
public record ComplianceSummary(
        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String penUdiseId,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String boardRegistrationNumber,

        @Classification(Tier.INTERNAL) boolean hasCasteCategory,
        @Classification(Tier.INTERNAL) boolean hasReligion,
        @Classification(Tier.INTERNAL) boolean hasSpecialCategory,
        @Classification(Tier.INTERNAL) boolean hasApaarId,
        @Classification(Tier.INTERNAL) boolean apaarConsentGiven,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String apaarConsentGivenBy,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant apaarConsentGivenAt) {

    public static ComplianceSummary of(StudentCompliance compliance) {
        return new ComplianceSummary(
                compliance.getPenUdiseId(),
                compliance.getBoardRegistrationNumber(),
                hasText(compliance.getCasteCategory()),
                hasText(compliance.getReligion()),
                hasText(compliance.getSpecialCategory()),
                hasText(compliance.getApaarId()),
                compliance.isApaarConsentGiven(),
                compliance.getApaarConsentGivenBy(),
                compliance.getApaarConsentGivenAt());
    }

    public static ComplianceSummary empty() {
        return new ComplianceSummary(null, null, false, false, false, false, false, null, null);
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
