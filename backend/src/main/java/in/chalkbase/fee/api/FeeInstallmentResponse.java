package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeInstallment;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One due date within a {@link FeeStructureItemResponse}, and the amount due on it. */
public record FeeInstallmentResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) LocalDate dueDate,
        @Classification(Tier.INTERNAL) BigDecimal amount) {

    public static FeeInstallmentResponse of(FeeInstallment installment) {
        return new FeeInstallmentResponse(installment.getId(), installment.getDueDate(), installment.getAmount());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
