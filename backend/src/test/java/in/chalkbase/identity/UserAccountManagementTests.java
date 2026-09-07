package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.audit.AuditAction;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Creating, deactivating, reactivating, unlocking and resetting the password of an account (item 2
 * of the identity write-endpoint milestone).
 *
 * <p>Deliberately NOT {@code @Transactional}: several tests turn on a write on one request being
 * visible to a later one — a reset ending a session, a deactivation being idempotent — which a
 * rolled-back transaction would never show.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserAccountManagementTests {

    private static final String SCHEMA = "riverside";
    private static final String CODE = "RVS-909";
    private static final String NAME = "Riverside School";
    private static final String PASSWORD = "Riverside#2026";

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

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private UUID principalId;

    @BeforeEach
    void onboardOneSchoolWithOnePrincipal() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, NAME, SCHEMA, Board.CBSE, "Guwahati", "Assam"));
        principalId = createAccount("principal", "Deepa Nair", "PRINCIPAL");
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Create ───────────────────────────────────────────────────────────────────────────────

    @Test
    void createsAnAccountWithAGeneratedPasswordThatOwesAChange() throws Exception {
        Cookie session = signIn("principal");

        MockHttpServletResponse response = mockMvc.perform(post("/api/access/users")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "newteacher", "displayName": "Ajay Kumar"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("newteacher"))
                .andExpect(jsonPath("$.data.displayName").value("Ajay Kumar"))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andReturn()
                .getResponse();

        assertThat(response.getContentAsString()).doesNotContain("\"password\"");

        // The new account is unusable except to change the password it was issued — the existing
        // forced-change machinery, unmodified.
        String temporaryPassword = JSON.readTree(response.getContentAsString())
                .path("data")
                .path("temporaryPassword")
                .asText();
        Cookie newAccountSession = signInAs("newteacher", temporaryPassword);
        mockMvc.perform(get("/api/me").cookie(newAccountSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true));
    }

    @Test
    void refusesASecondAccountWithTheSameUsername() throws Exception {
        Cookie session = signIn("principal");
        createAccountViaApi(session, "duplicate", "First One");

        mockMvc.perform(post("/api/access/users")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "duplicate", "displayName": "Second One"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_009"));
    }

    // ── Deactivate / reactivate / unlock: idempotent ────────────────────────────────────────────

    @Test
    void deactivatingTwiceChangesNothingTheSecondTime() throws Exception {
        // A subject teacher holds no ROLE_MANAGE grant, so the last-access-manager guard is not
        // what this test is about — it is purely about the second call being a no-op.
        UUID account = createAccount("teacher", "Some Teacher", "SUBJECT_TEACHER");
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + account + "/deactivate")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(post("/api/access/users/" + account + "/deactivate")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertThat(countOf(AuditAction.ENTITY_UPDATED, account)).isEqualTo(1);
    }

    @Test
    void reactivatingAnAlreadyActiveAccountChangesNothing() throws Exception {
        UUID account = createAccount("teacher", "Some Teacher", "SUBJECT_TEACHER");
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + account + "/reactivate")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countOf(AuditAction.ENTITY_UPDATED, account)).isZero();
    }

    @Test
    void unlockingAnAccountThatWasNotLockedChangesNothing() throws Exception {
        UUID account = createAccount("teacher", "Some Teacher", "SUBJECT_TEACHER");
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + account + "/unlock")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(countOf(AuditAction.ENTITY_UPDATED, account)).isZero();
    }

    // ── Deactivate: session actually ends, and the last-manager guard ──────────────────────────

    @Test
    void deactivatingAnAccountEndsItsSessionsImmediately() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", "SUBJECT_TEACHER");
        Cookie teacherSession = signIn("teacher");
        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isOk());

        mockMvc.perform(post("/api/access/users/" + teacher + "/deactivate")
                        .cookie(signIn("principal"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToDeactivateTheLastAccountThatCanManageAccess() throws Exception {
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + principalId + "/deactivate")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));

        assertThat(statusOf(principalId)).isEqualTo("ACTIVE");
    }

    @Test
    void allowsDeactivatingAnAccessManagerWhenAnotherOneRemains() throws Exception {
        UUID secondPrincipal = createAccount("principal2", "Second Principal", "PRINCIPAL");
        Cookie session = signIn("principal");

        mockMvc.perform(post("/api/access/users/" + principalId + "/deactivate")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertThat(statusOf(secondPrincipal)).isEqualTo("ACTIVE");
    }

    // ── Reset password ───────────────────────────────────────────────────────────────────────

    @Test
    void resettingAPasswordEndsExistingSessionsAndTheOldPasswordStopsWorking() throws Exception {
        UUID teacher = createAccount("teacher", "Some Teacher", "SUBJECT_TEACHER");
        Cookie teacherSession = signIn("teacher");
        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isOk());

        mockMvc.perform(post("/api/access/users/" + teacher + "/reset-password")
                        .cookie(signIn("principal"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());

        mockMvc.perform(get("/api/me").cookie(teacherSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "teacher", "password": "%s"}
                                """.formatted(CODE, PASSWORD)))
                .andExpect(status().isUnauthorized());

        assertThat(countOf(AuditAction.PASSWORD_RESET_BY_ADMIN, teacher)).isEqualTo(1);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private Cookie signIn(String username) throws Exception {
        return signInAs(username, PASSWORD);
    }

    private Cookie signInAs(String username, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(CODE, username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void createAccountViaApi(Cookie session, String username, String displayName) throws Exception {
        mockMvc.perform(post("/api/access/users")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "displayName": "%s"}
                                """.formatted(username, displayName)))
                .andExpect(status().isCreated());
    }

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
        UUID roleId = jdbc.sql("select id from " + SCHEMA + ".role where code = ?")
                .param(roleCode)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + SCHEMA
                        + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), accountId, roleId)
                .update();
        return accountId;
    }

    private String statusOf(UUID accountId) {
        return jdbc.sql("select status from " + SCHEMA + ".user_account where id = ?")
                .param(accountId)
                .query(String.class)
                .single();
    }

    private int countOf(String action, UUID entityId) {
        return jdbc.sql("select count(*) from " + SCHEMA + ".audit_event where action = ? and entity_id = ?")
                .params(action, entityId.toString())
                .query(Integer.class)
                .single();
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
