package in.chalkbase.communication.infrastructure;

import in.chalkbase.communication.domain.CircularRecipient;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes for {@link CircularRecipient}.
 *
 * <p>{@code idx_circular_recipient_student} exists for a query this interface does not yet declare
 * — {@code where student_id in (wards of the signed-in guardian)}, the parent-inbox read the
 * module's package doc describes. Adding it is a method here, not a migration, the day it is
 * needed.
 */
public interface CircularRecipientRepository extends JpaRepository<CircularRecipient, UUID> {

    /** A circular's own recipient list, in publish order — the detail screen's own read. */
    Page<CircularRecipient> findByCircularIdOrderByCreatedAtAsc(UUID circularId, Pageable pageable);

    /** How many students a circular reached, and how many have acknowledged — the list screen's summary. */
    long countByCircularId(UUID circularId);

    long countByCircularIdAndAcknowledgedAtIsNotNull(UUID circularId);
}
