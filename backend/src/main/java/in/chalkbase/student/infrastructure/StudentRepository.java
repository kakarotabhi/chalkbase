package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.Student;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {}
