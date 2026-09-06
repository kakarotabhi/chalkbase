package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentEnrolment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Placements, read for one student's record and in bulk for a page of the class list. */
public interface StudentEnrolmentRepository extends JpaRepository<StudentEnrolment, UUID> {

    /**
     * One student's whole history, newest year first.
     *
     * <p>Ordered by {@code enrolledOn} rather than by the session's start date, because the session
     * belongs to another module and cannot be joined to from here (ADR-0020). The two agree in every
     * ordinary case; the service sorts the assembled rows by the session start date it has resolved,
     * and this ordering is what makes the result deterministic before that happens.
     */
    List<StudentEnrolment> findByStudentIdOrderByEnrolledOnDescCreatedAtDesc(UUID studentId);

    /**
     * The live placements of a page of students, in one query.
     *
     * <p>The alternative is one query per row, which for a class list of forty is forty. Narrowed to
     * one session because that is what "current" means here — see {@code StudentService}.
     */
    List<StudentEnrolment> findByActiveTrueAndAcademicSessionIdAndStudentIdIn(
            UUID academicSessionId, Collection<UUID> studentIds);

    /**
     * The one placement {@code uq_student_enrolment_one_active} allows for this student and year, if
     * there is one.
     *
     * <p>{@code findFirst} rather than a query that would throw on a second row: the partial unique
     * index already guarantees there cannot be one, and a check performed before a write is not the
     * place to discover that an index has been dropped.
     */
    Optional<StudentEnrolment> findFirstByStudentIdAndAcademicSessionIdAndActiveTrue(
            UUID studentId, UUID academicSessionId);

    Optional<StudentEnrolment> findByIdAndStudentId(UUID id, UUID studentId);
}
