package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentTransfer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Where a student came from, and the transfer certificate that admitted them (FR-033).
 *
 * <p>Confidential under ADR-0014: it identifies where a child came from, not what they are, so none
 * of ADR-0022's masking or encryption applies here.
 */
public record PreviousSchoolDetail(
        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String previousSchoolName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String previousSchoolBoard,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String transferCertificateNumber,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        LocalDate transferCertificateIssuedOn,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String reasonForLeaving) {

    public static PreviousSchoolDetail of(StudentTransfer transfer) {
        return new PreviousSchoolDetail(
                transfer.getPreviousSchoolName(),
                transfer.getPreviousSchoolBoard(),
                transfer.getTransferCertificateNumber(),
                transfer.getTransferCertificateIssuedOn(),
                transfer.getReasonForLeaving());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
