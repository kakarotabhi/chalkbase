package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.RoleTemplate;
import in.chalkbase.identity.domain.RoleTemplates;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.tenancy.SchemaName;
import in.chalkbase.platform.tenancy.TenantInitializer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Copies the shipped role templates into one school's own {@code role} table, and reconciles the
 * ones that are already there (ADR-0005, ADR-0031).
 *
 * <p><strong>A copy, never a reference — until role management edits it.</strong> Once a role row
 * exists it belongs to the school: it may be renamed, and its permission set may be changed through
 * {@code RoleManagementService}. If this installer kept every role in lockstep with its template
 * forever, adding a permission to a template in a release would silently widen access at every
 * school that had ever been onboarded — a security incident delivered by an upgrade. That is why the
 * class used to stop at {@code continue} the moment a role's code already existed.
 *
 * <p><strong>That was also the bug.</strong> A role that already exists is not the same thing as a
 * role a school has edited. Before {@link in.chalkbase.identity.domain.Role#isCustomised()} existed
 * there was no way to tell the two apart, so this installer treated every pre-existing role as
 * off-limits — including the eleven of twelve permissions on it that a school never touched. A school
 * onboarded before a release that adds a permission to, say, {@code CLASS_TEACHER} never received
 * that permission at all, ever, because the row already existed from the moment the school signed
 * up. A school onboarded after the same release got the new permission for free, because it went
 * through the insert branch instead. That divergence is what ADR-0031 closes.
 *
 * <p><strong>What reconciliation is and is not.</strong> For a role whose {@code customised} column
 * is false, this adds whatever permission the template now carries that the row does not yet have.
 * It <strong>never removes one</strong> — taking a permission away is role management's decision,
 * made deliberately and guarded by {@link in.chalkbase.identity.application.AccessGuardrails}, never
 * this installer's. And it never touches a role whose {@code customised} column is true: the moment
 * role management replaces what a role grants, that role is the school's, permanently, and this
 * class stops looking at its permission set — a school that deliberately removed a permission from a
 * template role must not see it come back on the next deploy. A template added in a later release is
 * still installed fresh for a school onboarded before it, exactly as before; that path (the
 * {@code existing == null} branch below) is unchanged.
 *
 * <p>A newly granted permission takes effect at the affected accounts' next login (ADR-0023):
 * ADR-0005 already accepts that cadence for anything additive, and nothing here is urgent enough to
 * force a session to end early — unlike a role management edit that removes a permission, which does.
 *
 * <p><strong>Cost.</strong> This runs at every startup, for every school (ADR-0011 records the
 * startup-time budget). Detecting what is missing costs exactly two queries per school — one for the
 * roles, one for their permissions — regardless of how many of the twelve templates need
 * reconciling; only a school with something to grant pays for the {@code insert}s that follow, and a
 * fully reconciled school (every subsequent startup) pays for two selects that find nothing to do.
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
     * into a startup failure a developer sees immediately. This also guarantees reconciliation below
     * can never try to grant a permission the catalogue does not contain.
     */
    public RoleTemplateInstaller(JdbcClient jdbc, PermissionCatalog catalog) {
        this.jdbc = jdbc;
        catalog.requireAll(RoleTemplates.referencedPermissions());
    }

    @Override
    public void initialize(String schema) {
        String target = SchemaName.requireValid(schema);

        Map<String, ExistingRole> byCode = new HashMap<>();
        for (ExistingRole role : jdbc.sql("select id, code, customised from " + target + ".role")
                .query((rs, rowNum) ->
                        new ExistingRole((UUID) rs.getObject("id"), rs.getString("code"), rs.getBoolean("customised")))
                .list()) {
            byCode.put(role.code(), role);
        }

        Map<UUID, Set<String>> heldByRoleId = new HashMap<>();
        if (!byCode.isEmpty()) {
            for (Object[] row : jdbc.sql("select role_id, permission_code from " + target + ".role_permission")
                    .query((rs, rowNum) ->
                            new Object[] {rs.getObject("role_id", UUID.class), rs.getString("permission_code")})
                    .list()) {
                heldByRoleId
                        .computeIfAbsent((UUID) row[0], id -> new HashSet<>())
                        .add((String) row[1]);
            }
        }

        int installed = 0;
        int reconciled = 0;
        int permissionsGranted = 0;
        for (RoleTemplate template : RoleTemplates.all()) {
            ExistingRole existing = byCode.get(template.code());
            if (existing == null) {
                installRole(target, template);
                installed++;
                continue;
            }
            if (existing.customised()) {
                // The school's own, permanently. See the class javadoc: this is the fix, not a gap
                // in it — a role a school has edited must never be rewritten from its template.
                continue;
            }
            List<String> missing = template.sortedPermissions().stream()
                    .filter(code ->
                            !heldByRoleId.getOrDefault(existing.id(), Set.of()).contains(code))
                    .toList();
            if (missing.isEmpty()) {
                continue;
            }
            for (String permission : missing) {
                jdbc.sql("insert into " + target + ".role_permission (role_id, permission_code) values (?, ?)")
                        .params(existing.id(), permission)
                        .update();
            }
            reconciled++;
            permissionsGranted += missing.size();
            log.info(
                    "Reconciled role {} in {}: granted {} permission(s) added to the template since this role"
                            + " was installed",
                    template.code(),
                    target,
                    missing.size());
        }

        if (installed > 0) {
            log.info("Copied {} role template(s) into {}", installed, target);
        }
        if (reconciled > 0) {
            log.info(
                    "Reconciled {} role template(s) in {}: {} permission(s) granted in total",
                    reconciled,
                    target,
                    permissionsGranted);
        }
    }

    private void installRole(String target, RoleTemplate template) {
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
    }

    private record ExistingRole(UUID id, String code, boolean customised) {}
}
