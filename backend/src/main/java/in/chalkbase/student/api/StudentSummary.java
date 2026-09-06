package in.chalkbase.student.api;

import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentStatus;
import java.util.UUID;

/**
 * One row of the student list.
 *
 * <p>Confidential under ADR-0014: {@code fullName} and {@code admissionNumber} each identify a
 * child. Neither may be logged, put in an error message, or passed to an audit method — the audit
 * log records the student's UUID and the names of the fields that changed, and nothing else.
 *
 * <p>The date of birth is deliberately <strong>not</strong> here. It is on {@link StudentDetail},
 * where somebody has opened one child's record, rather than on eight hundred rows of a list that
 * anyone holding {@code student:student:read} can page through — and a list is what gets exported,
 * screenshotted and pasted into a message. It is not needed to identify a row: the admission number
 * does that, and it is what the office actually searches by.
 *
 * @param currentEnrolment null for a student with no active enrolment in the school's current
 *     academic year — a new admission not yet placed, a child who has left, or a school that has not
 *     yet said which year it is in
 */
public record StudentSummary(
        UUID id,
        String admissionNumber,
        String fullName,
        Gender gender,
        StudentStatus status,
        CurrentEnrolment currentEnrolment) {

    public static StudentSummary of(Student student, CurrentEnrolment currentEnrolment) {
        return new StudentSummary(
                student.getId(),
                student.getAdmissionNumber(),
                student.getFullName(),
                student.getGender(),
                student.getStatus(),
                currentEnrolment);
    }
}
