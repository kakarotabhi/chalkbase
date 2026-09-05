package in.chalkbase.school.infrastructure;

import in.chalkbase.school.domain.School;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {}
