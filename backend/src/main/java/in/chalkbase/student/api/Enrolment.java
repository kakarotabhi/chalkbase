package in.chalkbase.student.api;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.student.domain.StudentEnrolment;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One placement of a student: a year, a class, a section, and a roll number.
 *
 * <p>Ids <em>and</em> names, unlike {@link CurrentEnrolment}. This is returned on a student's own
 * record, where a client offers to edit the placement and therefore has to be able to name the
 * section it is changing to, and where a history of four years has to be readable without four more
 * calls to resolve what "the 2024-25 session" was called.
 *
 * <p>The names come from {@code academics} through its named interface, never from a join: this
 * module owns the enrolment and {@code academics} owns the year, the class and the section
 * (ADR-0020).
 *
 * @param active false for a placement that has ended. A student may have an ended enrolment and a
 *     live one in the same year — moved section mid-term — which is exactly why
 *     {@code uq_student_enrolment_one_active} is a partial index rather than a plain constraint.
 * @param rollNumber null until the class list settles
 */
public record Enrolment(
        UUID id,
        UUID sessionId,
        String sessionName,
        UUID classId,
        String className,
        UUID sectionId,
        String sectionName,
        String rollNumber,
        boolean active,
        LocalDate enrolledOn) {

    /**
     * @param session may be null, and {@code section} likewise. Neither can happen through the API —
     *     both are foreign keys, and both were resolved before the row was written — but a DTO that
     *     threw here would turn a retired-then-repaired academic structure into a 500 on a screen
     *     that only wanted to show a child's history. The row is returned with the name missing
     *     instead, which is the honest answer.
     */
    public static Enrolment of(StudentEnrolment enrolment, AcademicSessionRef session, SectionRef section) {
        return new Enrolment(
                enrolment.getId(),
                enrolment.getAcademicSessionId(),
                session == null ? null : session.name(),
                section == null ? null : section.classId(),
                section == null ? null : section.className(),
                enrolment.getSectionId(),
                section == null ? null : section.name(),
                enrolment.getRollNumber(),
                enrolment.isActive(),
                enrolment.getEnrolledOn());
    }
}
