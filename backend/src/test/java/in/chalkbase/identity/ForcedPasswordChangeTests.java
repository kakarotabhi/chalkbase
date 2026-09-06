package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A school-issued password may be replaced. It may not be used.
 *
 * <p>The flag was advice until this suite existed: the login response carried it, the Angular app
 * redirected on it, and typing any other address escaped the redirect. {@code GET /api/students}
 * with such a session returned 200 and sixty children's names. That is the shape of a temporary
 * password's whole threat model — it is read out on the phone and written on slips precisely
 * because it is supposed to be worthless for anything but replacing itself.
 *
 * <p>The fixture is two accounts at one school holding the <em>same</em> permissions, differing
 * only in whether they owe a password change. That is what makes the refusal attributable to the
 * flag rather than to a missing grant: {@link #leavesAnAccountThatOwesNothingAlone()} reads the same
 * list the refused account is refused, with the same role, over the same endpoint.
 *
 * <p>Deliberately NOT {@code @Transactional}. The lifting test turns on the password change being
 * committed and then read back by a filter on a later request, which a rolled-back transaction
 * would never show.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ForcedPasswordChangeTests {

    private static final String SCHEMA = "brookfield";
    private static final String CODE = "BRK-707";
    private static final String NAME = "Brookfield High School";

    /** Handed out by the office, so it is the one both accounts sign in with. */
    private static final String ISSUED_PASSWORD = "Brookfield#2026";

    private static final String NEW_PASSWORD = "Kingfisher#77";

    /** The account still on the password its school issued it. */
    private static final String OWING = "newjoiner";

    /** The same role, the same school, the same permissions — but nothing outstanding. */
    private static final String SETTLED = "established";

    private static final String FORCED_CHANGE = "AUTH_008";

    private static final String A_STUDENT = """
            {
              "admissionNumber": "2026-9001",
              "fullName": "Test Fixture Child",
              "dateOfBirth": "2015-04-11",
              "gender": "FEMALE",
              "status": "ACTIVE"
            }
            """;

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
    void createOneSchoolWithTwoPrincipals() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, NAME, SCHEMA, Board.CBSE, "Kochi", "Kerala"));
        createAccount(OWING, "Arun Shetty", true);
        createAccount(SETTLED, "Ravi Deshpande", false);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Refused ──────────────────────────────────────────────────────────────────────────────

    /**
     * The defect, as a test. The session is genuine, the role grants
     * {@code student:student:read}, and the answer is still no.
     */
    @Test
    void refusesToListStudentsWhileTheIssuedPasswordStands() throws Exception {
        mockMvc.perform(get("/api/students").cookie(signIn(OWING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(FORCED_CHANGE))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /** Reading is the headline; writing with a credential read out over the phone is worse. */
    @Test
    void refusesAWriteWhileTheIssuedPasswordStands() throws Exception {
        mockMvc.perform(post("/api/students")
                        .cookie(signIn(OWING))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(A_STUDENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(FORCED_CHANGE));

        assertThat(studentCount()).as("nothing was written").isZero();
    }

    /**
     * {@code /api/schools/**} is {@code permitAll} so a school can be onboarded before any account
     * exists inside it. That exemption is for callers with no principal; it is not a hole a
     * signed-in session that owes a password change gets to climb through.
     */
    @Test
    void refusesEvenAnEndpointThatIsOtherwisePublic() throws Exception {
        mockMvc.perform(get("/api/schools").cookie(signIn(OWING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(FORCED_CHANGE));
    }

    /**
     * The message says what is wrong, unlike the {@code SetupKeyFilter} 404 and unlike a bare
     * {@code PERM_001}. The caller proved they hold this account's credential and the account is
     * their own; telling them to change the password is the entire point of the flag, and a client
     * that could not tell this apart from "ask your school for this permission" would show the
     * wrong screen.
     */
    @Test
    void saysWhatIsWrongRatherThanHidingIt() throws Exception {
        mockMvc.perform(get("/api/students").cookie(signIn(OWING)))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("new password")))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── Allowed ──────────────────────────────────────────────────────────────────────────────

    /**
     * The bootstrap call, or the client has no way to discover that it must redirect — a blank
     * screen with no explanation is not an improvement on the hole.
     */
    @Test
    void allowsTheBootstrapCallSoTheClientCanLearnWhyItIsStuck() throws Exception {
        mockMvc.perform(get("/api/me").cookie(signIn(OWING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.user.displayName").value("Arun Shetty"));
    }

    /** Refusing this one would make the restriction permanent. */
    @Test
    void allowsThePasswordChangeItself() throws Exception {
        mockMvc.perform(changePassword(signIn(OWING), ISSUED_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /** Walking away always works. */
    @Test
    void allowsSigningOut() throws Exception {
        mockMvc.perform(post("/api/auth/logout").cookie(signIn(OWING)).with(csrf()))
                .andExpect(status().isOk());
    }

    /**
     * A browser that reloads loses the temporary password it was holding in memory and has to sign
     * in again. It still carries the old session cookie when it posts the login, so the security
     * context is restored before the filter runs — refusing it would strand exactly the user this
     * change is for.
     */
    @Test
    void allowsSigningInAgainWhileHoldingASessionThatOwesAChange() throws Exception {
        mockMvc.perform(login(OWING, ISSUED_PASSWORD).cookie(signIn(OWING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    // ── Lifted ───────────────────────────────────────────────────────────────────────────────

    /**
     * The test that decides where the flag is read from. The same cookie is refused, then allowed,
     * with no second sign-in in between — which only holds if the filter asks the account row
     * rather than a copy taken at login. A snapshot on the principal would still be refusing here,
     * and the user would have to sign out and back in to use the password they just set.
     */
    @Test
    void liftsTheRestrictionOnTheSameSessionAsSoonAsThePasswordIsChanged() throws Exception {
        Cookie session = signIn(OWING);

        mockMvc.perform(get("/api/students").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(FORCED_CHANGE));

        mockMvc.perform(changePassword(session, ISSUED_PASSWORD, NEW_PASSWORD)).andExpect(status().isOk());

        mockMvc.perform(get("/api/students").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(post("/api/students")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(A_STUDENT))
                .andExpect(status().isCreated());
    }

    // ── Unaffected ───────────────────────────────────────────────────────────────────────────

    /**
     * The control. Same school, same role, same endpoints — and no restriction, because this
     * account does not owe a password change. Without this the suite above would pass just as well
     * if the filter refused everybody.
     */
    @Test
    void leavesAnAccountThatOwesNothingAlone() throws Exception {
        Cookie session = signIn(SETTLED);

        mockMvc.perform(get("/api/students").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false));
        mockMvc.perform(post("/api/students")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(A_STUDENT))
                .andExpect(status().isCreated());
    }

    /** No session at all is still 401, not the forced-change 403. The two mean different things. */
    @Test
    void stillAnswers401WhenThereIsNoSessionAtAll() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                """.formatted(
                        CODE, username, password));
    }

    private MockHttpServletRequestBuilder changePassword(Cookie session, String currentPassword, String newPassword) {
        return post("/api/auth/password")
                .cookie(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword));
    }

    private Cookie signIn(String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(username, ISSUED_PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    /** A principal, so the account genuinely holds the student permissions it is refused with. */
    private void createAccount(String username, String displayName, boolean mustChangePassword) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + SCHEMA
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', ?, 0)")
                .params(accountId, displayName, mustChangePassword)
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, username)
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(ISSUED_PASSWORD))
                .update();
        jdbc.sql("insert into " + SCHEMA
                        + ".user_role_grant (id, user_account_id, role_id, scope_type, scope_id, valid_from, valid_to)"
                        + " values (?, ?, ?, 'SCHOOL', null, null, null)")
                .params(UUID.randomUUID(), accountId, roleId())
                .update();
    }

    private UUID roleId() {
        return jdbc.sql("select id from " + SCHEMA + ".role where code = 'PRINCIPAL'")
                .query(UUID.class)
                .single();
    }

    private long studentCount() {
        return jdbc.sql("select count(*) from " + SCHEMA + ".student")
                .query(Long.class)
                .single();
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".student_guardian").update();
        jdbc.sql("delete from " + SCHEMA + ".student_enrolment").update();
        jdbc.sql("delete from " + SCHEMA + ".student").update();
        // Written by the sign-ins and password changes these tests perform, and by nothing else.
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        // user_account cascades to its grants.
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
