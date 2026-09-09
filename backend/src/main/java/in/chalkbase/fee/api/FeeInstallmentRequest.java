package in.chalkbase.fee.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One due date within a {@code FeeStructureItemRequest}, and the amount due on it.
 *
 * <p>An item's installments must add up to that item's own {@code amount}
 * ({@code FeeErrorCode#INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT}) — checked by
 * {@code FeeStructureService}, not here, because the rule spans two fields of the parent request.
 */
public record FeeInstallmentRequest(
        @Classification(Tier.INTERNAL) @NotNull LocalDate dueDate,

        @Classification(Tier.INTERNAL) @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
