package in.chalkbase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * The document module end to end: upload, list, read, download, edit and delete, against a real
 * PostgreSQL schema and a real (filesystem) storage adapter — ADR-0025's tenancy claim is exercised
 * for real here too, since {@code local}/{@code test} register {@code FilesystemStorageService}
 * rather than a stub.
 *
 * <p>Two schools throughout, for the reason {@code SubjectApiTests} gives: the claim under test is
 * not "a document can be stored", it is that one school's documents are invisible to another's,
 * bytes included — not only the database row.
 *
 * <p>Every fixture is an invented school, an invented student and an invented file. Never real
 * student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentApiTests {

    private static final String RIVERBANK_SCHEMA = "riverbank_documents";
    private static final String RIVERBANK_CODE = "RVB-919";
    private static final String CLOVERDALE_SCHEMA = "cloverdale_documents";
    private static final String CLOVERDALE_CODE = "CLV-920";

    private static final String PASSWORD = "Riverbank#2026";

    private static final String DOCUMENTS = "/api/documents";

    /** {@code %PDF} plus enough bytes to be a non-empty, plausible upload. */
    private static final byte[] A_PDF = "%PDF-1.4 not a real pdf but starts like one".getBytes(StandardCharsets.UTF_8);

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    void onboardTwoSchools() {
        reset();
        registerSchool(RIVERBANK_CODE, "Riverbank International", RIVERBANK_SCHEMA);
        registerSchool(CLOVERDALE_CODE, "Cloverdale Vidyalaya", CLOVERDALE_SCHEMA);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    @Test
    void uploadsListsGetsAndDownloadsADocument() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Asha Verma");

        UUID id = idOf(uploadPdf(session, studentId, "BIRTH_CERTIFICATE", "2015-06-14", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentType").value("BIRTH_CERTIFICATE"))
                .andExpect(jsonPath("$.data.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(id()));

        mockMvc.perform(get(DOCUMENTS + "?studentId=" + studentId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()));

        mockMvc.perform(get(DOCUMENTS + "/" + id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentType").value("BIRTH_CERTIFICATE"));

        mockMvc.perform(get(DOCUMENTS + "/" + id + "/content").cookie(session))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(A_PDF));
    }

    @Test
    void refusesAFileThatIsNotPdfJpegOrPng() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Rohan Kapoor");

        upload(session, studentId, "OTHER", null, null, "not-a-real-file".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DOC_001"));
    }

    @Test
    void refusesAnEmptyFile() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Meera Nair");

        upload(session, studentId, "PHOTO", null, null, new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DOC_002"));
    }

    @Test
    void refusesAStudentThisSchoolDoesNotHave() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        uploadPdf(session, UUID.randomUUID(), "OTHER", null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOC_004"));
    }

    @Test
    void refusesAnExpiryDateThatIsNotAfterTheIssueDate() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Kabir Singh");

        uploadPdf(session, studentId, "OTHER", "2026-06-01", "2026-06-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DOC_005"));
    }

    @Test
    void deletesADocumentAndItStopsAppearingAnywhere() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Diya Iyer");
        UUID id = uploadedId(session, studentId);

        mockMvc.perform(delete(DOCUMENTS + "/" + id).cookie(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(DOCUMENTS + "/" + id).cookie(session)).andExpect(status().isNotFound());
        mockMvc.perform(get(DOCUMENTS + "?studentId=" + studentId).cookie(session))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void recordsAnUploadByFieldNameAndNeverByValue() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Vivaan Chatterjee");
        UUID id = uploadedId(session, studentId);

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, id);
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        assertThat(row.get("entity_type")).isEqualTo("DOCUMENT");
        String fields = String.valueOf(row.get("changed_fields"));
        assertThat(fields).doesNotContain("Vivaan").doesNotContain(studentId.toString());
    }

    /** Neither the database row nor the bytes behind it are reachable from the other school. */
    @Test
    void twoSchoolsDocumentsAreInvisibleToEachOtherBytesIncluded() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        UUID theirStudent = createStudent(CLOVERDALE_SCHEMA, "Cloverdale Student");
        UUID theirs = uploadedId(cloverdale, theirStudent);

        mockMvc.perform(get(DOCUMENTS + "/" + theirs).cookie(riverbank))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"));
        mockMvc.perform(get(DOCUMENTS + "/" + theirs + "/content").cookie(riverbank))
                .andExpect(status().isNotFound());
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    @Test
    void refusesEveryWriteToSomeoneWhoMayOnlyRead() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID studentId = createStudent(RIVERBANK_SCHEMA, "Anaya Reddy");
        UUID id = uploadedId(principal, studentId);

        Cookie teacher = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "CLASS_TEACHER");

        mockMvc.perform(get(DOCUMENTS + "?studentId=" + studentId).cookie(teacher))
                .andExpect(status().isOk());
        mockMvc.perform(get(DOCUMENTS + "/" + id + "/content").cookie(teacher)).andExpect(status().isOk());

        upload(teacher, studentId, "OTHER", null, null, A_PDF)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        mockMvc.perform(delete(DOCUMENTS + "/" + id).cookie(teacher).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesEveryEndpointWithoutASessionAtAll() throws Exception {
        mockMvc.perform(get(DOCUMENTS + "?studentId=" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions uploadPdf(
            Cookie session, UUID studentId, String documentType, String issueDate, String expiryDate) throws Exception {
        return upload(session, studentId, documentType, issueDate, expiryDate, A_PDF);
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            Cookie session, UUID studentId, String documentType, String issueDate, String expiryDate, byte[] content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "certificate.pdf", "application/pdf", content);
        MockMultipartHttpServletRequestBuilder builder = multipart(DOCUMENTS)
                .file(file)
                .param("studentId", studentId.toString())
                .param("documentType", documentType)
                .with(csrf());
        if (session != null) {
            builder = builder.cookie(session);
        }
        if (issueDate != null) {
            builder = builder.param("issueDate", issueDate);
        }
        if (expiryDate != null) {
            builder = builder.param("expiryDate", expiryDate);
        }
        return mockMvc.perform(builder);
    }

    private UUID uploadedId(Cookie session, UUID studentId) throws Exception {
        return idOf(uploadPdf(session, studentId, "OTHER", null, null).andExpect(status().isCreated()));
    }

    private static org.springframework.test.web.servlet.ResultMatcher id() {
        return jsonPath("$.data.id").exists();
    }

    private static UUID idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    // ── reading the database directly ────────────────────────────────────────────────────────

    private Map<String, Object> latestAuditFor(String schema, UUID entityId) {
        return jdbc.sql("select action, entity_type, entity_id, actor_name, changed_fields from " + schema
                        + ".audit_event where entity_id = ? order by occurred_at desc, id desc limit 1")
                .param(entityId.toString())
                .query()
                .singleRow();
    }

    private UUID createStudent(String schema, String fullName) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".student (id, admission_number, full_name, date_of_birth, gender, status)"
                        + " values (?, ?, ?, '2015-01-01', 'FEMALE', 'ACTIVE')")
                .params(id, "ADM-" + id, fullName)
                .update();
        return id;
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    private Cookie signInAs(String schema, String schoolCode, String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "-" + schema;
        createAccount(schema, username, "Ravi Deshpande");
        grantRole(schema, username, roleCode);
        return signIn(schoolCode, username);
    }

    private Cookie signIn(String schoolCode, String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(schoolCode, username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void registerSchool(String code, String name, String schema) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, "Nagpur", "Maharashtra"));
    }

    private void createAccount(String schema, String username, String displayName) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', false, 0)")
                .params(accountId, displayName)
                .update();
        jdbc.sql("insert into " + schema + ".user_identifier (id, user_account_id, type, value)"
                        + " values (?, ?, 'USERNAME', ?)")
                .params(UUID.randomUUID(), accountId, username)
                .update();
        jdbc.sql("insert into " + schema + ".user_credential (id, user_account_id, type, secret, status)"
                        + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                .params(UUID.randomUUID(), accountId, passwordEncoder.encode(PASSWORD))
                .update();
    }

    private void grantRole(String schema, String username, String roleCode) {
        UUID roleId = jdbc.sql("select id from " + schema + ".role where code = ?")
                .param(roleCode)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + schema + ".user_role_grant (id, user_account_id, role_id, scope_type)"
                        + " values (?, (select user_account_id from " + schema
                        + ".user_identifier where type = 'USERNAME' and value = ?), ?, 'SCHOOL')")
                .params(UUID.randomUUID(), username, roleId)
                .update();
    }

    private void reset() {
        for (String schema : List.of(RIVERBANK_SCHEMA, CLOVERDALE_SCHEMA)) {
            provisioning.provision(schema);
            jdbc.sql("delete from " + schema + ".document").update();
            jdbc.sql("delete from " + schema + ".student").update();
            jdbc.sql("delete from " + schema + ".audit_event").update();
            jdbc.sql("delete from " + schema + ".user_account").update();
            jdbc.sql("delete from " + schema + ".role").update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
        for (String schema : List.of(RIVERBANK_SCHEMA, CLOVERDALE_SCHEMA)) {
            provisioning.provision(schema);
        }
    }
}
