package in.chalkbase.school.infrastructure;

import in.chalkbase.school.domain.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    /** Only active schools resolve: deactivating a school must stop its users signing in. */
    Optional<School> findByCodeAndActiveIsTrue(String code);
}
