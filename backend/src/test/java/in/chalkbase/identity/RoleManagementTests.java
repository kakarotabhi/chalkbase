package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Creating a role, editing its permissions, and granting or revoking it for a user (item 3 of the
 * identity write-endpoint milestone) — and the two guards that make the endpoint set safe to expose
 * to a school: nobody may grant a permission they do not themselves hold, and nothing may leave a
 * school with no account able to manage access at all.
 *
 * <p>Deliberately NOT {@code @Transactional}: the guards read the current, committed shape of
 * {@code user_role_grant} across the whole school, which a rolled-back fixture would misrepresent.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RoleManagementTests {

    private static final String SCHEMA = "lakeside";
    private static final String CODE = "LKS-505";
    private static final String NAME = "Lakeside School";
    private static final String PASSWORD = "Lakeside#2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    SchoolRepository schools;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID principalId;

    @BeforeEach
    void onboardOneSchoolWithOnePrincipal() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, NAME, SCHEMA, Board.CBSE, "Bhopal", "Madhya Pradesh"));
        principalId = createAccount("principal", "Neha Joshi", "PRINCIPAL");
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Create role ──────────────────────────────────────────────────────────────────────────

    @Test
    void createsARoleWithACodeDerivedFromItsName() throws Exception {
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/roles")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Fee Counter Operator, evening shift",
                                 "description": "Collects fees after 4pm",
                                 "permissions": ["school:school:read"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("FEE_COUNTER_OPERATOR_EVENING_SHIFT"))
                .andExpect(jsonPath("$.data.templateCode").doesNotExist())
                .andExpect(jsonPath("$.data.permissions[0]").value("school:school:read"));
    }

    @Test
    void refusesAPermissionTheCatalogueDoesNotDeclare() throws Exception {
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/roles")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Impossible Role", "permissions": ["nonsense:not:real"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    /** identity:role:manage does not, by itself, let a holder grant what they do not have. */
    @Test
    void refusesToCreateARoleWithAPermissionTheActorDoesNotHold() throws Exception {
        UUID accessManagerOnly = createAccount("officeadmin", "Office Admin", null);
        grantCustomRole(accessManagerOnly, "ROLE_MANAGE_ONLY", "identity:role:manage");
        Cookie session = signIn("officeadmin");

        mockMvc.perform(post("/api/access/roles")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sneaky Role", "permissions": ["student:student:manage"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_011"));
    }

    // ── Update permissions ───────────────────────────────────────────────────────────────────

    @Test
    void replacesARolesPermissionSet() throws Exception {
        Cookie session = signIn("principal");
        UUID librarian = roleId("LIBRARIAN");

        mockMvc.perform(put("/api/access/roles/" + librarian + "/permissions")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["school:school:read", "identity:user:read"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(2))
                .andExpect(jsonPath("$.data.permissions")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("school:school:read", "identity:user:read")));
    }

    /**
     * The other half of ADR-0031, through the real endpoint rather than a raw SQL edit standing in
     * for one: the moment role management replaces a role's permission set, {@code role.customised}
     * is true, permanently, and {@code RoleTemplateInstaller} must never again add back a permission
     * the template carries and this row does not — the school removed it on purpose. Provisioning
     * (what runs at every startup) is called directly here rather than restarting the app, the same
     * way {@code AccessControlTests} already exercises {@code RoleTemplateInstaller}'s reconciliation.
     */
    @Test
    void editingARolesPermissionsProtectsItFromTemplateReconciliationForever() throws Exception {
        UUID librarian = roleId("LIBRARIAN");

        mockMvc.perform(put("/api/access/roles/" + librarian + "/permissions")
                        .cookie(signIn("principal"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": []}
                                """))
                .andExpect(status().isOk());

        provisioning.provision(SCHEMA);

        assertThat(jdbc.sql("select permission_code from " + SCHEMA + ".role_permission where role_id = ?")
                        .param(librarian)
                        .query(String.class)
                        .list())
                .as("LIBRARIAN ships with school:school:read, which the school just removed on purpose")
                .isEmpty();
    }

    @Test
    void removingAPermissionNeverNeedsTheActorToHoldIt() throws Exception {
        UUID accessManagerOnly = createAccount("officeadmin", "Office Admin", null);
        UUID roleId = grantCustomRole(
                accessManagerOnly, "ROLE_MANAGE_PLUS_STUDENT", "identity:role:manage", "student:student:read");
        Cookie session = signIn("officeadmin");

        // officeadmin holds identity:role:manage and student:student:read (via this very role), and
        // removes student:student:read from it — a reduction, so no escalation check applies.
        mockMvc.perform(put("/api/access/roles/" + roleId + "/permissions")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["identity:role:manage"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(1));
    }

    @Test
    void refusesToRemoveTheLastRoleManagePermissionFromTheSchool() throws Exception {
        Cookie session = signIn("principal");
        UUID principalRole = roleId("PRINCIPAL");

        mockMvc.perform(put("/api/access/roles/" + principalRole + "/permissions")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["school:school:read"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
    }

    // ── Grant ────────────────────────────────────────────────────────────────────────────────

    @Test
    void grantsARoleWithinAScope() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", null);
        Cookie session = signIn("principal");
        UUID classTeacherRole = roleId("CLASS_TEACHER");
        UUID sectionId = UUID.randomUUID();

        mockMvc.perform(post("/api/access/users/" + teacher + "/grants")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "scopeType": "SECTION", "scopeId": "%s"}
                                """.formatted(classTeacherRole, sectionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleName").value("Class Teacher"))
                .andExpect(jsonPath("$.data.scopeType").value("SECTION"));

        mockMvc.perform(get("/api/access/users/" + teacher + "/grants").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void refusesASchoolScopeWithATarget() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", null);
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + teacher + "/grants")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "scopeType": "SCHOOL", "scopeId": "%s"}
                                """.formatted(roleId("LIBRARIAN"), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    @Test
    void refusesASectionScopeWithNoTarget() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", null);
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + teacher + "/grants")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "scopeType": "SECTION"}
                                """.formatted(roleId("CLASS_TEACHER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    @Test
    void refusesAWardScope() throws Exception {
        UUID parent = createAccount("aparent", "A Parent", null);
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + parent + "/grants")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "scopeType": "WARD"}
                                """.formatted(roleId("PARENT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    /** Granting a role hands the holder every permission it carries; the actor must have them all. */
    @Test
    void refusesToGrantARoleCarryingAPermissionTheActorDoesNotHold() throws Exception {
        UUID accessManagerOnly = createAccount("officeadmin", "Office Admin", null);
        grantCustomRole(accessManagerOnly, "ROLE_MANAGE_ONLY", "identity:role:manage");
        UUID someone = createAccount("someone", "Someone Else", null);
        Cookie session = signIn("officeadmin");

        mockMvc.perform(post("/api/access/users/" + someone + "/grants")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "scopeType": "SCHOOL"}
                                """.formatted(roleId("PRINCIPAL"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_011"));
    }

    // ── Revoke ───────────────────────────────────────────────────────────────────────────────

    @Test
    void revokesAGrant() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", null);
        Cookie session = signIn("principal");
        UUID grantId = grant(teacher, roleId("LIBRARIAN"), "SCHOOL", null);

        mockMvc.perform(delete("/api/access/users/" + teacher + "/grants/" + grantId)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/access/users/" + teacher + "/grants").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** Losing access must not wait up to seven days for the holder's session to expire (ADR-0023). */
    @Test
    void revokingAGrantEndsTheHoldersSessionImmediately() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", "LIBRARIAN");
        Cookie teacherSession = signIn("teacher");
        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isOk());

        UUID theGrant = jdbc.sql("select id from " + SCHEMA + ".user_role_grant where user_account_id = ?")
                .param(teacher)
                .query(UUID.class)
                .single();
        mockMvc.perform(delete("/api/access/users/" + teacher + "/grants/" + theGrant)
                        .cookie(signIn("principal"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isUnauthorized());
    }

    /** Same reasoning, for every holder of a role that loses a permission rather than for one grant. */
    @Test
    void removingAPermissionFromARoleEndsEveryHoldersSessionImmediately() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", "LIBRARIAN");
        Cookie teacherSession = signIn("teacher");
        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isOk());

        mockMvc.perform(put("/api/access/roles/" + roleId("LIBRARIAN") + "/permissions")
                        .cookie(signIn("principal"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": []}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isUnauthorized());
    }

    /** Adding a permission is not urgent — ADR-0005 already accepts "next login" for anything additive. */
    @Test
    void addingAPermissionToARoleEndsNoOnesSession() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", "LIBRARIAN");
        Cookie teacherSession = signIn("teacher");

        mockMvc.perform(put("/api/access/roles/" + roleId("LIBRARIAN") + "/permissions")
                        .cookie(signIn("principal"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["school:school:read", "identity:user:read"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isOk());
    }

    @Test
    void refusesToRevokeTheLastGrantThatCanManageAccess() throws Exception {
        Cookie session = signIn("principal");
        UUID onlyGrant = jdbc.sql("select id from " + SCHEMA + ".user_role_grant where user_account_id = ?")
                .param(principalId)
                .query(UUID.class)
                .single();

        mockMvc.perform(delete("/api/access/users/" + principalId + "/grants/" + onlyGrant)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
    }

    @Test
    void allowsRevokingAnAccessManagersGrantWhenAnotherRemains() throws Exception {
        UUID secondPrincipal = createAccount("principal2", "Second Principal", "PRINCIPAL");
        Cookie session = signIn("principal");
        UUID onlyGrant = jdbc.sql("select id from " + SCHEMA + ".user_role_grant where user_account_id = ?")
                .param(principalId)
                .query(UUID.class)
                .single();

        mockMvc.perform(delete("/api/access/users/" + principalId + "/grants/" + onlyGrant)
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from " + SCHEMA + ".user_role_grant where user_account_id = ?")
                        .param(secondPrincipal)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private Cookie signIn(String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(CODE, username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    /** With no role, or the named shipped template's role if {@code roleCode} is given. */
    private UUID createAccount(String username, String displayName, String roleCode) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + SCHEMA
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', false, 0)")
                .params(accountId, displayName)
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, username)
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(PASSWORD))
                .update();
        if (roleCode != null) {
            jdbc.sql("insert into " + SCHEMA
                            + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                    .params(UUID.randomUUID(), accountId, roleId(roleCode))
                    .update();
        }
        return accountId;
    }

    /** A school-invented role holding exactly {@code permissions}, granted at SCHOOL scope. */
    private UUID grantCustomRole(UUID accountId, String code, String... permissions) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("insert into " + SCHEMA
                        + ".role (id, code, name, description, template_code) values (?, ?, ?, null, null)")
                .params(roleId, code, code)
                .update();
        for (String permission : permissions) {
            jdbc.sql("insert into " + SCHEMA + ".role_permission (role_id, permission_code) values (?, ?)")
                    .params(roleId, permission)
                    .update();
        }
        jdbc.sql("insert into " + SCHEMA
                        + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), accountId, roleId)
                .update();
        return roleId;
    }

    private UUID grant(UUID accountId, UUID roleId, String scopeType, UUID scopeId) {
        UUID grantId = UUID.randomUUID();
        jdbc.sql(
                        "insert into " + SCHEMA
                                + ".user_role_grant (id, user_account_id, role_id, scope_type, scope_id) values (?, ?, ?, ?, ?)")
                .params(grantId, accountId, roleId, scopeType, scopeId)
                .update();
        return grantId;
    }

    private UUID roleId(String code) {
        return jdbc.sql("select id from " + SCHEMA + ".role where code = ?")
                .param(code)
                .query(UUID.class)
                .single();
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from " + SCHEMA + ".role").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
        provisioning.provision(SCHEMA);
    }
}
