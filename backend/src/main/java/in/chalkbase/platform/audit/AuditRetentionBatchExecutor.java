package in.chalkbase.platform.audit;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one transaction boundary of the retention purge, and nothing else — mirrors
 * {@link AuditEventWriter} and exists for the same reason.
 *
 * <p><strong>Why this is a separate bean from {@link AuditRetentionPurgeJob}.</strong> The purge job
 * binds the tenant schema with {@code TenantContext.callWith} and then has to cross a proxy boundary
 * for Hibernate to open its session against that schema; annotating a method on the job itself and
 * calling it from another method on the same bean would be a self-invocation, which never passes
 * through the Spring AOP proxy, so {@code @Transactional} would silently do nothing. Putting the
 * annotated method one hop away, on a different bean, is what makes it real.
 *
 * <p>One call deletes one bounded batch, in its own {@code REQUIRES_NEW} transaction, and commits
 * before the next call starts. That is deliberate: a school with years of history is purged as many
 * short transactions rather than one that holds locks on {@code audit_event} — a table every write
 * in that schema's request also inserts into — for as long as the whole purge takes.
 */
@Component
class AuditRetentionBatchExecutor {

    private final AuditEventRepository events;

    AuditRetentionBatchExecutor(AuditEventRepository events) {
        this.events = events;
    }

    /**
     * Deletes up to {@code batchSize} of the oldest rows older than {@code cutoff}, committing
     * before returning.
     *
     * @return how many rows this call deleted; less than {@code batchSize} means the schema has
     *     nothing older than {@code cutoff} left
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int deleteBatch(Instant cutoff, int batchSize) {
        return events.deleteBatchOlderThan(cutoff, batchSize);
    }
}
