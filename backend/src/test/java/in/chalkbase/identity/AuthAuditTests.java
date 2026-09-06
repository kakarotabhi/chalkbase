package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.platform.web.RequestId;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
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
 * What the audit log records about signing in (ADR-0018, FR-008).
 *
 * <p>Deliberately not {@code @Transactional}. Security events are written in their own transaction
 * precisely so they outlive the work around them; a test that rolls everything back at the end
 * cannot tell that apart from a row that was never written.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthAuditTests {

    private static final String OAKRIDGE_SCHEMA = "oakridge";
    private static final String OAKRIDGE_CODE = "OAK-606";

    private static final String USERNAME = "2026-0731";
    private static final String PASSWORD = "Oakridge#2026";
    private static final String NEW_PASSWORD = "Peregrine#88";

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

    private UUID accountId;

    @BeforeEach
    void onboardOneSchoolWithOneAccount() {
        reset();
        provisioning.provision(OAKRIDGE_SCHEMA);
        schools.save(
                new School(OAKRIDGE_CODE, "Oakridge Vidyalaya", OAKRIDGE_SCHEMA, Board.CBSE, "Nashik", "Maharashtra"));
        accountId = createAccount("Meenakshi Iyer");
        grantAuditorRole(accountId);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Failed sign-in ───────────────────────────────────────────────────────────────────────

    /**
     * The row that only exists because security events are audited in their own transaction: the
     * attempt threw, and the record of it is still here.
     *
     * <p>No actor id, because nobody was authenticated — that is the honest answer, not a gap. What
     * is known is the identifier that was tried, and it goes in {@code entity_id} as an identifier
     * with {@code entity_type} naming what kind it is. It is not a "value" in the ADR-0014 sense
     * and there is no value field on the row it could have gone into.
     */
    @Test
    void aFailedSignInIsRecordedWithNoActorAndTheIdentifierThatWasTried() throws Exception {
        mockMvc.perform(login(USERNAME, "not-the-password")).andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyRow(AuditAction.LOGIN_FAILED);
        assertThat(row.get("outcome")).isEqualTo("FAILURE");
        assertThat(row.get("actor_id"))
                .as("an unauthenticated attempt has no actor")
                .isNull();
        assertThat(row.get("actor_name")).isNull();
        assertThat(row.get("entity_type")).isEqualTo("USERNAME");
        assertThat(row.get("entity_id")).isEqualTo(USERNAME);
        assertThat(row.get("trace_id")).isNotNull();
    }

    /** An unknown username fails identically, and is recorded identically. */
    @Test
    void anAttemptOnAUsernameThatDoesNotExistIsStillRecorded() throws Exception {
        mockMvc.perform(login("9999-0000", "not-the-password")).andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyRow(AuditAction.LOGIN_FAILED);
        assertThat(row.get("entity_id")).isEqualTo("9999-0000");
        assertThat(row.get("actor_id")).isNull();
    }

    /**
     * ADR-0018 §5. A sign-in attempt against an unknown school code has no tenant to write to and
     * is not recorded — a platform-level concern for later, not a hole in a school's audit trail.
     */
    @Test
    void anAttemptAgainstAnUnknownSchoolCodeIsRecordedNowhere() throws Exception {
        mockMvc.perform(login("NO-SUCH-SCHOOL", USERNAME, PASSWORD)).andExpect(status().isNotFound());

        assertThat(countOf(AuditAction.LOGIN_FAILED)).isZero();
        assertThat(countOf(AuditAction.LOGIN_SUCCEEDED)).isZero();
    }

    // ── Successful sign-in ───────────────────────────────────────────────────────────────────

    /**
     * The actor is a snapshot taken at the moment of the login: the id, the display name as it read
     * then, and the role codes held then. Not a foreign key — an audit row must still read
     * correctly after the account is renamed or deleted.
     */
    @Test
    void aSuccessfulSignInIsRecordedWithTheActorAndTheRequestsTraceId() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(USERNAME, PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        Map<String, Object> row = onlyRow(AuditAction.LOGIN_SUCCEEDED);
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("actor_id")).isEqualTo(accountId);
        assertThat(row.get("actor_name")).isEqualTo("Meenakshi Iyer");
        assertThat(row.get("actor_roles")).isEqualTo("AUDITOR");
        assertThat(row.get("entity_type")).isEqualTo("USERNAME");

        // The same id the ADR-0007 envelope returned, so a trace id quoted off a screen leads
        // straight to the audited action.
        assertThat(row.get("trace_id"))
                .as("trace id on the row matches the one the response carried")
                .isEqualTo(response.getHeader(RequestId.HEADER));
    }

    // ── Lockout ──────────────────────────────────────────────────────────────────────────────

    /**
     * The lockout is recorded once, when the lock is applied — not again on each attempt that the
     * lock then refuses. An audit log that counts wrong is one nobody trusts.
     */
    @Test
    void lockingAnAccountIsRecordedOnceAtTheMomentItLocks() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(login(USERNAME, "wrong-" + attempt)).andExpect(status().isUnauthorized());
        }

        Map<String, Object> lock = onlyRow(AuditAction.ACCOUNT_LOCKED);
        assertThat(lock.get("outcome")).isEqualTo("FAILURE");
        assertThat(lock.get("entity_type")).isEqualTo("USER_ACCOUNT");
        assertThat(lock.get("entity_id")).isEqualTo(accountId.toString());
        assertThat(countOf(AuditAction.LOGIN_FAILED)).isEqualTo(5);

        // A sixth attempt is refused by the lock, and is another failed attempt — not a second
        // lockout.
        mockMvc.perform(login(USERNAME, PASSWORD)).andExpect(status().isUnauthorized());
        assertThat(countOf(AuditAction.ACCOUNT_LOCKED)).isEqualTo(1);
        assertThat(countOf(AuditAction.LOGIN_FAILED)).isEqualTo(6);
    }

    // ── Sign-out and password change ─────────────────────────────────────────────────────────

    @Test
    void signingOutIsRecordedAgainstTheAccountThatWasSignedIn() throws Exception {
        Cookie session = signIn();

        mockMvc.perform(post("/api/auth/logout").cookie(session).with(csrf())).andExpect(status().isOk());

        Map<String, Object> row = onlyRow(AuditAction.LOGOUT);
        assertThat(row.get("actor_id")).isEqualTo(accountId);
        assertThat(row.get("entity_id")).isEqualTo(accountId.toString());
    }

    /** Signing out with no session has no principal and no school, so there is nothing to record. */
    @Test
    void signingOutWithoutASessionRecordsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isOk());

        assertThat(countOf(AuditAction.LOGOUT)).isZero();
    }

    /**
     * The action is recorded. The secret is not — neither the old one, nor the new one, nor either
     * hash, nor the session id. There is no parameter on {@code AuditService} that would take one.
     */
    @Test
    void changingAPasswordIsRecordedAndTheRowHoldsNoSecret() throws Exception {
        Cookie session = signIn();

        mockMvc.perform(post("/api/auth/password")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk());

        Map<String, Object> row = onlyRow(AuditAction.PASSWORD_CHANGED);
        assertThat(row.get("actor_id")).isEqualTo(accountId);
        assertThat(row.get("entity_type")).isEqualTo("USER_ACCOUNT");

        String everythingOnEveryRow = jdbc.sql("select coalesce(string_agg(concat_ws('|', actor_name, actor_roles,"
                        + " action, entity_type, entity_id, changed_fields, ip_address, user_agent, trace_id), '~'),"
                        + " '') from " + OAKRIDGE_SCHEMA + ".audit_event")
                .query(String.class)
                .single();
        assertThat(everythingOnEveryRow)
                .doesNotContain(PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(storedSecret())
                .doesNotContain(session.getValue());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private RequestBuilder login(String username, String password) {
        return login(OAKRIDGE_CODE, username, password);
    }

    private RequestBuilder login(String schoolCode, String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"schoolCode": "%s", "username": "%s", "password": "%s"}
                        """.formatted(
                        schoolCode, username, password));
    }

    private Cookie signIn() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(USERNAME, PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        // The sign-in itself is audited; clear it so each test asserts about its own action.
        jdbc.sql("delete from " + OAKRIDGE_SCHEMA + ".audit_event").update();
        return session;
    }

    private UUID createAccount(String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into " + OAKRIDGE_SCHEMA
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', false, 0)")
                .params(id, displayName)
                .update();
        jdbc.sql("insert into " + OAKRIDGE_SCHEMA + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), id, USERNAME)
                .update();
        jdbc.sql("insert into " + OAKRIDGE_SCHEMA + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), id, passwordEncoder.encode(PASSWORD))
                .update();
        return id;
    }

    /** So the actor snapshot has roles to record. The auditor is the role that holds audit:read. */
    private void grantAuditorRole(UUID account) {
        UUID roleId = jdbc.sql("select id from " + OAKRIDGE_SCHEMA + ".role where code = 'AUDITOR'")
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + OAKRIDGE_SCHEMA
                        + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), account, roleId)
                .update();
    }

    private Map<String, Object> onlyRow(String action) {
        List<Map<String, Object>> rows = jdbc.sql(
                        "select * from " + OAKRIDGE_SCHEMA + ".audit_event where action = ? order by occurred_at")
                .param(action)
                .query()
                .listOfRows();
        assertThat(rows).as("exactly one %s row", action).hasSize(1);
        return rows.getFirst();
    }

    /** The stored hash of the new password, so the assertion is about this run and not a prefix. */
    private String storedSecret() {
        return jdbc.sql("select secret from " + OAKRIDGE_SCHEMA + ".user_credential")
                .query(String.class)
                .single();
    }

    private int countOf(String action) {
        return jdbc.sql("select count(*) from " + OAKRIDGE_SCHEMA + ".audit_event where action = ?")
                .param(action)
                .query(Integer.class)
                .single();
    }

    private void reset() {
        provisioning.provision(OAKRIDGE_SCHEMA);
        jdbc.sql("delete from " + OAKRIDGE_SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + OAKRIDGE_SCHEMA + ".user_account").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
