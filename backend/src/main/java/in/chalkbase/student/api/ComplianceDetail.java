package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentCompliance;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A student's caste and community, religion, EWS/BPL/RTE category and APAAR id, <strong>unmasked</strong>
 * (ADR-0014, ADR-0022).
 *
 * <p>The answer to {@code GET /api/students/{id}/compliance/restricted}, and nothing else returns
 * this. Every component here is Restricted, decrypted from {@code student_compliance} on the way
 * out; {@code EncryptionBindingTests} keeps the pairing with {@code @Encrypted} honest. PEN/UDISE,
 * the board registration number and the APAAR consent record are not repeated here — they are
 * Confidential or Internal, not Restricted, and already sent in full by {@link ComplianceSummary}.
 *
 * <p>Calling this endpoint is the read ADR-0014 requires to be audited — see
 * {@code StudentRecordService#revealCompliance}.
 */
public record ComplianceDetail(
        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String casteCategory,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String religion,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String specialCategory,

        @Schema(nullable = true) @Classification(Tier.RESTRICTED)
        String apaarId) {

    public static ComplianceDetail of(StudentCompliance compliance) {
        return new ComplianceDetail(
                compliance.getCasteCategory(),
                compliance.getReligion(),
                compliance.getSpecialCategory(),
                compliance.getApaarId());
    }

    public static ComplianceDetail empty() {
        return new ComplianceDetail(null, null, null, null);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
