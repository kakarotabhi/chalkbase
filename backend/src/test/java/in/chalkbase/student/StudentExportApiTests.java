package in.chalkbase.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The student and guardian CSV exports (ADR-0014, ADR-0027).
 *
 * <p>The claim under test is the same one {@code StudentSensitiveApiTests} makes for the reveal
 * endpoints, aimed at a file instead of a JSON body: the masked export never carries a Restricted
 * value <strong>anywhere in its bytes</strong> — not a column, not a stray copy — the unmasked
 * export does, it needs its own permission, and every export writes an audit row naming the fields
 * it disclosed and how many rows, whether or not it was the dangerous one.
 *
 * <p>Deliberately not {@code @Transactional}, for the reason {@code StudentSensitiveApiTests} gives:
 * the audit row joins the caller's transaction, and a rolled-back test would report an audited
 * export that never actually committed.
 *
 * <p>Every person and value in this file is invented (AGENTS rule 9, ADR-0014).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StudentExportApiTests {

    private static final String SCHEMA = "studentexportriverbank";
    private static final String CODE = "SXP-717";
    private static final String PASSWORD = "Millrace#2026";

    private static final String STUDENTS = "/api/students";
    private static final String GUARDIANS = "/api/guardians";

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
    void onboardOneSchool() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, "Millrace Public School", SCHEMA, Board.CBSE, "Pune", "Maharashtra"));
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    @Test
    void theMaskedExportNeverCarriesARestrictedValueAnywhereInItsBytes() throws Exception {
        Cookie session = signInWithPermissions(
                "OFFICE", "student:student:read", "student:student:manage", "student:student:reveal_restricted");
        UUID student = createStudent(session, "2026/2001", "Vihaan Deshmukh");
        setMedical(session, student, "O+", "Locomotor disability");
        setCompliance(session, student, "General", "Hindu");

        MvcResult started = mockMvc.perform(get(STUDENTS + "/export").cookie(session))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("students.csv");
        // Confidential is not masked: the office still gets the name and the class placement.
        assertThat(csv).contains("Vihaan Deshmukh").contains("2026/2001");
        // Every Restricted value and every Restricted header is entirely absent — not blank, not
        // marked, just never written. This is the property the export feature exists to guarantee.
        assertThat(csv)
                .doesNotContain("O+")
                .doesNotContain("Locomotor disability")
                .doesNotContain("General")
                .doesNotContain("Hindu")
                .doesNotContain("Blood Group")
                .doesNotContain("Caste Category");

        assertThat(auditCountFor("STUDENT_EXPORT")).isEqualTo(1);
        String fields = auditFieldsFor("STUDENT_EXPORT");
        assertThat(fields).contains("fullName").doesNotContain("bloodGroup").doesNotContain("casteCategory");
    }

    @Test
    void theUnmaskedExportNeedsItsOwnPermission() throws Exception {
        // Holds read and manage, and even reveal_restricted for one child's screen — but not the
        // bulk export permission, exactly the point ADR-0027 makes about the two being separate.
        Cookie session = signInWithPermissions(
                "MANAGER_ONLY", "student:student:read", "student:student:manage", "student:student:reveal_restricted");
        createStudent(session, "2026/2002", "Ishita Rao");

        mockMvc.perform(get(STUDENTS + "/export/unmasked").cookie(session).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        assertThat(auditCountFor("STUDENT_EXPORT")).isZero();
    }

    @Test
    void theUnmaskedExportIncludesEveryRestrictedFieldAndAuditsWhichOnes() throws Exception {
        Cookie session = signInWithPermissions(
                "AUDITOR_ADMIN", "student:student:read", "student:student:manage", "student:student:export_unmasked");
        UUID student = createStudent(session, "2026/2003", "Ananya Joshi");
        setMedical(session, student, "B+", "None");
        setCompliance(session, student, "OBC", "Hindu");

        MvcResult started = mockMvc.perform(get(STUDENTS + "/export/unmasked").cookie(session))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("students-unmasked.csv");
        assertThat(csv).contains("Ananya Joshi").contains("B+").contains("OBC").contains("Blood Group");

        assertThat(auditCountFor("STUDENT_EXPORT")).isEqualTo(1);
        String fields = auditFieldsFor("STUDENT_EXPORT");
        assertThat(fields).contains("bloodGroup").contains("casteCategory").contains("fullName");
        assertThat(auditRecordCountFor("STUDENT_EXPORT")).isEqualTo(1);
    }

    @Test
    void theGuardianExportIsAuditedTooEvenThoughNothingIsRestricted() throws Exception {
        Cookie session = signInWithPermissions("OFFICE2", "student:guardian:read", "student:guardian:manage");
        createGuardian(session, "Kunal Bhatt", "+91 90000 55555");

        MvcResult started = mockMvc.perform(get(GUARDIANS + "/export").cookie(session))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("guardians.csv");
        assertThat(csv).contains("Kunal Bhatt").contains("+91 90000 55555");

        assertThat(auditCountFor("GUARDIAN_EXPORT")).isEqualTo(1);
        assertThat(auditFieldsFor("GUARDIAN_EXPORT")).contains("fullName").contains("phone");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder withAuth(
            MockHttpServletRequestBuilder builder, Cookie session, String body) {
        builder.with(csrf());
        if (session != null) {
            builder.cookie(session);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    private UUID createStudent(Cookie session, String admissionNumber, String fullName) throws Exception {
        String body = """
                {"admissionNumber": "%s", "fullName": "%s", "dateOfBirth": "2015-03-09",
                 "gender": "MALE", "status": "ACTIVE"}
                """.formatted(admissionNumber, fullName);
        JsonNode response = JSON.readTree(mockMvc.perform(withAuth(post(STUDENTS), session, body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return UUID.fromString(response.path("data").path("id").asText());
    }

    private void setMedical(Cookie session, UUID studentId, String bloodGroup, String cwsnStatus) throws Exception {
        String body = """
                {"bloodGroup": "%s", "cwsnStatus": "%s"}
                """.formatted(bloodGroup, cwsnStatus);
        mockMvc.perform(withAuth(put(STUDENTS + "/" + studentId + "/medical"), session, body))
                .andExpect(status().isOk());
    }

    private void setCompliance(Cookie session, UUID studentId, String casteCategory, String religion) throws Exception {
        String body = """
                {"casteCategory": "%s", "religion": "%s", "apaarConsentGiven": false}
                """.formatted(casteCategory, religion);
        mockMvc.perform(withAuth(put(STUDENTS + "/" + studentId + "/compliance"), session, body))
                .andExpect(status().isOk());
    }

    private void createGuardian(Cookie session, String fullName, String phone) throws Exception {
        String body = """
                {"fullName": "%s", "phone": "%s"}
                """.formatted(fullName, phone);
        mockMvc.perform(withAuth(post(GUARDIANS), session, body)).andExpect(status().isCreated());
    }

    private String auditFieldsFor(String entityType) {
        return jdbc.sql("select changed_fields from " + SCHEMA
                        + ".audit_event where entity_type = ? and action = 'DATA_EXPORTED' order by occurred_at desc"
                        + " limit 1")
                .param(entityType)
                .query(String.class)
                .single();
    }

    private int auditCountFor(String entityType) {
        return jdbc.sql("select count(*) from " + SCHEMA
                        + ".audit_event where entity_type = ? and action = 'DATA_EXPORTED'")
                .param(entityType)
                .query(Integer.class)
                .single();
    }

    private int auditRecordCountFor(String entityType) {
        return jdbc.sql("select record_count from " + SCHEMA
                        + ".audit_event where entity_type = ? and action = 'DATA_EXPORTED' order by occurred_at desc"
                        + " limit 1")
                .param(entityType)
                .query(Integer.class)
                .single();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    /** A role built by the test, holding exactly these permissions. */
    private Cookie signInWithPermissions(String roleCode, String... permissions) throws Exception {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("insert into " + SCHEMA + ".role (id, code, name, description) values (?, ?, ?, ?)")
                .params(roleId, roleCode, roleCode, "Built by a test, not shipped.")
                .update();
        for (String permission : permissions) {
            jdbc.sql("insert into " + SCHEMA + ".role_permission (role_id, permission_code) values (?, ?)")
                    .params(roleId, permission)
                    .update();
        }
        String username = roleCode.toLowerCase() + "-export";
        createAccount(username, "Priya Kulkarni");
        grantRole(username, roleCode);
        return signIn(username);
    }

    private Cookie signIn(String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(CODE, username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void createAccount(String username, String displayName) {
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
    }

    private void grantRole(String username, String roleCode) {
        UUID roleId = jdbc.sql("select id from " + SCHEMA + ".role where code = ?")
                .param(roleCode)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + SCHEMA + ".user_role_grant (id, user_account_id, role_id, scope_type)"
                        + " values (?, (select user_account_id from " + SCHEMA
                        + ".user_identifier where type = 'USERNAME' and value = ?), ?, 'SCHOOL')")
                .params(UUID.randomUUID(), username, roleId)
                .update();
    }

    private void reset() {
        provisioning.provision(SCHEMA);
        jdbc.sql("delete from " + SCHEMA + ".student_compliance").update();
        jdbc.sql("delete from " + SCHEMA + ".student_medical").update();
        jdbc.sql("delete from " + SCHEMA + ".student_transfer").update();
        jdbc.sql("delete from " + SCHEMA + ".student_contact").update();
        jdbc.sql("delete from " + SCHEMA + ".student_guardian").update();
        jdbc.sql("delete from " + SCHEMA + ".student_enrolment").update();
        jdbc.sql("delete from " + SCHEMA + ".guardian").update();
        jdbc.sql("delete from " + SCHEMA + ".student").update();
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from " + SCHEMA + ".role").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
