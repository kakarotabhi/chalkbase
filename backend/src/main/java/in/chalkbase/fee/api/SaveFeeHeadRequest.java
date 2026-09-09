package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeHeadCategory;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A fee head created or renamed, recategorised, capped or uncapped, retired or reinstated.
 *
 * <p>One shape for create and update, like {@code UpdateSchoolClassRequest}: {@code active} is a
 * boxed {@link Boolean} with {@link NotNull} rather than a primitive, so a client that forgot to
 * send it is told rather than silently retiring the head.
 *
 * <p>{@code capPercentOfTuition} is refused by the service, not this record, when
 * {@code category} is not {@code ANNUAL_DEVELOPMENT} ({@code FeeErrorCode#CAP_PERCENT_NOT_APPLICABLE})
 * — the same split {@code FeeHead#apply} draws between what the object may hold and what a caller
 * may ask for.
 *
 * <p>{@code capPercentOfTuition} carries no {@code @Schema(nullable = true)}, following
 * {@code GrantRoleRequest}'s own precedent: that marker only strips from response-only schemas
 * ({@code OpenApiConfig#requiredUnlessNullable}), and on a request the absence of {@code @NotNull}
 * already says "may be omitted" — adding it anyway would leak OpenAPI 3.1's
 * {@code "type": ["number", "null"]} into the contract, which {@code OpenApiContractTests} forbids.
 */
public record SaveFeeHeadRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 80) String name,

        @Classification(Tier.INTERNAL) @NotNull FeeHeadCategory category,

        @Classification(Tier.INTERNAL) @DecimalMin("0.01") @DecimalMax("100") BigDecimal capPercentOfTuition,

        @Classification(Tier.INTERNAL) @NotNull Boolean active) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
