package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.AcademicSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query here is implicitly scoped to the current school: the connection's {@code search_path}
 * decides which schema the table lives in, so there is no tenant filter to write or to forget.
 */
public interface AcademicSessionRepository extends JpaRepository<AcademicSession, UUID> {

    /**
     * Newest first. Not paged: a school accumulates one of these a year, and a page control over
     * fourteen rows is a control nobody uses.
     *
     * <p>Name ascending only breaks a tie, so two sessions that somehow start on the same day still
     * come back in the same order every time.
     */
    List<AcademicSession> findAllByOrderByStartsOnDescNameAsc();

    /**
     * The session the school is in, if it has said.
     *
     * <p>{@code findFirst} rather than a query that would throw on a second row: the partial unique
     * index {@code uq_academic_session_one_current} already guarantees there cannot be one, and a
     * read is not the place to discover that an index has been dropped.
     */
    Optional<AcademicSession> findFirstByCurrentTrue();
}
