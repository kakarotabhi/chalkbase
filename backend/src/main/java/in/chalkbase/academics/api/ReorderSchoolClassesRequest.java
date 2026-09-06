package in.chalkbase.academics.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The whole ladder, in the order it should now be in.
 *
 * <p>The list must be exactly the set of this school's class ids: no duplicates, none missing, none
 * belonging to anything else. Anything less is refused rather than applied to what was sent,
 * because a client that silently dropped one class would otherwise renumber the survivors and leave
 * a rung off the bottom of the ladder — and nothing about the result would look wrong until
 * somebody could not enrol a student into it.
 *
 * <p>Sending the whole list, rather than one class and one new position, is why
 * {@code uq_school_class_sequence} is {@code deferrable initially deferred}: swapping two positions
 * is one transaction whose intermediate states are allowed to be invalid, where two separate calls
 * would collide on the first one.
 *
 * @param classIds every class of this school, exactly once, in the new display order. Position in
 *     the list becomes {@code sequence}, counting from one.
 */
public record ReorderSchoolClassesRequest(
        @Classification(Tier.INTERNAL) @NotNull List<@NotNull UUID> classIds) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
