package in.chalkbase.school.infrastructure;

import in.chalkbase.school.domain.AcademicSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query here is implicitly scoped to the current school: the connection's {@code search_path}
 * decides which schema the table lives in, so there is no tenant filter to write or to forget.
 */
public interface AcademicSessionRepository extends JpaRepository<AcademicSession, UUID> {}
