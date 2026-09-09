package in.chalkbase.communication.api;

import in.chalkbase.communication.domain.CircularTarget;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One target, resolved to names — a composer or a summary showing "Class 5 · A" rather than two
 * bare ids.
 *
 * @param sectionId and {@code sectionName} null together, meaning "every active section of this
 *     class".
 */
public record CircularTargetResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID classId,
        @Classification(Tier.INTERNAL) String className,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID sectionId,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String sectionName) {

    public static CircularTargetResponse of(CircularTarget target, String className, String sectionName) {
        return new CircularTargetResponse(
                target.getId(), target.getClassId(), className, target.getSectionId(), sectionName);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
