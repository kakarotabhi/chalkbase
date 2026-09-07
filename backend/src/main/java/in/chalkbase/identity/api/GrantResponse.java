package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** One grant a user holds: which role, over how much of the school, and for how long. */
public record GrantResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID roleId,
        @Classification(Tier.INTERNAL) String roleName,
        @Classification(Tier.INTERNAL) String scopeType,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID scopeId,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate validFrom,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate validTo) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
