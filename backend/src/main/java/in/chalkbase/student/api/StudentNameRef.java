package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * Enough to label a row that already holds a student id, with no roster or session context.
 *
 * <p>Deliberately smaller than {@link EnrolledStudentRef}: that one exists for a class register and
 * carries a roll number that only means something against one section, for one session.
 * {@link StudentLookup#namesOf} answers for a student regardless of whether they hold an active
 * enrolment anywhere today — a correction request queue naming a student who transferred out last
 * term still has to say whose record it is about.
 */
public record StudentNameRef(
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
