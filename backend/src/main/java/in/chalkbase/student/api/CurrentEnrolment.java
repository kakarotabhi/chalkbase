package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where a student sits <em>this year</em>, flattened onto a list row.
 *
 * <p>Names only, no ids. A class list is read, not navigated from — the row already carries the
 * student's id, and a client that wants the enrolment itself asks for the student's detail, which
 * carries every enrolment with its ids. Sending four more ids on every one of eight hundred rows to
 * save a call nobody makes is the wrong trade.
 *
 * <p><strong>"Current" means the school's current academic year</strong>, not "the most recent one".
 * See {@code StudentService#currentEnrolments}: a school that has set up next year's session early
 * would otherwise see next year's class against every child while still teaching this year, and
 * nothing on the screen would say the number was for a different year.
 *
 * @param rollNumber null until the class list settles. Assigned after admission, on purpose.
 */
public record CurrentEnrolment(
        @Classification(Tier.INTERNAL) String sessionName,
        @Classification(Tier.INTERNAL) String className,
        @Classification(Tier.INTERNAL) String sectionName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String rollNumber) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
