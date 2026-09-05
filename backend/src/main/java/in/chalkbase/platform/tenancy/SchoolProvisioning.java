package in.chalkbase.platform.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Brings a new school online: create its schema, then migrate it to the current release.
 *
 * <p>A fresh schema receives every tenant migration in order, so a school onboarded today lands on
 * the same version as one onboarded a year ago.
 *
 * <p>This is deliberately serialised. A school created while the startup fan-out is running could
 * otherwise be created at the old version and then miss the migration that is mid-flight.
 */
@Service
public class SchoolProvisioning {

    private static final Logger log = LoggerFactory.getLogger(SchoolProvisioning.class);

    private final TenantMigrations migrations;
    private final TenantRegistry registry;

    public SchoolProvisioning(TenantMigrations migrations, TenantRegistry registry) {
        this.migrations = migrations;
        this.registry = registry;
    }

    /**
     * Creates and migrates {@code schema}. Safe to call again for a schema that already exists —
     * Flyway applies only what is missing.
     */
    public synchronized void provision(String schema) {
        SchemaName.requireValid(schema);
        boolean existed = registry.schemaExists(schema);
        migrations.migrateTenant(schema);
        log.info("{} schema {}", existed ? "Re-migrated existing" : "Provisioned", schema);
    }
}
