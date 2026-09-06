package in.chalkbase.school.infrastructure;

import in.chalkbase.school.domain.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    /** Only active schools resolve: deactivating a school must stop its users signing in. */
    Optional<School> findByCodeAndActiveIsTrue(String code);

    /**
     * The registry row for a bound tenant.
     *
     * <p>This is the one lookup that goes schema → registry, and it is how a request already inside
     * a school's schema finds out what that school is called. Not a tenancy filter: the schema
     * comes from {@code TenantContext}, never from a request parameter.
     */
    Optional<School> findBySchemaName(String schemaName);
}
