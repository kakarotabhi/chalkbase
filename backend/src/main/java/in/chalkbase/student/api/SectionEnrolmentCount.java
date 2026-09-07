package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * How many students hold a live enrolment in one section, as another module sees it.
 *
 * <p>Named by section rather than by class, the same way {@code StudentEnrolment} itself is
 * (ADR-0020 §4): this module does not know a section's class without asking {@code academics.api},
 * so a caller wanting counts by class resolves {@link #sectionId()} through
 * {@code AcademicsLookup.sections} and aggregates from there. Returning a class id here would mean
 * this module reaching into {@code academics.domain} to get one, which {@code ModularityTests}
 * refuses.
 *
 * <p>{@code count} is a number of students, not a value of any one student's field — the same
 * reasoning that lets {@code audit_event.record_count} exist under ADR-0014 applies here: a
 * headcount identifies nobody.
 */
public record SectionEnrolmentCount(
        @Classification(Tier.INTERNAL) UUID sectionId,
        @Classification(Tier.INTERNAL) long count) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
