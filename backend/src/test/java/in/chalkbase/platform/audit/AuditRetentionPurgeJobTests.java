package in.chalkbase.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.platform.tenancy.TenantRegistry;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The retention purge (ADR-0018 §6, ADR-0026): bounded batches, per-tenant fan-out that must not
 * cross schools, and the summary event it must write for itself without ever purging that same row
 * in the run that created it.
 *
 * <p>Two schools with different histories, for the same reason {@code AuditApiTests} and
 * {@code TenantIsolationTests} use two: the interesting assertion is the one where purging one
 * school leaves the other untouched, not the one where a single school's old rows disappear.
 *
 * <p>Every fixture here is an invented school. Never real student data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditRetentionPurgeJobTests {

    private static final String MAPLEGROVE_SCHEMA = "maplegrove";
    private static final String OAKRIDGE_SCHEMA = "oakridge";

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    SchoolRepository schools;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AuditEventRepository events;

    @Autowired
    TenantRegistry registry;

    @Autowired
    AuditService audit;

    @BeforeEach
    void onboardTwoSchools() {
        reset();
        schools.save(new School(
                "MPG-101", "Maplegrove Public School", MAPLEGROVE_SCHEMA, Board.CBSE, "Pune", "Maharashtra"));
        schools.save(new School("OKR-202", "Oakridge Academy", OAKRIDGE_SCHEMA, Board.CBSE, "Pune", "Maharashtra"));
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Tenant isolation ─────────────────────────────────────────────────────────────────────

    @Test
    void purgesOnlyTheSchoolWithExpiredRowsAndLeavesTheOtherAlone() {
        seed(MAPLEGROVE_SCHEMA, 3, Instant.now().minus(3 * 365, ChronoUnit.DAYS)); // older than a 2-year cutoff
        seed(MAPLEGROVE_SCHEMA, 2, Instant.now().minus(1, ChronoUnit.DAYS)); // recent
        seed(OAKRIDGE_SCHEMA, 4, Instant.now().minus(1, ChronoUnit.DAYS)); // all recent, nothing to purge

        job(2).purgeExpiredEvents();

        assertThat(countRows(MAPLEGROVE_SCHEMA))
                .as("old rows purged, recent rows kept, plus the summary row")
                .isEqualTo(3);
        assertThat(countRows(OAKRIDGE_SCHEMA))
                .as("untouched — nothing there was old enough")
                .isEqualTo(4);
        assertThat(countPurgeSummaries(OAKRIDGE_SCHEMA))
                .as("no summary row when nothing was purged")
                .isZero();
    }

    // ── Batching ─────────────────────────────────────────────────────────────────────────────

    /** Five old rows, a batch size of two: the job must loop until nothing older than cutoff is left. */
    @Test
    void deletesInBatchesUntilNothingOlderThanTheCutoffRemains() {
        seed(MAPLEGROVE_SCHEMA, 5, Instant.now().minus(3 * 365, ChronoUnit.DAYS));
        seed(MAPLEGROVE_SCHEMA, 1, Instant.now().minus(1, ChronoUnit.DAYS));

        job(2).purgeExpiredEvents();

        assertThat(countRows(MAPLEGROVE_SCHEMA)).isEqualTo(2); // one surviving row + one summary row
        Integer recordCount = jdbc.sql(
                        "select record_count from " + MAPLEGROVE_SCHEMA + ".audit_event where action = ?")
                .param(AuditAction.AUDIT_LOG_PURGED)
                .query(Integer.class)
                .single();
        assertThat(recordCount).isEqualTo(5);
    }

    // ── The purge audits itself, and cannot audit itself into deletion ─────────────────────────

    @Test
    void writesOneSummaryEventNamingHowManyRowsWerePurged() {
        seed(MAPLEGROVE_SCHEMA, 4, Instant.now().minus(3 * 365, ChronoUnit.DAYS));

        job(10).purgeExpiredEvents();

        List<String> actorNames = jdbc.sql(
                        "select actor_name from " + MAPLEGROVE_SCHEMA + ".audit_event where action = ?")
                .param(AuditAction.AUDIT_LOG_PURGED)
                .query(String.class)
                .list();
        assertThat(actorNames).hasSize(1);
        assertThat(actorNames.get(0))
                .as("names itself rather than reading as unattributed")
                .isNotBlank();

        Integer recordCount = jdbc.sql(
                        "select record_count from " + MAPLEGROVE_SCHEMA + ".audit_event where action = ?")
                .param(AuditAction.AUDIT_LOG_PURGED)
                .query(Integer.class)
                .single();
        assertThat(recordCount).isEqualTo(4);

        // The summary row itself must survive the run that wrote it — it is timestamped "now",
        // never older than the cutoff that triggered the run.
        assertThat(countRows(MAPLEGROVE_SCHEMA)).isEqualTo(1);
    }

    @Test
    void writesNoSummaryEventWhenNothingWasOldEnoughToPurge() {
        seed(MAPLEGROVE_SCHEMA, 3, Instant.now().minus(1, ChronoUnit.DAYS));

        job(10).purgeExpiredEvents();

        assertThat(countRows(MAPLEGROVE_SCHEMA)).isEqualTo(3);
        assertThat(countPurgeSummaries(MAPLEGROVE_SCHEMA)).isZero();
    }

    // ── The tripwire ─────────────────────────────────────────────────────────────────────────

    @Test
    void doesNothingWhenDisabled() {
        seed(MAPLEGROVE_SCHEMA, 3, Instant.now().minus(3 * 365, ChronoUnit.DAYS));

        AuditRetentionPurgeJob disabled =
                new AuditRetentionPurgeJob(registry, new AuditRetentionBatchExecutor(events), audit, 2, 100, false);
        disabled.purgeExpiredEvents();

        assertThat(countRows(MAPLEGROVE_SCHEMA)).as("nothing ran").isEqualTo(3);
    }

    @Test
    void refusesARetentionPeriodShorterThanOneYear() {
        assertThat(catchThrown(() -> new AuditRetentionPurgeJob(
                        registry, new AuditRetentionBatchExecutor(events), audit, 0, 100, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesABatchSizeSmallerThanOne() {
        assertThat(catchThrown(() -> new AuditRetentionPurgeJob(
                        registry, new AuditRetentionBatchExecutor(events), audit, 2, 0, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private AuditRetentionPurgeJob job(int batchSize) {
        // retentionYears is fixed at 2 for every test: everything seeded at Instant.now().minus(3 years) is
        // older than "now minus 2 years" and everything seeded at Instant.now().minus(1 day) is not, whatever
        // day the test actually runs on.
        return new AuditRetentionPurgeJob(registry, new AuditRetentionBatchExecutor(events), audit, 2, batchSize, true);
    }

    private void seed(String schema, int count, Instant occurredAt) {
        for (int index = 0; index < count; index++) {
            jdbc.sql("insert into " + schema + ".audit_event (id, occurred_at, actor_id, actor_name, actor_roles,"
                            + " action, entity_type, entity_id, changed_fields, outcome)"
                            + " values (?, ?, null, 'Fixture Actor', 'AUDITOR', ?, 'STUDENT', ?, 'section', 'SUCCESS')")
                    .params(
                            UUID.randomUUID(),
                            OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
                            AuditAction.ENTITY_UPDATED,
                            "fixture-" + index)
                    .update();
        }
    }

    private long countRows(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event")
                .query(Long.class)
                .single();
    }

    private long countPurgeSummaries(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where action = ?")
                .param(AuditAction.AUDIT_LOG_PURGED)
                .query(Long.class)
                .single();
    }

    private void reset() {
        for (String schema : List.of(MAPLEGROVE_SCHEMA, OAKRIDGE_SCHEMA)) {
            provisioning.provision(schema);
            jdbc.sql("delete from " + schema + ".audit_event").update();
        }
        schools.deleteAll();
    }

    private static Throwable catchThrown(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
