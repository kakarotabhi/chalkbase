package in.chalkbase.platform.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.school.infrastructure.SchoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The request half of the setup key, with the filter in the real chain.
 *
 * <p>The {@code prod} profile is activated alongside {@code test} because that profile <em>is</em>
 * the feature: {@link SetupKeyConfiguration} contributes nothing without it. The datasource
 * properties below exist only so {@code application-prod.yml}'s {@code ${SPRING_DATASOURCE_URL}}
 * placeholders resolve — the connection actually used is the Testcontainers one, which
 * {@code @ServiceConnection} supplies as a {@code JdbcConnectionDetails} bean that takes precedence
 * over any property. Nothing here reaches Supabase.
 *
 * <p>{@code SchoolApiTests} covers the same endpoints on the {@code test} profile alone and needs
 * no header at all. That it was not modified by this change is the evidence that the profile
 * condition is right; if it ever has to be, the condition is wrong.
 */
@SpringBootTest(
        properties = {
            "SPRING_DATASOURCE_URL=jdbc:postgresql://overridden-by-testcontainers/chalkbase",
            "SPRING_DATASOURCE_USERNAME=unused",
            "SPRING_DATASOURCE_PASSWORD=unused",
            "chalkbase.setup-key=" + SetupKeyFilterTests.KEY
        })
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
class SetupKeyFilterTests {

    static final String KEY = "correct-horse-battery-staple";

    // Same shape as SchoolApiTests, with its own code and schema so the two cannot collide if they
    // ever run against the same database.
    private static final String SCHOOL = """
            {
              "code": "SKT-S01",
              "name": "Setup Key Test School",
              "schemaName": "setupkeytest",
              "board": "CBSE",
              "city": "Pune",
              "state": "Maharashtra"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SchoolRepository schools;

    @AfterEach
    void clearSchools() {
        schools.deleteAll();
    }

    /**
     * The header is missing entirely, and the answer is 404 rather than 401.
     *
     * <p>The body is asserted as well as the status: {@code NF_002} is what this application says
     * for any address it does not serve, so a scanner learns the same from {@code /api/schools} as
     * from a path that was never mapped. A 401 would have told them the endpoint exists and that a
     * credential is all they are missing.
     */
    @Test
    void hidesOnboardingFromARequestWithNoKey() throws Exception {
        mockMvc.perform(post("/api/schools")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHOOL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NF_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void hidesOnboardingFromARequestWithTheWrongKey() throws Exception {
        mockMvc.perform(post("/api/schools")
                        .with(csrf())
                        .header(SetupKeyFilter.HEADER, "not-the-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHOOL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_002"));
    }

    /** A prefix of the real key must not pass — the comparison is over the whole value. */
    @Test
    void hidesOnboardingFromARequestWithAPrefixOfTheKey() throws Exception {
        mockMvc.perform(get("/api/schools").header(SetupKeyFilter.HEADER, KEY.substring(0, KEY.length() - 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_002"));
    }

    /** Reads are hidden too: listing the register leaks which schools exist and under what codes. */
    @Test
    void hidesTheRegisterFromAReadWithNoKey() throws Exception {
        mockMvc.perform(get("/api/schools"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_002"));
    }

    @Test
    void onboardsASchoolWhenTheKeyIsCorrect() throws Exception {
        mockMvc.perform(post("/api/schools")
                        .with(user("platform-operator")
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "school:school:create")))
                        .with(csrf())
                        .header(SetupKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHOOL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("SKT-S01"));

        mockMvc.perform(get("/api/schools")
                        .with(user("platform-operator")
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "school:school:create")))
                        .header(SetupKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Setup Key Test School"));
    }

    /**
     * The filter guards {@code /api/schools/**} and nothing else.
     *
     * <p>A matcher that was too broad would answer 404 here instead, and 404 is the one status that
     * looks like an ordinary routing mistake rather than a bug — which is why it is asserted rather
     * than assumed. {@code AUTH_002} is what an unauthenticated request to a guarded endpoint gets
     * from {@code SecurityErrorResponder}, so seeing it proves the request reached the security
     * chain's authorization rules with the setup key filter having stood aside.
     */
    @Test
    void leavesEveryOtherEndpointAlone() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }
}
