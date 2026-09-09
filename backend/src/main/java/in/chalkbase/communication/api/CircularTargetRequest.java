package in.chalkbase.communication.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One class or section to target, within a {@link CreateCircularRequest}.
 *
 * @param sectionId null to mean "every active section of this class". Given, means one specific
 *     section, which must belong to {@code classId} ({@link
 *     in.chalkbase.communication.domain.CommunicationErrorCode#INVALID_TARGET} otherwise).
 */
public record CircularTargetRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID classId,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID sectionId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
