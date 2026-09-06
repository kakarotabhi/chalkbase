package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new division of a class. The class is in the path, not in the body — a section outside a class
 * is not a thing.
 */
public record CreateSectionRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 20) String name) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
