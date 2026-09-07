package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * How many students hold a live enrolment in one class, this session.
 *
 * <p>Aggregated in {@link DashboardService} from {@code student.api.SectionEnrolmentCount} —
 * {@code student} only knows sections, and this module resolves each section's class through
 * {@code academics.api.AcademicsLookup} and sums, the same join every other cross-module read in
 * this codebase does at the caller rather than in the database.
 *
 * @param sequence where this class sits on the school's ladder ({@code academics.api.SchoolClassRef}),
 *     so the tile lists "Class 1" before "Class 10" rather than alphabetically
 */
public record ClassEnrolmentCount(
        @Classification(Tier.INTERNAL) UUID classId,
        @Classification(Tier.INTERNAL) String className,
        @Classification(Tier.INTERNAL) int sequence,
        @Classification(Tier.INTERNAL) long count) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
