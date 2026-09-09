package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeConcessionCategory;
import in.chalkbase.fee.domain.FeeConcessionType;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A kind of waiver this school offers (FR-078) — a catalogue entry, never a grant. See
 * {@code in.chalkbase.fee.package-info} for what is and is not built here.
 */
public record FeeConcessionTypeResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) FeeConcessionCategory category,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String description,

        @Classification(Tier.INTERNAL) boolean requiresApproval,
        @Classification(Tier.INTERNAL) boolean active) {

    public static FeeConcessionTypeResponse of(FeeConcessionType type) {
        return new FeeConcessionTypeResponse(
                type.getId(),
                type.getName(),
                type.getCategory(),
                type.getDescription(),
                type.isRequiresApproval(),
                type.isActive());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
