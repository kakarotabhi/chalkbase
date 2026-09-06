package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A student placed in a section for an academic year. The student is in the path.
 *
 * <p>The class is not here and is not asked for: a section belongs to exactly one class, so naming
 * both would be two answers to one question and a chance for them to disagree. The class comes back
 * on the {@link Enrolment} that this produces.
 *
 * <p>Refused if the student already has an active enrolment in that year
 * ({@code uq_student_enrolment_one_active}). Promotion is a new row in the <em>next</em> year, not a
 * second row in this one; moving a child between sections within a year is an edit to the enrolment
 * they already have.
 *
 * @param rollNumber optional, because it is assigned after admission and often after the class list
 *     settles (ADR-0020 §4). Unique per section and year when supplied; several students with no
 *     roll number yet do not collide, because PostgreSQL treats nulls in a unique index as distinct.
 */
public record CreateEnrolmentRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID academicSessionId,
        @Classification(Tier.INTERNAL) @NotNull UUID sectionId,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 20) String rollNumber) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
