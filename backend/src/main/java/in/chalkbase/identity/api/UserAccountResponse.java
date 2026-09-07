package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * An account's own state, after deactivating, reactivating or unlocking it.
 *
 * <p>No username here, matching {@link UserSummary}: the caller supplied the id it acted on, so this
 * is confirmation of the new state, never a lookup.
 */
public record UserAccountResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String displayName,
        @Classification(Tier.INTERNAL) String status,
        @Classification(Tier.INTERNAL) boolean mustChangePassword,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant lockedUntil,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant lastLoginAt) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
