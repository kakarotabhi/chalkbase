package in.chalkbase.school;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SchoolApiTests {
    // Deliberately NOT @Transactional. A unique constraint is only enforced when the insert reaches
    // the database, so a rolled-back test would report success for a duplicate that production
    // would reject. These tests commit and clean up after themselves instead.

    private static final String DPS = """
            {
              "code": "DPS-RKP",
              "name": "Delhi Public School, R. K. Puram",
              "board": "CBSE",
              "city": "New Delhi",
              "state": "Delhi"
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

    @Test
    void createsAndReadsBackASchool() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DPS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.code").value("DPS-RKP"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Delhi Public School, R. K. Puram"));
    }

    @Test
    void reportsFieldLevelValidationFailures() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"\", \"name\": \"\", \"board\": \"CBSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.code").exists())
                .andExpect(jsonPath("$.error.details.name").exists());
    }

    /** The unique index on school.code must surface as the school module's own message. */
    @Test
    void translatesADuplicateCodeIntoTheModulesOwnErrorCode() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DPS))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DPS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SCHOOL_001"))
                .andExpect(jsonPath("$.error.message").value("A school with this code already exists"));
    }

    @Test
    void rejectsAMalformedBodyWithoutEchoingIt() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"SECRET-VALUE\", not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_002"))
                // The body must not come back in the response.
                .andExpect(jsonPath("$.error.message").value("The request could not be read"));
    }

    @Test
    void returnsTheEnvelopeForAnUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/schools/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NF_001"));
    }

    @Test
    void rejectsAnUnparseableIdAsABadRequestRatherThanAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/schools/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    @Test
    void putsATraceIdOnEveryResponse() throws Exception {
        mockMvc.perform(get("/api/v1/schools")).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void honoursAnInboundTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/schools").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.traceId").value("abc-123"));
    }
}
