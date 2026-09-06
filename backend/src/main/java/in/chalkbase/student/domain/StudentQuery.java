package in.chalkbase.student.domain;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * What to narrow the student list by. Every field is optional; all of them are ANDed.
 *
 * @param q free text over the student's name and admission number. Those two, and nothing else: an
 *     office looking for a child has one or the other written in front of them. Searching guardian
 *     names from here would turn a student list into a way of asking "which children does this
 *     person have", which the guardian directory deliberately does not answer either.
 * @param status one of the five. Absent means every status, including students who have left —
 *     filtering them out by default would hide the records a transfer certificate is produced from.
 * @param sectionId students currently enrolled in that section. "Currently" means an active
 *     enrolment in the school's <em>current</em> academic year, so a school that has not said which
 *     year it is in gets an empty page rather than last year's class list.
 */
public record StudentQuery(
        @Classification(Tier.CONFIDENTIAL) String q,
        @Classification(Tier.INTERNAL) StudentStatus status,
        @Classification(Tier.INTERNAL) UUID sectionId) {

    /** Everything, unnarrowed. */
    public static StudentQuery all() {
        return new StudentQuery(null, null, null);
    }

    public boolean hasText() {
        return q != null && !q.isBlank();
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
