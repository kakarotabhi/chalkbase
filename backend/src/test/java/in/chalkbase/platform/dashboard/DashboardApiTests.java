package in.chalkbase.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The landing dashboard: which tiles a caller sees, by their own permissions, and the arithmetic
 * behind the ones {@code student} and {@code academics} contribute.
 *
 * <p>One school, unlike most API test files in this codebase. Tenant isolation for every read this
 * screen aggregates is already proven where that read lives — {@code AuditApiTests},
 * {@code AcademicsApiTests}, {@code StudentApiTests} — so a second school here would repeat that
 * claim rather than test a new one. What is new is which tiles a permission set produces and
 * whether the class-level arithmetic is right, and one school is enough to show both.
 *
 * <p><strong>PRINCIPAL and AUDITOR are the two shipped templates with the least overlap</strong>
 * ({@code RoleTemplates}): PRINCIPAL holds {@code academics:session:read},
 * {@code student:student:read} and {@code student:guardian:read} but not
 * {@code platform:audit:read}; AUDITOR holds only the last of the four. Signing in as each is what
 * proves the tiles are cut down per caller rather than all four always arriving together — which is
 * also, concretely, what changes for the auditor now that this screen exists (see the PR).
 *
 * <p>Deliberately not {@code @Transactional}: the audit rows this screen's own recent-activity tile
 * reads are committed by sign-in and by every fixture write, and a rolled-back test would never see
 * them.
 *
 * <p>Every person and school in this file is invented (AGENTS rule 9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardApiTests {

    private static final String SCHEMA = "dashboardschool";
    private static final String CODE = "DSH-707";
    private static final String PASSWORD = "Dashboard#2026";

    private static final String DASHBOARD = "/api/dashboard";
    private static final String SESSIONS = "/api/academics/sessions";
    private static final String CLASSES = "/api/academics/classes";
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
    void onboardSchool() {
        reset();
        provisioning.provision(SCHEMA);
        schools.save(new School(CODE, "Dashboard Test School", SCHEMA, Board.CBSE, "Pune", "Maharashtra"));
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Every field is a separate permission decision ───────────────────────────────────────────

    @Test
    void principalSeesEveryTileExceptRecentAudit() throws Exception {
        Cookie principal = signInAs("PRINCIPAL");

        UUID year = currentSession(principal, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(principal, "Class 5");
        UUID class5A = addSection(principal, classFive, "A");
        UUID class5B = addSection(principal, classFive, "B");
        UUID classSix = createClass(principal, "Class 6");
        UUID class6A = addSection(principal, classSix, "A");

        UUID s1 = createStudent(principal, "2026/001", "Aarav Kulkarni");
        UUID s2 = createStudent(principal, "2026/002", "Meera Joshi");
        UUID s3 = createStudent(principal, "2026/003", "Rohan Pillai");
        UUID s4 = createStudent(principal, "2026/004", "Isha Nair");
        UUID s5 = createStudent(principal, "2026/005", "Kabir Shah");
        UUID s6 = createStudent(principal, "2026/006", "Ananya Rao");
        // Admitted but never enrolled: must not count toward "enrolled".
        createStudent(principal, "2026/007", "Vihaan Menon");

        enrol(principal, s1, year, class5A, "1");
        enrol(principal, s2, year, class5A, "2");
        enrol(principal, s3, year, class5A, "3");
        enrol(principal, s4, year, class5B, "1");
        enrol(principal, s5, year, class6A, "1");
        enrol(principal, s6, year, class6A, "2");

        UUID linkedGuardian = createGuardian(principal, "Sunita Kulkarni", "9876500001");
        linkGuardian(principal, s1, linkedGuardian, "MOTHER", true);
        // A guardian on file, attached to nobody — the other half of the gap.
        createGuardian(principal, "Deepak Verma", "9876500002");

        mockMvc.perform(get(DASHBOARD).cookie(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.set").value(true))
                .andExpect(jsonPath("$.data.session.name").value("2026-27"))
                .andExpect(jsonPath("$.data.students.enrolled").value(6))
                .andExpect(jsonPath("$.data.students.byClass.length()").value(2))
                .andExpect(jsonPath("$.data.students.byClass[0].className").value("Class 5"))
                .andExpect(jsonPath("$.data.students.byClass[0].count").value(4))
                .andExpect(jsonPath("$.data.students.byClass[1].className").value("Class 6"))
                .andExpect(jsonPath("$.data.students.byClass[1].count").value(2))
                // 6 enrolled, 1 linked to a guardian (s1) — 5 with nobody on record.
                .andExpect(
                        jsonPath("$.data.linkageGaps.studentsWithoutAGuardian").value(5))
                .andExpect(
                        jsonPath("$.data.linkageGaps.guardiansWithoutAStudent").value(1))
                .andExpect(jsonPath("$.data.recentAudit").doesNotExist());
    }

    @Test
    void auditorSeesOnlyRecentAuditActivity() throws Exception {
        Cookie auditor = signInAs("AUDITOR");

        mockMvc.perform(get(DASHBOARD).cookie(auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session").doesNotExist())
                .andExpect(jsonPath("$.data.students").doesNotExist())
                .andExpect(jsonPath("$.data.linkageGaps").doesNotExist())
                // Signing in itself is a LOGIN_SUCCEEDED security event, so there is always at
                // least one row to show — the auditor's own sign-in.
                .andExpect(jsonPath("$.data.recentAudit.events").isNotEmpty())
                .andExpect(jsonPath("$.data.recentAudit.events.length()")
                        .value(org.hamcrest.Matchers.lessThanOrEqualTo(5)));
    }

    @Test
    void aClassTeacherSeesBothHalvesOfTheLinkageGapsTile() throws Exception {
        // A session has to exist for the student half to answer at all — see the no-session test
        // above. PRINCIPAL sets it up; a second account then signs in as CLASS_TEACHER, which
        // holds both student:student:read and student:guardian:read (RoleTemplates), unlike
        // SUBJECT_TEACHER, which holds only the first.
        Cookie principal = signInAs("PRINCIPAL");
        currentSession(principal, "2026-27", "2026-04-01", "2027-03-31");

        Cookie classTeacher = signInAs("CLASS_TEACHER");

        mockMvc.perform(get(DASHBOARD).cookie(classTeacher))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.linkageGaps.studentsWithoutAGuardian").value(0))
                .andExpect(
                        jsonPath("$.data.linkageGaps.guardiansWithoutAStudent").value(0));
    }

    // ── No session means no students tile, but the session tile still answers ──────────────────

    @Test
    void withNoSessionEverSetTheSessionTileSaysSoAndTheStudentsTileIsAbsent() throws Exception {
        Cookie principal = signInAs("PRINCIPAL");

        mockMvc.perform(get(DASHBOARD).cookie(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.set").value(false))
                .andExpect(jsonPath("$.data.session.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.session.name").doesNotExist())
                .andExpect(jsonPath("$.data.students").doesNotExist())
                // No session yet, so the student half of the gap is unanswerable; the guardian half
                // does not depend on a session and is still present.
                .andExpect(
                        jsonPath("$.data.linkageGaps.studentsWithoutAGuardian").doesNotExist())
                .andExpect(
                        jsonPath("$.data.linkageGaps.guardiansWithoutAStudent").value(0));
    }

    // ── No permission at all still gets 200, with everything absent ────────────────────────────

    @Test
    void aCallerWithNoRelevantPermissionGetsAnEmptyEnvelopeNotA403() throws Exception {
        // LIBRARIAN holds only school:school:read (RoleTemplates) — none of the four this screen
        // depends on.
        Cookie librarian = signInAs("LIBRARIAN");

        mockMvc.perform(get(DASHBOARD).cookie(librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session").doesNotExist())
                .andExpect(jsonPath("$.data.students").doesNotExist())
                .andExpect(jsonPath("$.data.linkageGaps").doesNotExist())
                .andExpect(jsonPath("$.data.recentAudit").doesNotExist());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private UUID currentSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        UUID id = idOf(
                mockMvc.perform(request(post(SESSIONS), session, """
                                {"name": "%s", "startsOn": "%s", "endsOn": "%s"}
                                """.formatted(name, startsOn, endsOn)))
                        .andExpect(status().isCreated()),
                "id");
        mockMvc.perform(request(post(SESSIONS + "/" + id + "/current"), session, null))
                .andExpect(status().isOk());
        return id;
    }

    private UUID createClass(Cookie session, String name) throws Exception {
        return idOf(
                mockMvc.perform(request(post(CLASSES), session, """
                                {"name": "%s"}
                                """.formatted(name)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID addSection(Cookie session, UUID classId, String name) throws Exception {
        return idOf(
                mockMvc.perform(request(post(CLASSES + "/" + classId + "/sections"), session, """
                                {"name": "%s"}
                                """.formatted(name)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID createStudent(Cookie session, String admissionNumber, String fullName) throws Exception {
        return idOf(
                mockMvc.perform(request(post(STUDENTS), session, """
                                {"admissionNumber": "%s", "fullName": "%s", "dateOfBirth": "2015-03-09",
                                 "gender": "MALE", "status": "ACTIVE"}
                                """.formatted(admissionNumber, fullName)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID enrol(Cookie session, UUID student, UUID year, UUID sectionId, String rollNumber) throws Exception {
        return idOf(
                mockMvc.perform(request(post(STUDENTS + "/" + student + "/enrolments"), session, """
                                {"academicSessionId": "%s", "sectionId": "%s", "rollNumber": "%s"}
                                """.formatted(
                                        year, sectionId, rollNumber)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID createGuardian(Cookie session, String fullName, String phone) throws Exception {
        return idOf(
                mockMvc.perform(request(post(GUARDIANS), session, """
                                {"fullName": "%s", "phone": "%s", "occupation": "Engineer"}
                                """.formatted(fullName, phone)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private void linkGuardian(Cookie session, UUID student, UUID guardian, String relation, boolean primary)
            throws Exception {
        mockMvc.perform(request(post(STUDENTS + "/" + student + "/guardians"), session, """
                        {"guardianId": "%s", "relation": "%s", "primary": %s}
                        """.formatted(
                                guardian, relation, primary)))
                .andExpect(status().isCreated());
    }

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

    private static UUID idOf(ResultActions result, String field) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path(field).asText());
    }

    /** Someone holding exactly what the named shipped template holds ({@code RoleTemplates}). */
    private Cookie signInAs(String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "-" + SCHEMA;
        createAccount(username, "Ravi Deshpande");
        grantRole(username, roleCode);
        return signIn(username);
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

    private void reset() {
        provisioning.provision(SCHEMA);
        for (String table : List.of(
                "student_guardian",
                "student_enrolment",
                "guardian",
                "student",
                "section",
                "school_class",
                "academic_session",
                "audit_event",
                "user_account")) {
            jdbc.sql("delete from " + SCHEMA + "." + table).update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
