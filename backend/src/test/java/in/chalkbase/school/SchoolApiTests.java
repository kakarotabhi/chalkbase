package in.chalkbase.school;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchoolApiTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsAndReadsBackASchool() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "DPS-RKP",
                                  "name": "Delhi Public School, R. K. Puram",
                                  "board": "CBSE",
                                  "city": "New Delhi",
                                  "state": "Delhi"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("DPS-RKP"));

        mockMvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Delhi Public School, R. K. Puram"));
    }

    @Test
    void rejectsAnInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"\", \"name\": \"\", \"board\": \"CBSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
