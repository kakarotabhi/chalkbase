package in.chalkbase.platform.tenancy;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Migrates `public` and then every active school, at startup, before anything can serve a request.
 *
 * <p>This is an {@link InitializingBean} rather than an {@code ApplicationRunner} on purpose: runners
 * fire after the web server is already accepting connections, which would leave a window where a
 * request could reach an unmigrated schema. {@link TenancyConfiguration} additionally makes the
 * entity manager factory depend on this bean, so no query can be issued before it has finished.
 *
 * <p><strong>Startup migration has a recorded expiry</strong> (ADR-0011). Startup time is linear in
 * tenant count, every replica migrates, and a failure here stops the application for every school —
 * including the ones that migrated cleanly. Move this to a deploy step once startup passes about a
 * minute, a second replica appears, or tenants pass ~50. Only the trigger changes; this code does not.
 */
@Component
public class TenantMigrationRunner implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final TenantMigrations migrations;
    private final TenantRegistry registry;

    public TenantMigrationRunner(TenantMigrations migrations, TenantRegistry registry) {
        this.migrations = migrations;
        this.registry = registry;
    }

    @Override
    public void afterPropertiesSet() {
        migrations.migratePlatform();

        List<String> schemas = registry.activeSchemas();
        if (schemas.isEmpty()) {
            log.info("No schools registered yet; nothing to migrate");
            return;
        }

        long start = System.currentTimeMillis();
        List<String> failed = new ArrayList<>();
        for (String schema : schemas) {
            try {
                migrations.migrateTenant(schema);
            } catch (RuntimeException ex) {
                // Collect rather than abort, so one school's failure does not hide the state of the
                // rest. The startup still fails — a partially migrated fleet must not serve traffic.
                log.error("Migration failed for {}", schema, ex);
                failed.add(schema);
            }
        }

        log.info("Migrated {} school(s) in {} ms", schemas.size() - failed.size(), System.currentTimeMillis() - start);

        if (!failed.isEmpty()) {
            throw new IllegalStateException(
                    "Tenant migration failed for " + failed + ". The remaining schools are on the new "
                            + "version; these are not. Repair them before starting again.");
        }
    }
}
