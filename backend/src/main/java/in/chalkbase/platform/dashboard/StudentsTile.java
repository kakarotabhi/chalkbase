package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;

/**
 * Students enrolled this session, and how they are spread across the school's classes.
 *
 * <p>Absent from {@link DashboardResponse} entirely — never present with zeros — when no session
 * is current: "enrolled" means nothing without a year to enrol into (ADR-0019), and reporting zero
 * would read as a school with an empty roll rather than as a school that has not opened its year.
 *
 * @param byClass one row per class holding at least one enrolled student, ordered by the school's
 *     own ladder. A class with nobody enrolled is simply absent — the same "absent, not zero"
 *     convention {@code student.api.SectionEnrolmentCount} already uses.
 */
public record StudentsTile(
        @Classification(Tier.INTERNAL) long enrolled,
        @Classification(Tier.INTERNAL) List<ClassEnrolmentCount> byClass) {

    public StudentsTile {
        byClass = byClass == null ? List.of() : List.copyOf(byClass);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
