package in.chalkbase.platform.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Copies {@link IndianStates#ALL} into {@code public.state} (ADR-0029).
 *
 * <p>The same relationship {@code PermissionSeeder} has to the {@code permission} table: code stays
 * the source of truth, this exists so the table can be queried and joined against, and a corrected
 * {@link IndianState#name()} reaches the database on the next startup rather than needing a
 * migration that edits a row a previous migration inserted.
 *
 * <p><strong>Runs once, against {@code public}, before the tenant fan-out</strong> — unlike {@code
 * PermissionSeeder}, which is a {@code TenantInitializer} run once per school. There is exactly one
 * {@code public.state} table, not one per school, so there is exactly one seeding pass, called from
 * {@code TenantMigrationRunner} immediately after {@code TenantMigrations#migratePlatform()} and
 * before the per-school loop — the same ordering guarantee that makes the permission catalogue and
 * role templates exist before the entity manager factory does, applied here so {@code
 * GET /api/reference/states} never answers against an empty table on a cold start.
 *
 * <p>Raw {@link JdbcClient} rather than the entity manager for the same reason {@code
 * PermissionSeeder} uses it: this runs before the entity manager factory exists ({@code
 * TenancyConfiguration} makes it depend on {@code TenantMigrationRunner} finishing first).
 *
 * <p>Idempotent: upserts on {@code code}, the natural key, and updates {@code name} on conflict. A
 * code the current list no longer mentions is left in the table rather than deleted — see the
 * migration comment for why that is not expected to matter here.
 */
@Component
public class ReferenceDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private final JdbcClient jdbc;

    public ReferenceDataSeeder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void seed() {
        for (IndianState state : IndianStates.ALL) {
            jdbc.sql("insert into public.state (code, name) values (?, ?)"
                            + " on conflict (code) do update set name = excluded.name")
                    .params(state.code(), state.name())
                    .update();
        }
        log.debug("Seeded {} state(s) into public.state", IndianStates.ALL.size());
    }
}
