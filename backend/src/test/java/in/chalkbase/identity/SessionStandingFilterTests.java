package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ADR-0023: a session survives its account's own {@code status} and {@code locked_until} exactly as
 * long as it takes the account to make one more request.
 *
 * <p>The fixture mirrors {@code ForcedPasswordChangeTests}: two accounts, same role, same school,
 * differing only in the one thing under test — here, account standing rather than the forced-change
 * flag — so a refusal is attributable to the standing check and not to a missing grant.
 *
 * <p>Deliberately NOT {@code @Transactional}: the whole suite turns on a status change made on one
 * connection being visible to a filter reading it back on another request, which a rolled-back
 * transaction would never show.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionStandingFilterTests {

    private static final String SCHEMA = "sunrise";
    private static final String CODE = "SUN-808";
    private static final String NAME = "Sunrise Public School";
    private static final String PASSWORD = "Sunrise#2026";

    private static final String STILL_ACTIVE = "sneha";
    private static final String TO_BE_DISABLED = "manoj";
    private static final String TO_BE_LOCKED = "kavita";

    private static final String NOT_AUTHENTICATED = "AUTH_002";

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

    private UUID disabledAccountId;
    private UUID lockedAccountId;

    @BeforeEach
    void createOneSchoolWithThreeAccounts() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, NAME, SCHEMA, Board.CBSE, "Pune", "Maharashtra"));
        createAccount(STILL_ACTIVE, "Sneha Rane");
        disabledAccountId = createAccount(TO_BE_DISABLED, "Manoj Kulkarni");
        lockedAccountId = createAccount(TO_BE_LOCKED, "Kavita Bhosale");
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Disabled mid-session ─────────────────────────────────────────────────────────────────

    @Test
    void aSessionStopsWorkingAsSoonAsItsAccountIsDisabled() throws Exception {
        Cookie session = signIn(TO_BE_DISABLED);
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());

        disable(disabledAccountId);

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(NOT_AUTHENTICATED));
    }

    /** The session is not merely refused once — it is gone, so a retry gets the ordinary no-session 401. */
    @Test
    void theSessionIsActuallyInvalidatedNotJustRefused() throws Exception {
        Cookie session = signIn(TO_BE_DISABLED);
        disable(disabledAccountId);

        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        // A second use of the same cookie behaves exactly as a cookie for a session that never
        // existed: there is nothing left in `spring_session` for it to be resolved against.
        assertThat(liveSessionCount()).isZero();
    }

    @Test
    void disablingIsRecordedOnceAsASessionRevoked() throws Exception {
        Cookie session = signIn(TO_BE_DISABLED);
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        disable(disabledAccountId);

        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        // Retrying the same dead cookie must not double-count: there is no session left to discover
        // the problem on a second time.
        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyRow(AuditAction.SESSION_REVOKED);
        assertThat(row.get("outcome")).isEqualTo("DENIED");
        assertThat(row.get("entity_type")).isEqualTo("USER_ACCOUNT");
        assertThat(row.get("entity_id")).isEqualTo(disabledAccountId.toString());
        assertThat(row.get("actor_id")).isEqualTo(disabledAccountId);
    }

    // ── Locked mid-session ───────────────────────────────────────────────────────────────────

    @Test
    void aSessionStopsWorkingWhileItsAccountIsLocked() throws Exception {
        Cookie session = signIn(TO_BE_LOCKED);
        lock(lockedAccountId, Instant.now().plus(15, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(NOT_AUTHENTICATED));
    }

    /** A lock that has already expired is not a lock. Re-validation reads the same clock isLocked() does. */
    @Test
    void aLockThatHasAlreadyExpiredDoesNotEndTheSession() throws Exception {
        Cookie session = signIn(TO_BE_LOCKED);
        lock(lockedAccountId, Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
    }

    // ── Unaffected ───────────────────────────────────────────────────────────────────────────

    /** The control: nothing here changes for an account nobody touched. */
    @Test
    void leavesAnUnaffectedAccountAlone() throws Exception {
        Cookie session = signIn(STILL_ACTIVE);

        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").value("Sneha Rane"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder login(String username) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schoolCode": "%s", "username": "%s", "password": "%s"}
                        """.formatted(CODE, username, PASSWORD));
    }

    private Cookie signIn(String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(username))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private UUID createAccount(String username, String displayName) {
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
        jdbc.sql("insert into " + SCHEMA
                        + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), accountId, principalRoleId())
                .update();
        return accountId;
    }

    private UUID principalRoleId() {
        return jdbc.sql("select id from " + SCHEMA + ".role where code = 'PRINCIPAL'")
                .query(UUID.class)
                .single();
    }

    private void disable(UUID accountId) {
        jdbc.sql("update " + SCHEMA + ".user_account set status = 'DISABLED' where id = ?")
                .params(accountId)
                .update();
    }

    private void lock(UUID accountId, Instant until) {
        // The PostgreSQL driver cannot infer a SQL type for a bare java.time.Instant; Timestamp
        // is what it maps to timestamptz through.
        jdbc.sql("update " + SCHEMA + ".user_account set locked_until = ? where id = ?")
                .params(java.sql.Timestamp.from(until), accountId)
                .update();
    }

    private long liveSessionCount() {
        return jdbc.sql("select count(*) from public.spring_session")
                .query(Long.class)
                .single();
    }

    private Map<String, Object> onlyRow(String action) {
        List<Map<String, Object>> rows = jdbc.sql(
                        "select * from " + SCHEMA + ".audit_event where action = ? order by occurred_at")
                .param(action)
                .query()
                .listOfRows();
        assertThat(rows).as("exactly one %s row", action).hasSize(1);
        return rows.getFirst();
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
