package in.chalkbase.platform.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
 *
 * <p>{@link #deleteBatchOlderThan} is the one exception, and it is not a write API either — nothing
 * HTTP-reachable calls it. It exists for {@code AuditRetentionPurgeJob} (ADR-0018 §6, ADR-0026)
 * alone, and it is bounded on purpose: an unbounded {@code delete from audit_event where
 * occurred_at < ?} on a school with years of history is one long-held lock on a table every write
 * in that schema's request also touches.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    /**
     * Deletes up to {@code batchSize} of the oldest rows with {@code occurredAt} before
     * {@code cutoff}, and reports how many that was.
     *
     * <p>Native SQL rather than JPQL: {@code delete ... where id in (select ... limit ?)} needs a
     * {@code LIMIT} inside a subquery, which JPQL has no syntax for. {@code idx_audit_event_occurred}
     * serves the inner {@code order by}, so this is an index scan bounded to {@code batchSize} rows,
     * not a sequential scan of the whole table.
     *
     * <p>Call this in a loop from a short {@code REQUIRES_NEW} transaction per call — never once
     * with an unbounded {@code cutoff} window — so a school with years of history is purged as many
     * short transactions instead of one that holds locks on a table every request in that schema
     * writes to.
     *
     * @return how many rows this call deleted; less than {@code batchSize} means nothing older than
     *     {@code cutoff} remains
     */
    @Modifying
    @Query(value = """
                    delete from audit_event
                    where id in (
                        select id from audit_event
                        where occurred_at < :cutoff
                        order by occurred_at
                        limit :batchSize
                    )
                    """, nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
