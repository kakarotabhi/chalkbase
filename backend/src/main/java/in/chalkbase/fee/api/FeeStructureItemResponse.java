package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeHeadCategory;
import in.chalkbase.fee.domain.FeeInstallment;
import in.chalkbase.fee.domain.FeeStructureItem;
import in.chalkbase.fee.domain.InstallmentFrequency;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One fee head's amount, frequency and installments within a {@link FeeStructureResponse}.
 *
 * <p>Carries the head's name and category inline, the same reason {@code SectionRef} carries its
 * class's name: a fee head id alone means nothing to a screen rendering a bill, and resolving it
 * would either be a second call per row or a reach into this module's own domain from the DTO
 * layer.
 */
public record FeeStructureItemResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID feeHeadId,
        @Classification(Tier.INTERNAL) String feeHeadName,
        @Classification(Tier.INTERNAL) FeeHeadCategory feeHeadCategory,
        @Classification(Tier.INTERNAL) BigDecimal amount,
        @Classification(Tier.INTERNAL) InstallmentFrequency frequency,
        @Classification(Tier.INTERNAL) List<FeeInstallmentResponse> installments) {

    /** Sorted here as well as by {@code @OrderBy}, so the contract holds however the rows were loaded. */
    public static FeeStructureItemResponse of(FeeStructureItem item) {
        return new FeeStructureItemResponse(
                item.getId(),
                item.getFeeHead().getId(),
                item.getFeeHead().getName(),
                item.getFeeHead().getCategory(),
                item.getAmount(),
                item.getFrequency(),
                item.getInstallments().stream()
                        .sorted(Comparator.comparing(FeeInstallment::getDueDate))
                        .map(FeeInstallmentResponse::of)
                        .toList());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
