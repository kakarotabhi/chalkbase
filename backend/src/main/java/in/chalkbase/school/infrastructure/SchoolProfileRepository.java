package in.chalkbase.school.infrastructure;

import in.chalkbase.school.domain.SchoolProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The current school's profile. Scoped by the connection's {@code search_path}, so there is no
 * tenant filter to write or to forget (ADR-0011).
 */
public interface SchoolProfileRepository extends JpaRepository<SchoolProfile, UUID> {

    /**
     * The one profile row, or empty for a school that has not filled it in yet.
     *
     * <p>{@code findFirst} rather than a query that would throw on a second row: the schema already
     * guarantees there cannot be one ({@code uq_school_profile_singleton}), and a read is not the
     * place to discover that a constraint has been dropped.
     */
    Optional<SchoolProfile> findFirstByOrderByCreatedAtAsc();
}
