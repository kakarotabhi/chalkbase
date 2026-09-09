package in.chalkbase.communication.infrastructure;

import in.chalkbase.communication.domain.Circular;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes for {@link Circular}. */
public interface CircularRepository extends JpaRepository<Circular, UUID> {

    /** The list screen: every circular, newest first, draft and published together. */
    Page<Circular> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
