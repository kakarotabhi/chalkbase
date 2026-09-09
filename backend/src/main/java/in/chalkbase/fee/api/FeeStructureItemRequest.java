package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.InstallmentFrequency;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One fee head's amount, frequency and installment schedule within a
 * {@link SaveFeeStructureRequest}.
 *
 * <p>The same fee head must not appear twice in one structure
 * ({@code FeeErrorCode#DUPLICATE_HEAD_IN_STRUCTURE}) — checked across the whole item list by
 * {@code FeeStructureService}, since a single item cannot see its siblings.
 */
public record FeeStructureItemRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID feeHeadId,

        @Classification(Tier.INTERNAL) @NotNull @DecimalMin(value = "0") @Digits(integer = 10, fraction = 2) BigDecimal amount,

        @Classification(Tier.INTERNAL) @NotNull InstallmentFrequency frequency,

        @Classification(Tier.INTERNAL) @NotEmpty @Valid List<FeeInstallmentRequest> installments) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
