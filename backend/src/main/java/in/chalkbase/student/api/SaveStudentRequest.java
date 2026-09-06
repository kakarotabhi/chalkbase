package in.chalkbase.student.api;

import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.StudentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A child admitted, or a child's record corrected. One shape for both, as
 * {@code SaveAcademicSessionRequest} is: the fields a school may set are the same either way, and
 * two records differing in nothing would drift apart for no reason.
 *
 * <p>The enrolment is <strong>not</strong> here. Admitting a child and placing them in 5B are two
 * decisions the office makes at two different moments — often weeks apart, since the class list
 * settles after admissions close — and folding the second into the first would make a student
 * unrecordable until somebody had decided which section they were going into.
 * {@code POST /api/students/{id}/enrolments} is where that decision goes.
 *
 * <p>Guardians are absent for the same reason and one more: a guardian is a shared person record,
 * so creating one implicitly from a student form is how a father ends up in the table four times
 * (ADR-0020 §5). The office searches the directory first, then links.
 *
 * <p>{@code gender} and {@code status} are enums, so an unknown value is rejected by Jackson as a
 * field error naming the field rather than reaching {@code ck_student_gender} and coming back as a
 * bare conflict.
 *
 * @param dateOfBirth {@code @Past}, not {@code @PastOrPresent}: a child admitted on their day of
 *     birth is not a case, and an unvalidated future date is how a typo in the year — 2027 for 2017
 *     — reaches a transfer certificate. The message names the field and never echoes the value
 *     (ADR-0014).
 * @param status supplied rather than defaulted, so that correcting a record cannot silently
 *     reactivate a child who has left: an edit form that omitted it would send back {@code ACTIVE}
 *     for a {@code TRANSFERRED} student every time it was saved
 * @param admittedOn optional, because a record migrated from a paper register often has no reliable
 *     admission date and inventing one would put a guess on a document a school is held to
 */
public record SaveStudentRequest(
        @NotBlank @Size(max = 40) String admissionNumber,
        @NotBlank @Size(max = 200) String fullName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @NotNull StudentStatus status,
        LocalDate admittedOn) {}
