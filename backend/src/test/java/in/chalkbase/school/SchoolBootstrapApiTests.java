package in.chalkbase.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.infrastructure.SchoolRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * The acceptance test ADR-0024 asks for: after a fresh deploy against an empty database, a person
 * with the deployment's secrets can create a school and sign in to it, over HTTP, with no developer
 * tooling and no direct database access.
 *
 * <p>{@link #bootstrapsASchoolAndSignsInAsTheGeneratedAdministrator} is that sentence made
 * executable — it is the only test in this class that matters if you are deciding whether the
 * feature works at all. The rest guard the two ways this endpoint could quietly become a backdoor:
 * running twice for one school, or disagreeing with itself about which schema a code belongs to.
 *
 * <p>Deliberately NOT {@code @Transactional}, matching {@code SchoolApiTests} and
 * {@code AuthApiTests}: the refusal on a second bootstrap is only meaningful if the first one's
 * writes actually reached the database, and a rolled-back test would report a refusal production
 * would not see. These tests commit and clean up after themselves instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SchoolBootstrapApiTests {

    private static final String CODE = "BOOT-001";
    private static final String SCHEMA = "bootstrap_school";
    private static final String NAME = "Bootstrap Test School";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BOOTSTRAP_BODY = """
            {
              "code": "%s",
              "name": "%s",
              "schemaName": "%s",
              "board": "CBSE",
              "city": "Pune",
              "state": "Maharashtra",
              "adminUsername": "admin",
              "adminDisplayName": "First Administrator"
            }
            """.formatted(CODE, NAME, SCHEMA);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    SchoolRepository schools;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clean() {
        reset();
    }

    @AfterEach
    void cleanUp() {
        reset();
    }

    /**
     * The whole point. No {@code csrf()} post-processor, deliberately: the real caller is an
     * operator's script, not a browser with a cookie to echo (ADR-0024, and the CSRF exemption
     * {@code SecurityConfig} documents for this exact path). No signed-in user either — there is
     * nobody to sign in as until this call finishes.
     */
    @Test
    void bootstrapsASchoolAndSignsInAsTheGeneratedAdministrator() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.school.code").value(CODE))
                .andExpect(jsonPath("$.data.school.schemaName").value(SCHEMA))
                .andExpect(jsonPath("$.data.adminAccountId").exists())
                .andExpect(jsonPath("$.data.adminUsername").value("admin"))
                .andExpect(jsonPath("$.data.adminTemporaryPassword").exists())
                .andExpect(jsonPath("$.data.adminMustChangePassword").value(true))
                .andReturn()
                .getResponse();

        String temporaryPassword = JSON.readTree(response.getContentAsString())
                .path("data")
                .path("adminTemporaryPassword")
                .asText();

        // Over HTTP, as the generated administrator, with the password this endpoint just handed
        // back — no seeder, no JDBC, no shortcut.
        String loginBody = """
                {"schoolCode": "%s", "username": "admin", "password": "%s"}
                """.formatted(CODE, temporaryPassword);
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.permissions").isArray())
                // PRINCIPAL, the template this endpoint grants — proof this is a real, working
                // account and not merely a row that exists.
                .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.hasItem("identity:user:manage")));
    }

    @Test
    void refusesASecondBootstrapForASchoolThatAlreadyHasAnAdministrator() throws Exception {
        mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isCreated());

        String secondAdmin = """
                {
                  "code": "%s",
                  "name": "%s",
                  "schemaName": "%s",
                  "board": "CBSE",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "adminUsername": "second-admin",
                  "adminDisplayName": "Somebody Else"
                }
                """.formatted(CODE, NAME, SCHEMA);

        mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_014"));

        // The refusal did not touch what the first call created.
        assertThat(jdbc.sql("select count(*) from " + SCHEMA + ".user_account")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void refusesWhenTheCodeIsAlreadyRegisteredUnderADifferentSchema() throws Exception {
        mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isCreated());

        String differentSchema = """
                {
                  "code": "%s",
                  "name": "%s",
                  "schemaName": "bootstrap_school_other",
                  "board": "CBSE",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "adminUsername": "someone",
                  "adminDisplayName": "Someone"
                }
                """.formatted(CODE, NAME);

        mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentSchema))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCHOOL_007"));
    }

    @Test
    void reportsFieldLevelValidationFailures() throws Exception {
        mockMvc.perform(post("/api/schools/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"\", \"name\": \"\", \"schemaName\": \"\", \"board\": \"CBSE\","
                                + " \"adminUsername\": \"\", \"adminDisplayName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.adminUsername").exists());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".user_role_grant").update();
        jdbc.sql("delete from " + SCHEMA + ".user_credential").update();
        jdbc.sql("delete from " + SCHEMA + ".user_identifier").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
