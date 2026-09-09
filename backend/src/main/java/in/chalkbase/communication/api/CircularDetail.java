package in.chalkbase.communication.api;

import in.chalkbase.communication.domain.CircularStatus;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One circular in full: its content, its targets, and the recipient counts a
 * {@code communication:circular:read} holder may see.
 *
 * @param recipientCount zero until published. Every recipient counts as delivered the instant this
 *     row exists — there is no queue an in-app circular waits in — so a separate "delivered count"
 *     would only ever equal this one; see the module's package doc for why.
 * @param acknowledgedCount zero for a circular that does not require acknowledgement, and
 *     otherwise how many of {@code recipientCount} have one recorded.
 */
public record CircularDetail(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String title,
        @Classification(Tier.INTERNAL) String body,
        @Classification(Tier.INTERNAL) boolean requiresAcknowledgement,
        @Classification(Tier.INTERNAL) CircularStatus status,
        @Classification(Tier.INTERNAL) List<CircularTargetResponse> targets,
        @Classification(Tier.INTERNAL) int recipientCount,
        @Classification(Tier.INTERNAL) int acknowledgedCount,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Instant publishedAt,

        @Classification(Tier.INTERNAL) Instant createdAt) {

    public CircularDetail {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
