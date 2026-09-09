package in.chalkbase.communication.infrastructure;

import in.chalkbase.communication.domain.CircularTarget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes for {@link CircularTarget}. */
public interface CircularTargetRepository extends JpaRepository<CircularTarget, UUID> {

    /** Every target a circular was composed with, in the order they were added. */
    List<CircularTarget> findByCircularIdOrderByCreatedAtAsc(UUID circularId);

    /** How many targets a circular carries — the list screen's summary, without loading each one. */
    long countByCircularId(UUID circularId);
}
