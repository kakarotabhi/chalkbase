package in.chalkbase.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The student record's contact, previous school, medical and compliance sections
 * (ADR-0020, ADR-0022, FR-028, FR-029, FR-033, FR-034).
 *
 * <p>The claim under test throughout the medical/compliance half is not "the fields save and load"
 * — that much every other section in this module already proves the pattern for. It is the three
 * things ADR-0014 asks of a Restricted field and nothing else in this codebase has exercised yet:
 * the ordinary read masks it, a second permissioned read reveals it, and revealing it is audited.
 * {@link #masksRestrictedFieldsOnTheOrdinaryReadAndRevealsThemOnTheAuditedOne} is the one test that
 * walks all three in order.
 *
 * <p>Deliberately not {@code @Transactional}, for the same reason {@code StudentApiTests} is not:
 * the audit row joins the caller's transaction, and a rolled-back test would report an audited read
 * that never actually committed.
 *
 * <p>Every person and value in this file is invented (AGENTS rule 9, ADR-0014).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StudentSensitiveApiTests {

    private static final String SCHEMA = "studentsensitiveriverbank";
    private static final String CODE = "SSV-909";
    private static final String PASSWORD = "Riverbank#2026";

    private static final String STUDENTS = "/api/students";

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
        schools.save(new School(CODE, "Riverbank International", SCHEMA, Board.CBSE, "Nagpur", "Maharashtra"));
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Contact and previous school: Confidential, not masked ───────────────────────────────

    @Test
    void savesAndReadsBackContactDetails() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID student = createStudent(session, "2026/1001", "Aarav Kulkarni");

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/contact"), session, """
                        {"address": "12 MG Road, Nagpur", "phone": "+91 90000 11111", "email": "aarav.g@example.test"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("12 MG Road, Nagpur"))
                .andExpect(jsonPath("$.data.phone").value("+91 90000 11111"));

        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contact.address").value("12 MG Road, Nagpur"))
                .andExpect(jsonPath("$.data.previousSchool").doesNotExist());

        assertThat(auditCountFor(student, "STUDENT_CONTACT")).isEqualTo(1);
    }

    @Test
    void savesAndReadsBackThePreviousSchool() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID student = createStudent(session, "2026/1002", "Diya Nair");

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/previous-school"), session, """
                        {"previousSchoolName": "Green Valley School", "previousSchoolBoard": "CBSE",
                         "transferCertificateNumber": "TC-4471"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousSchoolName").value("Green Valley School"));

        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousSchool.previousSchoolBoard").value("CBSE"));
    }

    // ── Medical and compliance: masked, revealed, audited ────────────────────────────────────

    /**
     * The one test that walks ADR-0014's three guarantees for a Restricted field in order: the
     * ordinary record never carries the value, the reveal endpoint does, and calling it is audited.
     */
    @Test
    void masksRestrictedFieldsOnTheOrdinaryReadAndRevealsThemOnTheAuditedOne() throws Exception {
        Cookie session = signInWithPermissions(
                "PRINCIPAL_LIKE",
                "student:student:read",
                "student:student:manage",
                "student:student:reveal_restricted");
        UUID student = createStudent(session, "2026/1003", "Kabir Rao");

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/medical"), session, """
                        {"bloodGroup": "O+", "cwsnStatus": "Locomotor disability", "allergies": "Peanuts",
                         "emergencyContactName": "Meera Rao", "emergencyContactPhone": "+91 90000 22222",
                         "emergencyContactRelation": "Mother"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasBloodGroup").value(true))
                .andExpect(jsonPath("$.data.hasCwsnStatus").value(true))
                .andExpect(jsonPath("$.data.hasAllergies").value(true))
                .andExpect(jsonPath("$.data.hasChronicConditions").value(false))
                // The masked shape never carries the value, even in the response to the write that set it.
                .andExpect(jsonPath("$.data.bloodGroup").doesNotExist())
                .andExpect(jsonPath("$.data.emergencyContactName").value("Meera Rao"));

        // The ordinary GET is the same story: presence flags, no values.
        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.medical.hasBloodGroup").value(true))
                .andExpect(jsonPath("$.data.medical.bloodGroup").doesNotExist());

        assertThat(auditCountFor(student, "STUDENT_MEDICAL")).isEqualTo(1); // the write above
        assertThat(auditActionsFor(student, "STUDENT_MEDICAL")).containsExactly("ENTITY_CREATED");

        // The reveal endpoint, and only it, answers with the real values.
        mockMvc.perform(get(STUDENTS + "/" + student + "/medical/restricted")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bloodGroup").value("O+"))
                .andExpect(jsonPath("$.data.cwsnStatus").value("Locomotor disability"))
                .andExpect(jsonPath("$.data.allergies").value("Peanuts"))
                .andExpect(jsonPath("$.data.chronicConditions").doesNotExist());

        // Calling it wrote a second audit row, on the same entity type, distinct from the write.
        assertThat(auditCountFor(student, "STUDENT_MEDICAL")).isEqualTo(2);
        assertThat(auditActionsFor(student, "STUDENT_MEDICAL"))
                .containsExactlyInAnyOrder("ENTITY_CREATED", "RESTRICTED_DATA_REVEALED");

        // And the value on disk is not the plaintext at all — it is ciphertext under the current key.
        String stored = jdbc.sql("select blood_group from " + SCHEMA + ".student_medical where student_id = ?")
                .param(student)
                .query(String.class)
                .single();
        assertThat(stored).startsWith("v1:").doesNotContain("O+");
    }

    @Test
    void revealingMedicalDataNeedsItsOwnPermission() throws Exception {
        // Holds student:student:read and student:student:manage, but not the reveal permission —
        // exactly the shipped VICE_PRINCIPAL/ADMISSION_COUNSELLOR shape (RoleTemplates).
        Cookie session = signInWithPermissions("MANAGER_ONLY", "student:student:read", "student:student:manage");
        UUID student = createStudent(session, "2026/1004", "Ishaan Bose");

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/medical"), session, """
                        {"bloodGroup": "A+"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get(STUDENTS + "/" + student + "/medical/restricted")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        // A denied reveal is not a granted one: no RESTRICTED_DATA_REVEALED row for this child.
        assertThat(auditActionsFor(student, "STUDENT_MEDICAL")).containsExactly("ENTITY_CREATED");
    }

    @Test
    void anApaarIdCannotBeSavedWithoutRecordedConsent() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID student = createStudent(session, "2026/1005", "Meera Iyer");

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/compliance"), session, """
                        {"apaarId": "1234-5678-9012", "apaarConsentGiven": false}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_019"));

        mockMvc.perform(request(put(STUDENTS + "/" + student + "/compliance"), session, """
                        {"apaarId": "1234-5678-9012", "apaarConsentGiven": true, "apaarConsentGivenBy": "Ravi Iyer"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasApaarId").value(true))
                .andExpect(jsonPath("$.data.apaarConsentGiven").value(true))
                .andExpect(jsonPath("$.data.apaarConsentGivenBy").value("Ravi Iyer"));
    }

    @Test
    void compliancePenAndBoardIdentifiersAreConfidentialNotMasked() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID student = createStudent(session, "2026/1006", "Rohan Verma");

        // apaarConsentGiven is a primitive boolean and has to be sent explicitly — the same
        // convention UpdateEnrolmentRequest.active and UpdateStudentGuardianRequest.primary
        // already follow. A consent flag defaulting itself when a caller forgets it would be the
        // wrong kind of lenient.
        mockMvc.perform(request(put(STUDENTS + "/" + student + "/compliance"), session, """
                        {"penUdiseId": "27140100123", "boardRegistrationNumber": "CBSE-9981",
                         "apaarConsentGiven": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.penUdiseId").value("27140100123"));

        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(status().isOk())
                // Confidential, not Restricted — sent in full on the ordinary read, unlike caste or APAAR.
                .andExpect(jsonPath("$.data.compliance.penUdiseId").value("27140100123"))
                .andExpect(jsonPath("$.data.compliance.hasCasteCategory").value(false));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder request(
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
        JsonNode response = JSON.readTree(mockMvc.perform(request(post(STUDENTS), session, body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        return UUID.fromString(response.path("data").path("id").asText());
    }

    private List<String> auditActionsFor(UUID studentId, String entityType) {
        return jdbc.sql("select action from " + SCHEMA
                        + ".audit_event where entity_id = ? and entity_type = ? order by occurred_at, id")
                .params(studentId.toString(), entityType)
                .query(String.class)
                .list();
    }

    private int auditCountFor(UUID studentId, String entityType) {
        return jdbc.sql("select count(*) from " + SCHEMA + ".audit_event where entity_id = ? and entity_type = ?")
                .params(studentId.toString(), entityType)
                .query(Integer.class)
                .single();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    private Cookie signInAs(String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "-sensitive";
        createAccount(username, "Ravi Deshpande");
        grantRole(username, roleCode);
        return signIn(username);
    }

    /** As {@code StudentApiTests#signInWithPermissions}: a role built by the test, holding exactly these. */
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
        String username = roleCode.toLowerCase() + "-sensitive";
        createAccount(username, "Ravi Deshpande");
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
