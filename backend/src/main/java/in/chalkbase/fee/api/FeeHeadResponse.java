package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeHead;
import in.chalkbase.fee.domain.FeeHeadCategory;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A named thing this school charges for. Internal under ADR-0014 (module map, ADR-0014's own
 * tier table): a price list, not any family's own record.
 *
 * @param capPercentOfTuition null unless this head carries a Development Fee cap
 *     (07-phase-0-decisions.md §2). Only ever non-null when {@code category} is
 *     {@code ANNUAL_DEVELOPMENT}.
 * @param active false for a head the school has retired. Returned rather than hidden: a structure
 *     written before it was retired still names it.
 */
public record FeeHeadResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) FeeHeadCategory category,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        BigDecimal capPercentOfTuition,

        @Classification(Tier.INTERNAL) boolean active) {

    public static FeeHeadResponse of(FeeHead head) {
        return new FeeHeadResponse(
                head.getId(), head.getName(), head.getCategory(), head.getCapPercentOfTuition(), head.isActive());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
