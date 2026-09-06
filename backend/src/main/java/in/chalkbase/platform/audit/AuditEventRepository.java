package in.chalkbase.platform.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The audit table, in whichever school's schema is currently bound.
 *
 * <p>No {@code schoolId} argument anywhere, and there never may be: the schema is the tenant
 * boundary (ADR-0011) and a repository method taking a school is a review blocker.
 *
 * <p>{@link JpaSpecificationExecutor} rather than a hand-written JPQL query with
 * {@code (:actorId is null or ...)} branches. Three optional filters is eight statements written as
 * one, and the null-guard form leaves PostgreSQL to infer a type for a parameter that is only ever
 * compared to null — which it cannot always do. A specification simply omits the predicate.
 *
 * <p>Inherited {@code delete} and {@code save}-as-update methods exist because
 * {@link JpaRepository} defines them; nothing calls them, and no endpoint reaches them. The
 * append-only rule of ADR-0018 is enforced by there being no write API at all, not by this
 * interface.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
