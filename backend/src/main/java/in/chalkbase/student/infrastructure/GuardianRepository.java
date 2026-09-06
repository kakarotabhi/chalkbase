package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.Guardian;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * This school's guardians, as people rather than as somebody's parent.
 *
 * <p>Scoped by {@code search_path} like everything else (ADR-0011).
 *
 * <p>No delete: a guardian survives being detached from a child, which is the whole point of the
 * one {@code DELETE} this module has (ADR-0020 §6).
 */
public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {}
