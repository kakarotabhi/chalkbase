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
 * {@code contact}, {@code previousSchool}, {@code medical} and {@code compliance} follow the same
 * reasoning: FR-028's remaining sections, on the one payload a screen needs to draw the whole record.
 *
 * <p><strong>The most Confidential payload in the product</strong> (ADR-0014): a child's name, date
 * of birth and admission number, and every guardian's name, phone and email. Nothing here may be
 * logged at any level or appear in an error message.
 *
 * <p><strong>{@code medical} and {@code compliance} are masked</strong> (ADR-0014, ADR-0022): they
 * carry {@link MedicalSummary} and {@link ComplianceSummary}, never {@link MedicalDetail} or
 * {@link ComplianceDetail}. The Restricted fields — CWSN/disability, allergies, chronic conditions,
 * medication, blood group, caste and community, religion, EWS/BPL/RTE category, APAAR — are never
 * present in this response; only whether each has been recorded. Fetching this record is not the
 * audited read ADR-0014 asks for. {@code GET …/medical/restricted} and
 * {@code GET …/compliance/restricted} are, and are the only two endpoints that ever return the real
 * values — see {@code StudentAudit#RESTRICTED_DATA_REVEALED}.
 *
 * <p>There is no {@code DELETE} for this resource and there is not going to be one (ADR-0020 §6).
 * A child who leaves is {@link StudentStatus#WITHDRAWN} or {@link StudentStatus#TRANSFERRED}.
 *
 * @param enrolments every placement this student has ever had, newest year first. A history, not a
 *     current state: promotion is a new row, so this is where "which class was she in in 2024-25"
 *     is answered.
 * @param guardians the people responsible for this child, primary contact first
 * @param contact this student's own address, phone and email, or null when nothing has been entered
 * @param previousSchool where this student came from and their transfer certificate, or null when
 *     nothing has been entered — an ordinary first admission has no row here at all
 * @param medical the masked view of this student's health record — never null, even when nothing
 *     has been recorded, because a screen needs somewhere to render "not recorded" from
 * @param compliance the masked view of this student's UDISE+/board identifiers and statutory
 *     categories — never null, for the same reason as {@code medical}
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
        @Classification(Tier.CONFIDENTIAL) List<Enrolment> enrolments,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        ContactDetail contact,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        PreviousSchoolDetail previousSchool,

        @Classification(Tier.CONFIDENTIAL) MedicalSummary medical,
        @Classification(Tier.CONFIDENTIAL) ComplianceSummary compliance) {

    public StudentDetail {
        guardians = guardians == null ? List.of() : List.copyOf(guardians);
        enrolments = enrolments == null ? List.of() : List.copyOf(enrolments);
    }

    public static StudentDetail of(
            Student student,
            CurrentEnrolment currentEnrolment,
            List<StudentGuardian> guardians,
            List<Enrolment> enrolments,
            ContactDetail contact,
            PreviousSchoolDetail previousSchool,
            MedicalSummary medical,
            ComplianceSummary compliance) {
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
                enrolments,
                contact,
                previousSchool,
                medical,
                compliance);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
