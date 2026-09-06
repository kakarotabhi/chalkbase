package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Login, lockout and password change, against two real tenant schemas.
 *
 * <p>Deliberately NOT {@code @Transactional}. Lockout only works if the failure counter is actually
 * committed, so a rolled-back test would report a lockout that production never reaches. These
 * tests commit and clean up after themselves instead.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

    private static final String RIVERDALE_SCHEMA = "riverdale";
    private static final String RIVERDALE_CODE = "RVD-101";
    private static final String LAKEVIEW_SCHEMA = "lakeview";
    private static final String LAKEVIEW_CODE = "LKV-202";

    /** The same username at both schools — an admission number is only unique inside one school. */
    private static final String USERNAME = "2026-0412";

    private static final String RIVERDALE_PASSWORD = "Riverdale#2026";
    private static final String LAKEVIEW_PASSWORD = "Lakeview#2026";
    private static final String NEW_PASSWORD = "Kingfisher#77";

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
    void createTwoSchoolsEachWithTheSameUsername() {
        reset();
        registerSchool(RIVERDALE_CODE, "Riverdale Public School", RIVERDALE_SCHEMA);
        registerSchool(LAKEVIEW_CODE, "Lakeview Academy", LAKEVIEW_SCHEMA);
        createAccount(RIVERDALE_SCHEMA, USERNAME, RIVERDALE_PASSWORD, "Ananya Rao", true);
        createAccount(LAKEVIEW_SCHEMA, USERNAME, LAKEVIEW_PASSWORD, "Devika Nair", false);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Login ────────────────────────────────────────────────────────────────────────────────

    @Test
    void signsInAndReportsThatTheIssuedPasswordMustBeChanged() throws Exception {
        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.displayName").value("Ananya Rao"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.school.code").value(RIVERDALE_CODE))
                .andExpect(jsonPath("$.data.school.name").value("Riverdale Public School"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** The response body must give away nothing about the session itself. */
    @Test
    void neverPutsTheSessionIdInTheBody() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie").isNotNull();
        assertThat(session.isHttpOnly()).as("session cookie is HttpOnly").isTrue();
        assertThat(response.getContentAsString()).doesNotContain(session.getValue());
    }

    /**
     * The test that keeps the login form from becoming a directory of who is enrolled: a wrong
     * password and a username that does not exist must be indistinguishable.
     */
    @Test
    void aWrongPasswordAndAnUnknownUsernameLookIdentical() throws Exception {
        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"))
                .andExpect(jsonPath("$.error.message").value("Invalid username or password"));

        mockMvc.perform(login(RIVERDALE_CODE, "9999-0000", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"))
                .andExpect(jsonPath("$.error.message").value("Invalid username or password"));
    }

    /**
     * A stored secret that carries no algorithm prefix is a failed sign-in, not a server error.
     *
     * <p>Found by running against the dev database with a hash inserted by hand — the way it would
     * first happen in production, through an import or a half-finished credential migration.
     * {@code DelegatingPasswordEncoder.matches} throws for an unmapped prefix, and before this was
     * caught the caller got a 500, the attempt was never audited, and it never counted toward the
     * lockout: an account with a corrupt hash could be guessed against forever, in silence.
     */
    @Test
    void treatsAnUnreadableStoredSecretAsAFailedSignInRatherThanAServerError() throws Exception {
        jdbc.sql("update " + RIVERDALE_SCHEMA
                        + ".user_credential set secret = '$2b$10$notaprefixedhashatallxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'"
                        + " where user_account_id = (select user_account_id from " + RIVERDALE_SCHEMA
                        + ".user_identifier where value = ?)")
                .param(USERNAME)
                .update();

        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));

        // It counted, which is the half that matters: the attempt is on the record and the account
        // is on its way to being locked rather than being guessed against indefinitely.
        assertThat(jdbc.sql("select failed_attempts from " + RIVERDALE_SCHEMA
                                + ".user_account where id = (select user_account_id from " + RIVERDALE_SCHEMA
                                + ".user_identifier where value = ?)")
                        .param(USERNAME)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);

        assertThat(jdbc.sql("select count(*) from " + RIVERDALE_SCHEMA + ".audit_event where action = 'LOGIN_FAILED'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void reportsAnUnknownSchoolCodeAsAuth005() throws Exception {
        mockMvc.perform(login("NO-SUCH-SCHOOL", USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_005"))
                .andExpect(jsonPath("$.error.message").value("No school with that code"));
    }

    @Test
    void rejectsAMissingFieldBeforeItReachesTheDatabase() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolCode\": \"\", \"username\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.schoolCode").exists())
                .andExpect(jsonPath("$.error.details.username").exists())
                .andExpect(jsonPath("$.error.details.password").exists());
    }

    // ── Lockout ──────────────────────────────────────────────────────────────────────────────

    @Test
    void locksTheAccountAfterFiveFailuresAndSaysSoOnTheSixthAttempt() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(login(RIVERDALE_CODE, USERNAME, "wrong-" + attempt))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        }

        // Even the correct password does not get in now — that is what a lockout means.
        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"))
                .andExpect(jsonPath("$.error.message")
                        .value("This account is locked. Ask your school office to unlock it."));

        assertThat(failedAttempts(RIVERDALE_SCHEMA)).isEqualTo(5);
    }

    /** The lock belongs to one school's account, not to the username. */
    @Test
    void lockingOneSchoolsAccountLeavesTheOtherSchoolAlone() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(login(RIVERDALE_CODE, USERNAME, "wrong-" + attempt)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(LAKEVIEW_CODE, USERNAME, LAKEVIEW_PASSWORD)).andExpect(status().isOk());
        assertThat(failedAttempts(LAKEVIEW_SCHEMA)).isZero();
    }

    // ── Per-tenant identity ──────────────────────────────────────────────────────────────────

    /**
     * The test that proves identity is per tenant. Two unrelated schools each have a user called
     * {@code 2026-0412}; signing in to one must not authenticate the other, and the credential of
     * one must be worthless at the other.
     */
    @Test
    void twoSchoolsMayShareAUsernameWithoutSharingAnAccount() throws Exception {
        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ananya Rao"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        mockMvc.perform(login(LAKEVIEW_CODE, USERNAME, LAKEVIEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Devika Nair"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        // Riverdale's password is not Lakeview's password, even for the same username.
        mockMvc.perform(login(LAKEVIEW_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, LAKEVIEW_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    // ── Password change ──────────────────────────────────────────────────────────────────────

    @Test
    void changingThePasswordClearsTheForcedChangeFlagAndTakesEffect() throws Exception {
        Cookie session = signIn(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD);

        mockMvc.perform(changePassword(session, RIVERDALE_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        mockMvc.perform(login(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void refusesAPasswordChangeWhenTheCurrentPasswordIsWrong() throws Exception {
        Cookie session = signIn(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD);

        mockMvc.perform(changePassword(session, "not-the-password", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_006"));
    }

    @Test
    void refusesANewPasswordThatDoesNotMeetTheRules() throws Exception {
        Cookie session = signIn(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD);

        // Long enough, but no digit and no symbol.
        mockMvc.perform(changePassword(session, RIVERDALE_PASSWORD, "kingfishers"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_007"));

        // A digit and a symbol, but only nine characters.
        mockMvc.perform(changePassword(session, RIVERDALE_PASSWORD, "King#f7ir"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_007"));
    }

    // ── Session lifecycle ────────────────────────────────────────────────────────────────────

    @Test
    void refusesAnAuthenticatedEndpointWithoutASession() throws Exception {
        mockMvc.perform(post("/api/auth/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(RIVERDALE_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void signingOutEndsTheSessionOnTheServer() throws Exception {
        Cookie session = signIn(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD);

        mockMvc.perform(post("/api/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(changePassword(session, RIVERDALE_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    /** A cookie-session API without CSRF protection is a cookie-session API with a hole in it. */
    @Test
    void rejectsAStateChangingCallWithoutACsrfToken() throws Exception {
        Cookie session = signIn(RIVERDALE_CODE, USERNAME, RIVERDALE_PASSWORD);

        mockMvc.perform(post("/api/auth/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(RIVERDALE_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.RequestBuilder login(String code, String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"schoolCode": "%s", "username": "%s", "password": "%s"}
                        """.formatted(
                        code, username, password));
    }

    private org.springframework.test.web.servlet.RequestBuilder changePassword(
            Cookie session, String current, String replacement) {
        return post("/api/auth/password")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordBody(current, replacement));
    }

    private String passwordBody(String current, String replacement) {
        return """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, replacement);
    }

    private Cookie signIn(String code, String username, String password) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(code, username, password))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void registerSchool(String code, String name, String schema) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, "Dehradun", "Uttarakhand"));
    }

    private void createAccount(
            String schema, String username, String password, String displayName, boolean mustChangePassword) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', ?, 0)")
                .params(accountId, displayName, mustChangePassword)
                .update();
        jdbc.sql("insert into " + schema + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, username)
                .update();
        jdbc.sql("insert into " + schema + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(password))
                .update();
    }

    private int failedAttempts(String schema) {
        return jdbc.sql("select failed_attempts from " + schema + ".user_account")
                .query(Integer.class)
                .single();
    }

    private void reset() {
        provisioning.provision(RIVERDALE_SCHEMA);
        provisioning.provision(LAKEVIEW_SCHEMA);
        jdbc.sql("delete from " + RIVERDALE_SCHEMA + ".user_account").update();
        jdbc.sql("delete from " + LAKEVIEW_SCHEMA + ".user_account").update();
        // Cleared too. Audit rows are not deleted by anything the application does — that is the
        // point of them — so without this every test in this class inherits the sign-ins of the
        // ones before it, and any assertion on a count is answered by an accumulated total.
        jdbc.sql("delete from " + RIVERDALE_SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + LAKEVIEW_SCHEMA + ".audit_event").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
