package in.chalkbase.platform.reference;

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

/**
 * {@code GET /api/reference/states} and {@code GET /api/schools/boards} (ADR-0029).
 *
 * <p>One school, and one caller holding no permission at all: both endpoints require nothing beyond
 * a session, so the interesting assertions are that a session is enough, that no session is not, and
 * that the content itself is what {@link IndianStates} and {@link Board} say it should be — not
 * which role is signed in, which the dashboard and audit tests already cover for this style of
 * fixture.
 *
 * <p>Every person and school in this file is invented (AGENTS rule 9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReferenceDataApiTests {

    private static final String SCHEMA = "referenceschool";
    private static final String CODE = "REF-707";
    private static final String USERNAME = "librarian-" + SCHEMA;
    private static final String PASSWORD = "Reference#2026";

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
    void onboardSchool() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, "Reference Test School", SCHEMA, Board.CBSE, "Kochi", "Kerala"));
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    @Test
    void statesAreServedAlphabeticallyToAnySignedInCaller() throws Exception {
        // LIBRARIAN holds no permission this screen would need (RoleTemplates) — the point is that
        // neither endpoint asks for one.
        Cookie session = signInAsLibrarian();

        mockMvc.perform(get("/api/reference/states").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(36))
                .andExpect(jsonPath("$.data[0].value").value("Andaman and Nicobar Islands"))
                .andExpect(jsonPath("$.data[0].label").value("Andaman and Nicobar Islands"))
                .andExpect(jsonPath("$.data[?(@.value=='Maharashtra')]").exists())
                .andExpect(jsonPath("$.data[35].value").value("West Bengal"));
    }

    @Test
    void statesRequireASessionButNoPermission() throws Exception {
        mockMvc.perform(get("/api/reference/states")).andExpect(status().isUnauthorized());
    }

    @Test
    void boardsAreServedWithSchoolFacingLabels() throws Exception {
        Cookie session = signInAsLibrarian();

        mockMvc.perform(get("/api/schools/boards").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(Board.values().length))
                .andExpect(jsonPath("$.data[0].value").value("CBSE"))
                .andExpect(jsonPath("$.data[0].label").value("CBSE"))
                .andExpect(jsonPath("$.data[1].value").value("CISCE"))
                .andExpect(jsonPath("$.data[1].label").value("CISCE (ICSE / ISC)"));
    }

    /**
     * Proves the method-level {@code isAuthenticated()} is doing real work, not the URL-level
     * {@code permitAll()} on {@code /api/schools/**} that {@code SchoolController}'s Javadoc warns
     * about: an anonymous caller must still be refused.
     */
    @Test
    void boardsRefuseAnAnonymousCallerDespiteTheOpenUrlPrefix() throws Exception {
        mockMvc.perform(get("/api/schools/boards")).andExpect(status().isUnauthorized());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private Cookie signInAsLibrarian() throws Exception {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + SCHEMA
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', false, 0)")
                .params(accountId, "Nandini Pillai")
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, USERNAME)
                .update();
        jdbc.sql("insert into " + SCHEMA + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(PASSWORD))
                .update();
        UUID roleId = jdbc.sql("select id from " + SCHEMA + ".role where code = 'LIBRARIAN'")
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + SCHEMA + ".user_role_grant (id, user_account_id, role_id, scope_type)"
                        + " values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), accountId, roleId)
                .update();

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(CODE, USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        return response.getCookie("SESSION");
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        for (String table : List.of("user_role_grant", "user_credential", "user_identifier", "user_account")) {
            jdbc.sql("delete from " + SCHEMA + "." + table).update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
