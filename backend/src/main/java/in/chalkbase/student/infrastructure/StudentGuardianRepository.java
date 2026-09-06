package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.StudentGuardianLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** The links between children and the people responsible for them. */
public interface StudentGuardianRepository extends JpaRepository<StudentGuardianLink, UUID> {

    /**
     * One child's guardians with the people behind them, in one query, primary contact first.
     *
     * <p>A fetch join, because the guardian is what the row is mostly made of: without it, four
     * links are five queries. Then by relation and name, so a record redrawn after an edit does not
     * reshuffle.
     */
    @Query("select l from StudentGuardianLink l join fetch l.guardian g where l.student.id = :studentId"
            + " order by l.primary desc, l.relation asc, g.fullName asc")
    List<StudentGuardianLink> findByStudentIdWithGuardian(UUID studentId);

    /**
     * One guardian's children, with the students themselves, in one query.
     *
     * <p>The mirror image of {@link #findByStudentIdWithGuardian}, and what turns "linked to 4
     * students" from a number into an answer. A clerk holding two similar guardian records cannot
     * tell them apart from a count; the names of the children are what say which Suresh Kulkarni is
     * the one already here.
     *
     * <p>A fetch join for the same reason as the other direction: without it, four links are five
     * queries. Ordered by the child's name — a guardian has a handful of children, so this is not
     * paged and the order is the one a person reads in.
     */
    @Query("select l from StudentGuardianLink l join fetch l.student s where l.guardian.id = :guardianId"
            + " order by s.fullName asc, s.admissionNumber asc")
    List<StudentGuardianLink> findByGuardianIdWithStudent(UUID guardianId);

    /**
     * The link that currently holds primary for this student, if any.
     *
     * <p>Read before setting a new one, so the old one can be cleared and flushed first.
     * {@code uq_student_guardian_one_primary} is a partial unique index and cannot be deferred, so
     * the order of those two writes is not an implementation detail — see
     * {@code StudentService#link}.
     */
    Optional<StudentGuardianLink> findFirstByStudentIdAndPrimaryTrue(UUID studentId);

    Optional<StudentGuardianLink> findByIdAndStudentId(UUID id, UUID studentId);

    /**
     * How many children each of these guardians is linked to.
     *
     * <p>One query for a whole page of the directory rather than a count per row, and the number is
     * what tells a clerk that the person they have found is already somebody's parent (see
     * {@code GuardianSummary}). Returns {@code [guardianId, count]} pairs; a guardian linked to
     * nobody is simply absent, which the caller reads as zero.
     */
    @Query("select l.guardian.id, count(l) from StudentGuardianLink l where l.guardian.id in :guardianIds"
            + " group by l.guardian.id")
    List<Object[]> countLinkedStudents(Collection<UUID> guardianIds);
}
