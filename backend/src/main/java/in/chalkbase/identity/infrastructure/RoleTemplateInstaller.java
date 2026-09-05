package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.RoleTemplate;
import in.chalkbase.identity.domain.RoleTemplates;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.tenancy.SchemaName;
import in.chalkbase.platform.tenancy.TenantInitializer;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Copies the shipped role templates into one school's own {@code role} table (ADR-0005).
 *
 * <p><strong>A copy, never a reference.</strong> Once these rows exist they belong to the school:
 * it may rename them, add permissions, remove permissions or delete them outright. This installer
 * therefore never touches a role that is already there. If it did — if it "kept templates in sync"
 * — then adding a permission to a template in a release would silently widen access at every school
 * that had ever been onboarded, which is a security incident delivered by an upgrade. A release
 * changes the templates here; each school's administrator reviews what is new.
 *
 * <p>A template added in a later release is installed on the next startup for schools onboarded
 * before it, because a role that does not exist cannot have been edited and so cannot be
 * overwritten.
 */
@Component
@Order(200)
public class RoleTemplateInstaller implements TenantInitializer {

    private static final Logger log = LoggerFactory.getLogger(RoleTemplateInstaller.class);

    private final JdbcClient jdbc;

    /**
     * The catalogue check happens once, here, rather than per school: a template naming a
     * permission no module declares is a mistake in this build, and the application must not start
     * with it. Failing at construction turns "one school's roles are quietly missing a permission"
     * into a startup failure a developer sees immediately.
     */
    public RoleTemplateInstaller(JdbcClient jdbc, PermissionCatalog catalog) {
        this.jdbc = jdbc;
        catalog.requireAll(RoleTemplates.referencedPermissions());
    }

    @Override
    public void initialize(String schema) {
        String target = SchemaName.requireValid(schema);

        List<String> existing = jdbc.sql("select code from " + target + ".role")
                .query(String.class)
                .list();

        int installed = 0;
        for (RoleTemplate template : RoleTemplates.all()) {
            if (existing.contains(template.code())) {
                continue;
            }
            UUID roleId = UUID.randomUUID();
            jdbc.sql("insert into " + target + ".role (id, code, name, description, template_code)"
                            + " values (?, ?, ?, ?, ?)")
                    .params(roleId, template.code(), template.name(), template.description(), template.code())
                    .update();
            for (String permission : template.sortedPermissions()) {
                jdbc.sql("insert into " + target + ".role_permission (role_id, permission_code) values (?, ?)")
                        .params(roleId, permission)
                        .update();
            }
            installed++;
        }

        if (installed > 0) {
            log.info("Copied {} role template(s) into {}", installed, target);
        }
    }
}
