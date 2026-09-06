package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.GuardianRelation;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentGuardianLink;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One child, as seen from a guardian's record — the other direction of {@link StudentGuardian}.
 *
 * <p><strong>This is what {@code GuardianSummary.linkedStudentCount} could not answer.</strong> The
 * count says the shared model is working — "linked to 4 students" is what tells a clerk that
 * correcting this row reaches four children (ADR-0020 §5). But somebody staring at two records that
 * both say "Suresh Kulkarni, linked to 2 students" is asking a different question, and the count
 * cannot answer it: <em>which</em> two? Without the names, the safest-looking move is to create a
 * third record, which is the duplication the whole model exists to prevent.
 *
 * <p>Every field but the ids, the relation and the flag is Confidential under ADR-0014 — this is a
 * child's name, their admission number and where they sit. That is why the endpoint carrying this
 * is gated on {@code student:student:read} rather than on the guardian permission: see
 * {@code GuardianController#students}.
 *
 * @param currentEnrolment absent for a child with no active enrolment in the school's current
 *     academic year — a new admission not yet placed, a child who has left, or a school that has
 *     not yet said which year it is in
 */
public record GuardianStudent(
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String fullName,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.INTERNAL) GuardianRelation relation,
        @Classification(Tier.INTERNAL) boolean primary,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        CurrentEnrolment currentEnrolment) {

    public static GuardianStudent of(StudentGuardianLink link, CurrentEnrolment currentEnrolment) {
        Student student = link.getStudent();
        return new GuardianStudent(
                student.getId(),
                student.getFullName(),
                student.getAdmissionNumber(),
                link.getRelation(),
                link.isPrimary(),
                currentEnrolment);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
