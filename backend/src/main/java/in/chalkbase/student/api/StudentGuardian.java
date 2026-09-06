package in.chalkbase.student.api;

import in.chalkbase.student.domain.Guardian;
import in.chalkbase.student.domain.GuardianRelation;
import in.chalkbase.student.domain.StudentGuardianLink;
import java.util.UUID;

/**
 * A guardian as they appear on one child's record: the person, plus what they are to that child.
 *
 * <p>Two ids, and both are load-bearing. {@link #linkId()} addresses the relationship — it is what a
 * client sends to change the relation, make this the primary contact, or detach the guardian from
 * this child. {@link #guardianId()} addresses the <em>person</em>, who is shared with their other
 * children (ADR-0020 §5): it is what the office uses to attach the same father to a sibling instead
 * of typing him in again, and it is what an edit to his phone number is aimed at. Collapsing them
 * into one id would make "remove this guardian" ambiguous between the two things it could mean, and
 * one of those two things is deleting a person, which this module does not do.
 *
 * <p>Every field but the two ids and {@code primary} is Confidential under ADR-0014.
 *
 * @param primary whether the school rings this person first. At most one per student, said in the
 *     database by {@code uq_student_guardian_one_primary}.
 */
public record StudentGuardian(
        UUID linkId,
        UUID guardianId,
        String fullName,
        GuardianRelation relation,
        String phone,
        String email,
        String occupation,
        boolean primary) {

    public static StudentGuardian of(StudentGuardianLink link) {
        Guardian guardian = link.getGuardian();
        return new StudentGuardian(
                link.getId(),
                guardian.getId(),
                guardian.getFullName(),
                link.getRelation(),
                guardian.getPhone(),
                guardian.getEmail(),
                guardian.getOccupation(),
                link.isPrimary());
    }
}
