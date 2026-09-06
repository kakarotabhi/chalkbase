package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One of this school's roles, with the permissions it currently holds.
 *
 * @param templateCode the shipped template this role was copied from at onboarding, or null if the
 *     school created it. Provenance only: the role is the school's, and editing it changes nothing
 *     at any other school (ADR-0005).
 */
public record RoleResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String code,
        @Classification(Tier.INTERNAL) String name,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String description,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String templateCode,

        @Classification(Tier.INTERNAL) List<String> permissions) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
