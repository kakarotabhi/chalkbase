package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.Subject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * This school's subject catalogue. Scoped by the connection's {@code search_path}, so there is no
 * tenant filter to write or to forget (ADR-0011).
 *
 * <p>{@link JpaSpecificationExecutor} for the same reason {@code GuardianRepository} carries it: an
 * optional free-text search over name and code, alongside ordinary paging.
 */
public interface SubjectRepository extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {}
