package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A student's previous school and transfer certificate, corrected (FR-033). One shape for entering
 * it the first time and for editing it.
 *
 * <p>Every field optional: a student admitted straight into nursery has no previous school and no
 * transfer certificate, and this section simply has nothing in it.
 */
public record SavePreviousSchoolRequest(
        @Classification(Tier.CONFIDENTIAL) @Size(max = 200) String previousSchoolName,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 60) String previousSchoolBoard,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 60) String transferCertificateNumber,
        @Classification(Tier.CONFIDENTIAL) LocalDate transferCertificateIssuedOn,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 4000) String reasonForLeaving) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
