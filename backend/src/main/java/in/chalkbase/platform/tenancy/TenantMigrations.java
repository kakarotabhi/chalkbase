package in.chalkbase.platform.tenancy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the two migration sets.
 *
 * <p>Flyway has no notion of "apply to every tenant" — it migrates one schema and records it in one
 * history table — so the fan-out is ours (ADR-0011). Each tenant schema keeps its own
 * {@code flyway_schema_history}, so versions are tracked per school.
 */
@Component
public class TenantMigrations {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrations.class);

    static final String SHARED_LOCATION = "classpath:db/migration/shared";
    static final String TENANT_LOCATION = "classpath:db/migration/tenant";

    private final DataSource dataSource;

    public TenantMigrations(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Migrates `public`: the tenant registry and shared reference data. */
    public void migratePlatform() {
        migrate(TenantContext.PLATFORM, SHARED_LOCATION);
    }

    /** Migrates one school's schema, creating it if it does not exist yet. */
    public void migrateTenant(String schema) {
        migrate(SchemaName.requireValid(schema), TENANT_LOCATION);
    }

    private void migrate(String schema, String location) {
        long start = System.currentTimeMillis();
        var result = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(location)
                .baselineOnMigrate(false)
                .load()
                .migrate();

        if (result.migrationsExecuted > 0) {
            log.info(
                    "Migrated {} to {} ({} migration(s), {} ms)",
                    schema,
                    result.targetSchemaVersion,
                    result.migrationsExecuted,
                    System.currentTimeMillis() - start);
        } else {
            // targetSchemaVersion is null when nothing ran, so do not try to print it.
            log.debug("{} already current", schema);
        }
    }
}
