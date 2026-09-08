package in.chalkbase.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
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
 * Reproduces and closes the bug ADR-0031 exists for: a permission added to a template after a
 * school was already onboarded never reached that school, because the old installer skipped any
 * role whose code already existed — permissions and all (see {@link RoleTemplateInstaller}'s class
 * Javadoc). These tests provision a school, force one of its roles into that "stale" shape by hand
 * — the shape a school onboarded before a template gained a permission is actually in — and prove
 * the installer's next run heals it. A role the school has customised must not be touched the same
 * way, which is the other half of ADR-0031 and the reason this is not a plain "always add what's
 * missing" fix.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RoleTemplateInstallerTests {

    private static final String SCHEMA = "reconcile_test";

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    RoleTemplateInstaller installer;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void freshlyOnboardedSchool() {
        reset();
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    @Test
    void aPermissionMissingFromAnUneditedRoleIsGrantedOnTheNextRun() {
        UUID principalRoleId = roleId("PRINCIPAL");
        // Simulate a school onboarded before `identity:user:manage` existed on this template: the
        // PRINCIPAL row is here (the installer put it there in @BeforeEach) but this one permission
        // is not — exactly the shape the old "skip if the code exists" installer left forever.
        removePermission(principalRoleId, "identity:user:manage");
        assertThat(permissionsOf(principalRoleId)).doesNotContain("identity:user:manage");

        installer.initialize(SCHEMA);

        assertThat(permissionsOf(principalRoleId)).contains("identity:user:manage");
    }

    @Test
    void reconciliationNeverRemovesAPermissionTheRowAlreadyHas() {
        UUID librarianRoleId = roleId("LIBRARIAN");
        // LIBRARIAN ships with only school:school:read. Give it something no template names, the
        // way a school's own edit might, and confirm reconciliation leaves it there — it only ever
        // adds what the template has and the row lacks, and never removes anything.
        addPermission(librarianRoleId, "identity:user:read");

        installer.initialize(SCHEMA);

        assertThat(permissionsOf(librarianRoleId)).contains("school:school:read", "identity:user:read");
    }

    @Test
    void aCustomisedRoleIsNeverReconciled() {
        UUID principalRoleId = roleId("PRINCIPAL");
        removePermission(principalRoleId, "identity:user:manage");
        // The school deliberately removed this permission, and RoleManagementService marks the row
        // customised the moment it changes a permission set — set directly here because this test
        // is about the installer's reaction to the flag, not about who sets it.
        jdbc.sql("update " + SCHEMA + ".role set customised = true where id = ?")
                .params(principalRoleId)
                .update();

        installer.initialize(SCHEMA);

        assertThat(permissionsOf(principalRoleId)).doesNotContain("identity:user:manage");
    }

    @Test
    void runningTwiceGrantsNothingTheSecondTime() {
        UUID principalRoleId = roleId("PRINCIPAL");
        removePermission(principalRoleId, "identity:user:manage");

        installer.initialize(SCHEMA);
        List<String> afterFirstRun = permissionsOf(principalRoleId);
        installer.initialize(SCHEMA);
        List<String> afterSecondRun = permissionsOf(principalRoleId);

        assertThat(afterSecondRun).containsExactlyInAnyOrderElementsOf(afterFirstRun);
    }

    private UUID roleId(String code) {
        return jdbc.sql("select id from " + SCHEMA + ".role where code = ?")
                .param(code)
                .query(UUID.class)
                .single();
    }

    private List<String> permissionsOf(UUID roleId) {
        return jdbc.sql("select permission_code from " + SCHEMA + ".role_permission where role_id = ?")
                .param(roleId)
                .query(String.class)
                .list();
    }

    private void removePermission(UUID roleId, String permission) {
        jdbc.sql("delete from " + SCHEMA + ".role_permission where role_id = ? and permission_code = ?")
                .params(roleId, permission)
                .update();
    }

    private void addPermission(UUID roleId, String permission) {
        jdbc.sql("insert into " + SCHEMA + ".role_permission (role_id, permission_code) values (?, ?)")
                .params(roleId, permission)
                .update();
    }

    /** Same shape as {@code RoleManagementTests#reset}: drop this school's roles and reinstall them fresh. */
    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".role").update();
        provisioning.provision(SCHEMA);
    }
}
