package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.Student;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * This school's students.
 *
 * <p>Scoped by the connection's {@code search_path} (ADR-0011): there is no tenant filter to write
 * or to forget, and a student id from another school is not in this schema — which makes a request
 * naming one a 404 rather than a leak. A method here taking a {@code schoolId} would be a review
 * blocker.
 *
 * <p>The list is a {@link JpaSpecificationExecutor} rather than a wall of derived query methods:
 * three optional filters over one entity is eight derived methods and would be sixteen the moment a
 * fourth arrives. See {@code StudentQueries}, which builds the predicate, and note that an absent
 * filter contributes no predicate at all — so the statement PostgreSQL plans is the one the
 * migration's indexes were built for.
 *
 * <p>There is deliberately <strong>no delete method</strong>, not even an unused inherited one that
 * looks harmless (ADR-0020 §6). {@code JpaRepository} brings several; none is called anywhere in
 * this module, and a review that finds one is finding a bug.
 */
public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    /**
     * Which of these admission numbers this school already holds.
     *
     * <p>For the bulk import, which has to be able to say "row 47 clashes with a child already on
     * the roll" <em>before</em> it writes anything (ADR-0021 §2). One statement for the whole file
     * rather than one per row, and it selects the numbers rather than the students because loading
     * six hundred children to find out that two of them exist is six hundred rows of Confidential
     * data read for a question about strings.
     *
     * <p>Bounded by the import's own row cap, so the {@code in} list is never longer than 2,000.
     */
    @Query("select s.admissionNumber from Student s where s.admissionNumber in :admissionNumbers")
    List<String> findExistingAdmissionNumbers(Collection<String> admissionNumbers);
}
