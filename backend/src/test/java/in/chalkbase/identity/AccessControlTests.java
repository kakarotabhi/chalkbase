package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.identity.domain.RoleTemplates;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Permissions, roles, grants and enforcement, against two real tenant schemas (ADR-0005).
 *
 * <p>Two schools with the same shipped templates is the fixture that matters. A role is a
 * <em>copy</em>, and the only way to prove a copy is a copy is to change one and look at the other.
 *
 * <p>Deliberately not {@code @Transactional}: the effective permission set is resolved at login
 * from committed rows, so a rolled-back grant would be a grant this test invented and production
 * never sees.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccessControlTests {

    private static final String HILLVIEW_SCHEMA = "hillview";
    private static final String HILLVIEW_CODE = "HLV-303";
    private static final String SEAVIEW_SCHEMA = "seaview";
    private static final String SEAVIEW_CODE = "SVW-404";

    private static final String PASSWORD = "Hillview#2026";

    private static final String SCHOOL_READ = "school:school:read";
    private static final String USER_READ = "identity:user:read";
    private static final String ROLE_MANAGE = "identity:role:manage";

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

    @BeforeEach
    void onboardTwoSchools() {
        reset();
        registerSchool(HILLVIEW_CODE, "Hillview Public School", HILLVIEW_SCHEMA);
        registerSchool(SEAVIEW_CODE, "Seaview Academy", SEAVIEW_SCHEMA);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Seeding ──────────────────────────────────────────────────────────────────────────────

    /** Code is the source of truth; the table is a copy that exists so a role can reference it. */
    @Test
    void seedsTheCatalogueIntoEverySchoolsPermissionTable() {
        for (String schema : List.of(HILLVIEW_SCHEMA, SEAVIEW_SCHEMA)) {
            assertThat(jdbc.sql("select code from " + schema + ".permission")
                            .query(String.class)
                            .list())
                    .contains(SCHOOL_READ, "school:school:create", USER_READ, ROLE_MANAGE);
        }
    }

    // ── Role templates ───────────────────────────────────────────────────────────────────────

    @Test
    void onboardingCopiesEveryShippedTemplateIntoTheSchoolsOwnTables() {
        for (String schema : List.of(HILLVIEW_SCHEMA, SEAVIEW_SCHEMA)) {
            List<String> codes = jdbc.sql("select code from " + schema + ".role order by code")
                    .query(String.class)
                    .list();

            assertThat(codes)
                    .containsExactlyInAnyOrderElementsOf(RoleTemplates.all().stream()
                            .map(template -> template.code())
                            .toList())
                    .contains("PRINCIPAL", "CLASS_TEACHER", "PARENT", "AUDITOR")
                    .hasSize(12);

            // Provenance is recorded, so a school can be told which of its roles came from where.
            assertThat(jdbc.sql("select count(*) from " + schema + ".role where template_code = code")
                            .query(Integer.class)
                            .single())
                    .isEqualTo(12);

            assertThat(permissionsOf(schema, "PRINCIPAL")).containsExactly(ROLE_MANAGE, USER_READ, SCHOOL_READ);
        }
    }

    /** No template holds it: onboarding a campus is a platform-operator action, not a school one. */
    @Test
    void noShippedTemplateCanOnboardASchool() {
        assertThat(jdbc.sql("select count(*) from " + HILLVIEW_SCHEMA
                                + ".role_permission where permission_code = 'school:school:create'")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    /**
     * The test that makes "a copy, not a reference" real. If school roles pointed at shared
     * templates, this would change both schools at once — and a release adding a permission to a
     * template would widen access everywhere, silently.
     */
    @Test
    void editingOneSchoolsRoleDoesNotChangeAnotherSchoolsCopyOfTheSameTemplate() {
        removePermission(HILLVIEW_SCHEMA, "PRINCIPAL", ROLE_MANAGE);
        addPermission(HILLVIEW_SCHEMA, "LIBRARIAN", USER_READ);
        jdbc.sql("update " + HILLVIEW_SCHEMA + ".role set name = 'Head Mistress' where code = 'PRINCIPAL'")
                .update();

        assertThat(permissionsOf(HILLVIEW_SCHEMA, "PRINCIPAL")).containsExactly(USER_READ, SCHOOL_READ);
        assertThat(permissionsOf(SEAVIEW_SCHEMA, "PRINCIPAL")).containsExactly(ROLE_MANAGE, USER_READ, SCHOOL_READ);

        assertThat(permissionsOf(HILLVIEW_SCHEMA, "LIBRARIAN")).containsExactly(USER_READ, SCHOOL_READ);
        assertThat(permissionsOf(SEAVIEW_SCHEMA, "LIBRARIAN")).containsExactly(SCHOOL_READ);

        assertThat(nameOf(HILLVIEW_SCHEMA, "PRINCIPAL")).isEqualTo("Head Mistress");
        assertThat(nameOf(SEAVIEW_SCHEMA, "PRINCIPAL")).isEqualTo("Principal");
    }

    /** Re-provisioning is what runs at every startup. It must never undo a school's edits. */
    @Test
    void reProvisioningDoesNotOverwriteARoleTheSchoolHasEdited() {
        removePermission(HILLVIEW_SCHEMA, "PRINCIPAL", ROLE_MANAGE);

        provisioning.provision(HILLVIEW_SCHEMA);

        assertThat(permissionsOf(HILLVIEW_SCHEMA, "PRINCIPAL")).containsExactly(USER_READ, SCHOOL_READ);
        assertThat(jdbc.sql("select count(*) from " + HILLVIEW_SCHEMA + ".role")
                        .query(Integer.class)
                        .single())
                .isEqualTo(12);
    }

    // ── Effective permissions ────────────────────────────────────────────────────────────────

    /** Access is the union of grants. There are no deny rules (ADR-0005). */
    @Test
    void effectivePermissionsAreTheUnionOfEveryGrant() throws Exception {
        UUID priya = createAccount(HILLVIEW_SCHEMA, "priya", "Priya Menon");
        grant(HILLVIEW_SCHEMA, priya, "CLASS_TEACHER", "SECTION", UUID.randomUUID(), null, null);
        grant(HILLVIEW_SCHEMA, priya, "LIBRARIAN", "SCHOOL", null, null, null);
        grant(HILLVIEW_SCHEMA, priya, "AUDITOR", "SCHOOL", null, null, null);

        // school:school:read comes from all three; identity:user:read only from the auditor grant.
        // identity:role:manage comes from none of them, and no union of allows can produce it.
        mockMvc.perform(login(HILLVIEW_CODE, "priya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(SCHOOL_READ, USER_READ)));
    }

    @Test
    void aUserWithNoGrantsHoldsNoPermissionsAtAll() throws Exception {
        createAccount(HILLVIEW_SCHEMA, "newjoiner", "Arun Shetty");

        mockMvc.perform(login(HILLVIEW_CODE, "newjoiner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    /** "Exam controller during the exam window" is over when the window is. */
    @Test
    void aGrantWhoseValidityHasPassedContributesNothing() throws Exception {
        UUID account = createAccount(HILLVIEW_SCHEMA, "wasprincipal", "Meera Iyer");
        grant(
                HILLVIEW_SCHEMA,
                account,
                "PRINCIPAL",
                "SCHOOL",
                null,
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1));

        mockMvc.perform(login(HILLVIEW_CODE, "wasprincipal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    /** "Acting principal for March" is not yet in force in February. */
    @Test
    void aGrantThatHasNotStartedYetContributesNothing() throws Exception {
        UUID account = createAccount(HILLVIEW_SCHEMA, "willbeprincipal", "Vikram Sen");
        grant(
                HILLVIEW_SCHEMA,
                account,
                "PRINCIPAL",
                "SCHOOL",
                null,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(30));

        mockMvc.perform(login(HILLVIEW_CODE, "willbeprincipal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
    }

    /** Both bounds are inclusive: a grant for today is in force today, on its first and last day. */
    @Test
    void aGrantWhoseWindowIsExactlyTodayIsInForce() throws Exception {
        UUID account = createAccount(HILLVIEW_SCHEMA, "actingprincipal", "Nisha Kurup");
        grant(HILLVIEW_SCHEMA, account, "PRINCIPAL", "SCHOOL", null, LocalDate.now(), LocalDate.now());

        mockMvc.perform(login(HILLVIEW_CODE, "actingprincipal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(SCHOOL_READ, USER_READ, ROLE_MANAGE)));
    }

    // ── Enforcement ──────────────────────────────────────────────────────────────────────────

    @Test
    void refusesAnEndpointTheUserHasNoPermissionFor() throws Exception {
        UUID account = createAccount(HILLVIEW_SCHEMA, "librarian", "Farida Khan");
        grant(HILLVIEW_SCHEMA, account, "LIBRARIAN", "SCHOOL", null, null, null);
        Cookie session = signIn(HILLVIEW_CODE, "librarian");

        mockMvc.perform(get("/api/access/roles").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PERM_001"))
                .andExpect(jsonPath("$.error.message").value("You do not have permission to do that"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void allowsTheSameEndpointForSomeoneWhoHoldsThePermission() throws Exception {
        UUID account = createAccount(HILLVIEW_SCHEMA, "principal", "Ravi Deshpande");
        grant(HILLVIEW_SCHEMA, account, "PRINCIPAL", "SCHOOL", null, null, null);
        Cookie session = signIn(HILLVIEW_CODE, "principal");

        mockMvc.perform(get("/api/access/roles").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[?(@.code == 'PRINCIPAL')].templateCode")
                        .value("PRINCIPAL"));
    }

    /** The catalogue is the same everywhere, but only someone who administers access may read it. */
    @Test
    void separatePermissionsGuardSeparateEndpoints() throws Exception {
        UUID auditor = createAccount(HILLVIEW_SCHEMA, "auditor", "Sanjay Bhatt");
        grant(HILLVIEW_SCHEMA, auditor, "AUDITOR", "SCHOOL", null, null, null);
        Cookie session = signIn(HILLVIEW_CODE, "auditor");

        // The auditor holds identity:user:read …
        mockMvc.perform(get("/api/access/users").cookie(session))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[?(@.displayName == 'Sanjay Bhatt')]").exists());

        // … but not identity:role:manage.
        mockMvc.perform(get("/api/access/permissions").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    /** A permission is held at one school. The same person's account elsewhere is a different account. */
    @Test
    void aGrantAtOneSchoolIsWorthNothingAtAnother() throws Exception {
        UUID atHillview = createAccount(HILLVIEW_SCHEMA, "sharedname", "Anita Roy");
        grant(HILLVIEW_SCHEMA, atHillview, "PRINCIPAL", "SCHOOL", null, null, null);
        createAccount(SEAVIEW_SCHEMA, "sharedname", "Anita Roy");

        mockMvc.perform(get("/api/access/roles").cookie(signIn(HILLVIEW_CODE, "sharedname")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/access/roles").cookie(signIn(SEAVIEW_CODE, "sharedname")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void anUnauthenticatedRequestIsRefusedBeforeAnyPermissionIsConsidered() throws Exception {
        mockMvc.perform(get("/api/access/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private RequestBuilder login(String schoolCode, String username) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"schoolCode": "%s", "username": "%s", "password": "%s"}
                        """.formatted(
                        schoolCode, username, PASSWORD));
    }

    private Cookie signIn(String schoolCode, String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(schoolCode, username))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void registerSchool(String code, String name, String schema) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, "Kochi", "Kerala"));
    }

    private UUID createAccount(String schema, String username, String displayName) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', false, 0)")
                .params(accountId, displayName)
                .update();
        jdbc.sql("insert into " + schema + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, username)
                .update();
        jdbc.sql("insert into " + schema + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(PASSWORD))
                .update();
        return accountId;
    }

    private void grant(
            String schema,
            UUID accountId,
            String roleCode,
            String scopeType,
            UUID scopeId,
            LocalDate validFrom,
            LocalDate validTo) {
        jdbc.sql("insert into " + schema
                        + ".user_role_grant (id, user_account_id, role_id, scope_type, scope_id, valid_from, valid_to)"
                        + " values (?, ?, ?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), accountId, roleId(schema, roleCode), scopeType, scopeId, validFrom, validTo)
                .update();
    }

    private UUID roleId(String schema, String code) {
        return jdbc.sql("select id from " + schema + ".role where code = ?")
                .param(code)
                .query(UUID.class)
                .single();
    }

    private String nameOf(String schema, String roleCode) {
        return jdbc.sql("select name from " + schema + ".role where code = ?")
                .param(roleCode)
                .query(String.class)
                .single();
    }

    private List<String> permissionsOf(String schema, String roleCode) {
        return jdbc.sql("select permission_code from " + schema + ".role_permission rp"
                        + " join " + schema + ".role r on r.id = rp.role_id where r.code = ?"
                        + " order by permission_code")
                .param(roleCode)
                .query(String.class)
                .list();
    }

    private void removePermission(String schema, String roleCode, String permission) {
        jdbc.sql("delete from " + schema + ".role_permission where role_id = ? and permission_code = ?")
                .params(roleId(schema, roleCode), permission)
                .update();
    }

    private void addPermission(String schema, String roleCode, String permission) {
        jdbc.sql("insert into " + schema + ".role_permission (role_id, permission_code) values (?, ?)")
                .params(roleId(schema, roleCode), permission)
                .update();
    }

    /**
     * Roles are deleted and reinstalled between tests, because several of these deliberately edit a
     * school's copy of a template and the next test must start from the shipped one.
     */
    private void reset() {
        for (String schema : List.of(HILLVIEW_SCHEMA, SEAVIEW_SCHEMA)) {
            provisioning.provision(schema);
            // user_account cascades to its grants; role cascades to its permissions.
            jdbc.sql("delete from " + schema + ".user_account").update();
            jdbc.sql("delete from " + schema + ".role").update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
        for (String schema : List.of(HILLVIEW_SCHEMA, SEAVIEW_SCHEMA)) {
            provisioning.provision(schema);
        }
    }
}
