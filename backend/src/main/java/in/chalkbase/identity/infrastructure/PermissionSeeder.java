package in.chalkbase.identity.infrastructure;

import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.tenancy.SchemaName;
import in.chalkbase.platform.tenancy.TenantInitializer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Copies the permission catalogue into one school's {@code permission} table (ADR-0005).
 *
 * <p>Code stays the source of truth; these rows exist so a role can carry a foreign key to a
 * permission and a principal can read a real list in a UI. Runs at every startup for every school
 * and again when a school is onboarded, so it is an upsert: a changed label reaches every school on
 * the next boot without a migration.
 *
 * <p><strong>Rows for permissions that no longer exist in code are logged, never deleted.</strong>
 * A school's role may still reference one, and deleting the permission would either fail on the
 * foreign key or, worse, cascade away part of a role the school configured. Removing a permission
 * for real is a migration that rewrites {@code role_permission} first — the price of permission
 * identifiers being effectively public API.
 *
 * <p>Ordered before {@link RoleTemplateInstaller}: a template cannot point a foreign key at a
 * permission row that does not exist yet.
 */
@Component
@Order(100)
public class PermissionSeeder implements TenantInitializer {

    private static final Logger log = LoggerFactory.getLogger(PermissionSeeder.class);

    private final JdbcClient jdbc;
    private final PermissionCatalog catalog;

    public PermissionSeeder(JdbcClient jdbc, PermissionCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    /**
     * The schema is interpolated rather than bound, because a schema name is an identifier and no
     * driver binds those. {@link SchemaName#requireValid} is what makes that safe, and it is the
     * same guard {@code TenantMigrations} uses. This runs before the entity manager factory exists,
     * so there is no JPA here and {@code TenantContext} would not be consulted — the qualification
     * is the tenant selection.
     */
    @Override
    public void initialize(String schema) {
        String target = SchemaName.requireValid(schema);

        for (PermissionDefinition permission : catalog.all()) {
            jdbc.sql("insert into " + target + ".permission (code, module, label, description)"
                            + " values (?, ?, ?, ?)"
                            + " on conflict (code) do update set module = excluded.module,"
                            + " label = excluded.label, description = excluded.description")
                    .params(permission.code(), permission.module(), permission.label(), permission.description())
                    .update();
        }

        List<String> stale =
                jdbc.sql("select code from " + target + ".permission order by code").query(String.class).list().stream()
                        .filter(code -> !catalog.contains(code))
                        .toList();
        if (!stale.isEmpty()) {
            log.warn(
                    "{} has {} permission row(s) no module declares any more: {}. They are left in place because a"
                            + " role may still reference them; removing one needs a migration that rewrites"
                            + " role_permission first.",
                    target,
                    stale.size(),
                    stale);
        }

        log.debug("Seeded {} permission(s) into {}", catalog.all().size(), target);
    }
}
