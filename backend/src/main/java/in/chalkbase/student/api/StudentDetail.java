package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One child's whole record: the summary fields, plus the two things that hang off them.
 *
 * <p>Guardians and enrolments come nested rather than from two more endpoints because there is no
 * screen that wants one without the other — a student record is the guardians to ring and the class
 * they are in — and three calls to draw one page is three chances for the page to be half right.
 *
 * <p><strong>The most Confidential payload in the product</strong> (ADR-0014): a child's name, date
 * of birth and admission number, and every guardian's name, phone and email. Nothing here may be
 * logged at any level or appear in an error message. What is deliberately <em>absent</em> is the
 * Restricted tier — caste, religion, disability, category, income, APAAR, Aadhaar — because
 * encryption at rest, masking and read-auditing do not exist yet, and adding the fields first would
 * mean storing a child's caste in plaintext in a table nobody has decided how to protect (ADR-0020
 * §2).
 *
 * <p>There is no {@code DELETE} for this resource and there is not going to be one (ADR-0020 §6).
 * A child who leaves is {@link StudentStatus#WITHDRAWN} or {@link StudentStatus#TRANSFERRED}.
 *
 * @param enrolments every placement this student has ever had, newest year first. A history, not a
 *     current state: promotion is a new row, so this is where "which class was she in in 2024-25"
 *     is answered.
 * @param guardians the people responsible for this child, primary contact first
 */
public record StudentDetail(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName,
        @Classification(Tier.CONFIDENTIAL) Gender gender,
        @Classification(Tier.INTERNAL) StudentStatus status,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        CurrentEnrolment currentEnrolment,

        @Classification(Tier.CONFIDENTIAL) LocalDate dateOfBirth,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        LocalDate admittedOn,

        @Classification(Tier.CONFIDENTIAL) List<StudentGuardian> guardians,
        @Classification(Tier.CONFIDENTIAL) List<Enrolment> enrolments) {

    public StudentDetail {
        guardians = guardians == null ? List.of() : List.copyOf(guardians);
        enrolments = enrolments == null ? List.of() : List.copyOf(enrolments);
    }

    public static StudentDetail of(
            Student student,
            CurrentEnrolment currentEnrolment,
            List<StudentGuardian> guardians,
            List<Enrolment> enrolments) {
        return new StudentDetail(
                student.getId(),
                student.getAdmissionNumber(),
                student.getFullName(),
                student.getGender(),
                student.getStatus(),
                currentEnrolment,
                student.getDateOfBirth(),
                student.getAdmittedOn(),
                guardians,
                enrolments);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
