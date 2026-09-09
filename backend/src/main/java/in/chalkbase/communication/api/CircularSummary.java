package in.chalkbase.communication.api;

import in.chalkbase.communication.domain.CircularStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One row of the circular list — the composer's own history, newest first. */
public record CircularSummary(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String title,
        @Classification(Tier.INTERNAL) CircularStatus status,
        @Classification(Tier.INTERNAL) boolean requiresAcknowledgement,
        @Classification(Tier.INTERNAL) int targetCount,
        @Classification(Tier.INTERNAL) int recipientCount,
        @Classification(Tier.INTERNAL) int acknowledgedCount,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant publishedAt,

        @Classification(Tier.INTERNAL) Instant createdAt) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
