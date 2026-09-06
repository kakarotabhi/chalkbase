package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.SchoolClass;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * One rung of the school's ladder, as another module sees it.
 *
 * <p>Deliberately not {@link SchoolClassResponse}, which nests its sections because the one screen
 * that shows a class shows them together. This is the cross-module contract and stays flat: a caller
 * resolving names asks for the classes and the sections separately and joins them on
 * {@link SectionRef#classId()}, so a class with no sections yet is still visible — which is the
 * whole reason a name can fail to match one way rather than the other.
 *
 * @param sequence where this rung sits, so a caller listing the ladder back to a user lists it in
 *     the order the school reads it rather than alphabetically, where "Class 10" precedes "Class 2"
 * @param active false for a class the school has retired. Returned rather than hidden: a name that
 *     matches a retired class deserves a different answer from a name that matches nothing.
 */
public record SchoolClassRef(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) int sequence,
        @Classification(Tier.INTERNAL) boolean active) {

    public static SchoolClassRef of(SchoolClass schoolClass) {
        return new SchoolClassRef(
                schoolClass.getId(), schoolClass.getName(), schoolClass.getSequence(), schoolClass.isActive());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
