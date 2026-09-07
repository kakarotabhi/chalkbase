package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Two data-quality gaps a school can act on directly: a child with nobody to call, and a person in
 * the directory attached to nobody.
 *
 * <p>The two halves are gated separately, because they are two different permissions —
 * {@code student:student:read} for the first, {@code student:guardian:read} for the second
 * ({@code StudentPermissions}) — and a school that has narrowed one need not lose the other. A
 * caller holding neither never receives this tile at all; a caller holding one sees that field
 * populated and the other absent, never zero standing in for "not permitted to know".
 */
public record LinkageGapsTile(
        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Long studentsWithoutAGuardian,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Long guardiansWithoutAStudent) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
