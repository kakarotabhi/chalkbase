package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeConcessionCategory;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A concession type created or edited. One shape for create and update, like
 * {@code SaveFeeHeadRequest}: {@code requiresApproval} and {@code active} are boxed
 * {@link Boolean}s with {@link NotNull} so a client that forgot one is told, rather than the field
 * silently defaulting to {@code false}.
 */
public record SaveFeeConcessionTypeRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 80) String name,

        @Classification(Tier.INTERNAL) @NotNull FeeConcessionCategory category,

        @Schema(nullable = true) @Classification(Tier.INTERNAL) @Size(max = 300) String description,

        @Classification(Tier.INTERNAL) @NotNull Boolean requiresApproval,
        @Classification(Tier.INTERNAL) @NotNull Boolean active) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
