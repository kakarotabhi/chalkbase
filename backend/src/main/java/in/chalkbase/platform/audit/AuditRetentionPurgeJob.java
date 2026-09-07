package in.chalkbase.platform.audit;

import in.chalkbase.platform.tenancy.TenantContext;
import in.chalkbase.platform.tenancy.TenantRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes {@code audit_event} rows older than the configured retention period, across every
 * school, on a schedule (ADR-0018 §6, ADR-0026).
 *
 * <p><strong>The period is one number for every category</strong> (ADR-0026, amending the "legal
 * question" ADR-0018 left open): seven years, the Indian financial-record convention, uniformly.
 * ADR-0014 asks for a period per classification tier; the product owner chose one uniform number
 * over a schedule per tier because a single number errs long and long is the safe direction for an
 * audit log. A per-category schedule stays possible later — nothing here assumes there is only ever
 * one period.
 *
 * <p><strong>It runs per tenant, deliberately not against whatever schema happens to be on the
 * connection.</strong> {@link TenantRegistry#activeSchemas()} is the same list
 * {@code TenantMigrationRunner} fans out over at startup; each schema is bound with
 * {@link TenantContext#callWith} before anything is deleted from it, and one school's failure is
 * logged and does not stop the rest — a purge is not the kind of job where a startup-style "stop
 * everything" makes sense, because most schools succeeding while one is investigated is a fine
 * outcome for a housekeeping job, where it would not be for a migration every school depends on
 * being current.
 *
 * <p><strong>Deleting is bounded.</strong> Each school's purge runs as repeated calls to
 * {@link AuditRetentionBatchExecutor#deleteBatch}, {@value #DEFAULT_BATCH_SIZE} rows at a time in
 * its own short transaction, rather than one {@code delete} with an unbounded {@code WHERE}. A
 * first run against a school with years of history can be tens of thousands of rows; one
 * transaction holding a lock on {@code audit_event} for however long that takes is a cost paid by
 * every other write to that schema for the length of the purge, on a connection pool every school
 * shares (ADR-0011). Five hundred is small enough that a batch finishes in a fraction of a second on
 * the indexed {@code occurred_at} scan {@code idx_audit_event_occurred} already serves, and large
 * enough that a school with a few years of ordinary activity does not need thousands of round trips
 * to finish.
 *
 * <p><strong>Deleting audit rows is itself audited</strong> — {@link AuditAction#AUDIT_LOG_PURGED},
 * one row per school per run, naming how many rows were removed and the cutoff used, never which
 * rows. It cannot be recursive: the summary row is written with {@code occurred_at} at the moment of
 * the run, which is never itself older than {@code cutoff}, so it is not eligible for the very sweep
 * that created it — it ages out, correctly, on some future run once it is old enough itself. The
 * actor is {@link AuditActor#system}, not resolved from a security context, because there is no
 * HTTP request behind a scheduled job.
 *
 * <p><strong>Nothing runs this by accident.</strong> There is no endpoint —
 * {@code AuditController} has none, by design (ADR-0018 §6) — so the only way this method runs is
 * the schedule below, and {@code chalkbase.audit.retention.enabled} is a tripwire an operator can
 * set to {@code false} without a redeploy if a run needs to be held back.
 */
@Component
class AuditRetentionPurgeJob {

    /** See the class javadoc for why this number. */
    static final int DEFAULT_BATCH_SIZE = 500;

    /** 02:30 UTC — outside the school day for every timezone this product ships to. */
    static final String DEFAULT_CRON = "0 30 2 * * *";

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionPurgeJob.class);

    private final TenantRegistry registry;
    private final AuditRetentionBatchExecutor batches;
    private final AuditService audit;
    private final int retentionYears;
    private final int batchSize;
    private final boolean enabled;

    AuditRetentionPurgeJob(
            TenantRegistry registry,
            AuditRetentionBatchExecutor batches,
            AuditService audit,
            @Value("${chalkbase.audit.retention.years:7}") int retentionYears,
            @Value("${chalkbase.audit.retention.batch-size:" + DEFAULT_BATCH_SIZE + "}") int batchSize,
            @Value("${chalkbase.audit.retention.enabled:true}") boolean enabled) {
        if (retentionYears < 1) {
            throw new IllegalArgumentException(
                    "chalkbase.audit.retention.years must be at least 1, was " + retentionYears);
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "chalkbase.audit.retention.batch-size must be at least 1, was " + batchSize);
        }
        this.registry = registry;
        this.batches = batches;
        this.audit = audit;
        this.retentionYears = retentionYears;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    /**
     * Runs at {@value #DEFAULT_CRON} UTC by default — configurable via
     * {@code chalkbase.audit.retention.cron} — and package-private so the only caller in production
     * is Spring's scheduler. Tests call it directly; there is deliberately no other path to it.
     */
    @Scheduled(cron = "${chalkbase.audit.retention.cron:" + DEFAULT_CRON + "}", zone = "UTC")
    void purgeExpiredEvents() {
        if (!enabled) {
            log.info("Audit retention purge is disabled (chalkbase.audit.retention.enabled=false); skipping");
            return;
        }

        Instant cutoff =
                ZonedDateTime.now(ZoneOffset.UTC).minusYears(retentionYears).toInstant();
        List<String> schemas = registry.activeSchemas();
        if (schemas.isEmpty()) {
            log.info("Audit retention purge: no schools registered; nothing to do");
            return;
        }

        int schoolsWithRowsPurged = 0;
        long totalRowsPurged = 0;
        List<String> failed = new ArrayList<>();
        for (String schema : schemas) {
            try {
                int deleted = purgeSchema(schema, cutoff);
                if (deleted > 0) {
                    schoolsWithRowsPurged++;
                    totalRowsPurged += deleted;
                }
            } catch (RuntimeException ex) {
                log.error("Audit retention purge failed for {}", schema, ex);
                failed.add(schema);
            }
        }

        log.info(
                "Audit retention purge: {} row(s) across {} of {} school(s), cutoff {}",
                totalRowsPurged,
                schoolsWithRowsPurged,
                schemas.size(),
                cutoff);
        if (!failed.isEmpty()) {
            log.error("Audit retention purge did not complete for {}: {}", failed.size(), failed);
        }
    }

    /** Binds {@code schema} for the duration of its purge, then restores whatever was bound before. */
    private int purgeSchema(String schema, Instant cutoff) {
        try {
            return TenantContext.callWith(schema, () -> purgeBoundSchema(schema, cutoff));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Audit retention purge failed for " + schema, ex);
        }
    }

    /** Runs with {@code schema} already bound. Deletes in bounded batches, then audits the total. */
    private int purgeBoundSchema(String schema, Instant cutoff) {
        int totalDeleted = 0;
        int deletedThisBatch;
        do {
            deletedThisBatch = batches.deleteBatch(cutoff, batchSize);
            totalDeleted += deletedThisBatch;
        } while (deletedThisBatch == batchSize);

        if (totalDeleted == 0) {
            return 0;
        }

        try {
            audit.recordBulkChange(
                    AuditAction.AUDIT_LOG_PURGED,
                    "AUDIT_EVENT",
                    cutoff.toString(),
                    List.of(),
                    totalDeleted,
                    AuditActor.system("Audit retention purge", schema));
        } catch (RuntimeException ex) {
            // The rows are already gone — each batch committed on its own. Losing the summary row
            // is a gap the ERROR log below is the explanation for, but it must not read as the
            // purge itself having failed and it must not be retried: retrying would only shorten
            // the next run by however many rows this one already removed.
            log.error(
                    "Purged {} audit_event row(s) older than {} from {}, but failed to record the purge itself",
                    totalDeleted,
                    cutoff,
                    schema,
                    ex);
            return totalDeleted;
        }

        log.info("Purged {} audit_event row(s) older than {} from {}", totalDeleted, cutoff, schema);
        return totalDeleted;
    }
}
