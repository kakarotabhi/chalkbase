package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new rung on the ladder.
 *
 * <p><strong>There is no {@code sequence} here on purpose.</strong> A new class is appended at the
 * end, at {@code max(sequence) + 1}. Letting a client name a position would mean a school inserting
 * a rung between two existing ones collides with whichever class already holds that number, and the
 * honest fix for that is not to renumber quietly behind the request — it is to reorder, which is
 * its own operation over the whole list.
 *
 * <p>No {@code active} either: a class is created active. Creating something already retired is not
 * a thing a school does, and the edit form is where it is retired.
 */
public record CreateSchoolClassRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 40) String name) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
