package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * This school's roles. Scoped by the connection's {@code search_path}, so there is no tenant filter
 * to write or to forget (ADR-0011).
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCode(String code);
}
