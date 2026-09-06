package in.chalkbase.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Students, guardians and enrolment: the child's record and everything that hangs off it (ADR-0020).
 *
 * <p>Two schools throughout, for the reason {@code AcademicsApiTests} uses two: the claim being
 * tested is not "a student can be saved", it is that a child's record belongs to <em>one</em> school
 * and that the schema boundary is what makes that true. A single-tenant version of this file would
 * pass with the tenancy removed.
 *
 * <p>Deliberately not {@code @Transactional}. Four of the things being tested only happen at the
 * database — the partial unique index allowing one active enrolment per year, the one allowing one
 * primary guardian per child, the roll-number uniqueness scope, and the audit row joining the
 * caller's transaction — so a rolled-back test would report success for writes production refuses.
 * These commit and clean up after themselves.
 *
 * <p><strong>Every person in this file is invented</strong> (AGENTS rule 9, ADR-0014): the schools,
 * the children, the guardians, the phone numbers. Nothing here came from a real school, and nothing
 * ever may — a fixture holding a real child's data has no lawful basis and no consent record behind
 * it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StudentApiTests {

    private static final String RIVERBANK_SCHEMA = "studentriverbank";
    private static final String RIVERBANK_CODE = "SRV-707";
    private static final String CLOVERDALE_SCHEMA = "studentcloverdale";
    private static final String CLOVERDALE_CODE = "SCL-808";

    private static final String PASSWORD = "Riverbank#2026";

    private static final String STUDENTS = "/api/students";
    private static final String GUARDIANS = "/api/guardians";
    private static final String SESSIONS = "/api/academics/sessions";
    private static final String CLASSES = "/api/academics/classes";

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

    // ── Students ─────────────────────────────────────────────────────────────────────────────

    /** A single-name student, which is the case the one-name-field decision exists for (ADR-0020 §1). */
    @Test
    void admitsAStudentWithASingleNameAndNoEnrolmentYet() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(STUDENTS), session, studentBody("2026/0001", "Lakshmi", "2015-06-14", "FEMALE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.admissionNumber").value("2026/0001"))
                .andExpect(jsonPath("$.data.fullName").value("Lakshmi"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2015-06-14"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // Admission and placement are two decisions the office makes at two moments.
                .andExpect(jsonPath("$.data.currentEnrolment").doesNotExist())
                .andExpect(jsonPath("$.data.enrolments").isEmpty())
                .andExpect(jsonPath("$.data.guardians").isEmpty());
    }

    @Test
    void refusesASecondStudentWithTheSameAdmissionNumber() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createStudent(session, "2026/0001", "Aarav Kulkarni");

        mockMvc.perform(request(post(STUDENTS), session, studentBody("2026/0001", "Diya Nair", "2015-01-02", "FEMALE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STU_001"))
                // The number identifies a child, so it is not echoed back (ADR-0014).
                .andExpect(jsonPath("$.error.message")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("2026/0001"))));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    /** Two campuses of one trust may each hold 2026/0001: a schema is the uniqueness scope (ADR-0020 §3). */
    @Test
    void allowsTheSameAdmissionNumberAtTwoSchools() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        createStudent(riverbank, "2026/0001", "Aarav Kulkarni");
        createStudent(cloverdale, "2026/0001", "Ishaan Bose");

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        assertThat(studentCount(CLOVERDALE_SCHEMA)).isEqualTo(1);
    }

    @Test
    void refusesAStudentBornInTheFuture() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(STUDENTS), session, studentBody("2026/0002", "Kabir Rao", "2099-01-01", "MALE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.dateOfBirth").exists());

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    @Test
    void refusesAStudentWithNoNameNoNumberAndNoDateOfBirth() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(STUDENTS), session, """
                        {"admissionNumber": "", "fullName": "", "dateOfBirth": null,
                         "gender": "MALE", "status": "ACTIVE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.admissionNumber").exists())
                .andExpect(jsonPath("$.error.details.fullName").exists())
                .andExpect(jsonPath("$.error.details.dateOfBirth").exists());
    }

    /** {@code WITHDRAWN} is what leaving looks like. There is no DELETE, and the row stays (ADR-0020 §6). */
    @Test
    void withdrawsAStudentByStatusAndKeepsTheRow() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(session, "2026/0003", "Meera Joshi");

        mockMvc.perform(request(put(STUDENTS + "/" + student), session, """
                        {"admissionNumber": "2026/0003", "fullName": "Meera Joshi",
                         "dateOfBirth": "2015-03-09", "gender": "FEMALE", "status": "WITHDRAWN"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        assertThat(latestAuditFor(RIVERBANK_SCHEMA, student, "STUDENT").get("changed_fields"))
                .isEqualTo("status");

        // And there is no DELETE to reach for instead.
        mockMvc.perform(request(delete(STUDENTS + "/" + student), session, null))
                .andExpect(status().isMethodNotAllowed());
        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    @Test
    void recordsNothingWhenAStudentEditChangesNothing() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(session, "2026/0004", "Rohan Pillai");
        int afterCreate = auditCount(RIVERBANK_SCHEMA);

        mockMvc.perform(request(put(STUDENTS + "/" + student), session, """
                        {"admissionNumber": "2026/0004", "fullName": "Rohan Pillai",
                         "dateOfBirth": "2015-03-09", "gender": "MALE", "status": "ACTIVE"}
                        """)).andExpect(status().isOk());

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterCreate);
    }

    // ── The list: paging, sorting, filters ───────────────────────────────────────────────────

    @Test
    void pagesTheListAndSortsByFullNameByDefault() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createStudent(session, "2026/0011", "Zoya Sheikh");
        createStudent(session, "2026/0012", "Aarav Kulkarni");
        createStudent(session, "2026/0013", "Meera Joshi");

        mockMvc.perform(get(STUDENTS).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(25))
                // The whole name, because there is no surname to sort by (ADR-0020 §1).
                .andExpect(jsonPath("$.data.content[0].fullName").value("Aarav Kulkarni"))
                .andExpect(jsonPath("$.data.content[1].fullName").value("Meera Joshi"))
                .andExpect(jsonPath("$.data.content[2].fullName").value("Zoya Sheikh"));

        mockMvc.perform(get(STUDENTS + "?page=1&size=2").cookie(session))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Zoya Sheikh"));
    }

    @Test
    void searchesByNameAndByAdmissionNumber() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createStudent(session, "2026/0021", "Aarav Kulkarni");
        createStudent(session, "2026/0022", "Meera Joshi");

        mockMvc.perform(get(STUDENTS + "?q=joshi").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Meera Joshi"));

        // Case-insensitive, and over the admission number too — which is what the office types.
        mockMvc.perform(get(STUDENTS + "?q=AARAV").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(STUDENTS + "?q=0022").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].admissionNumber").value("2026/0022"));

        // A bare wildcard is a search for that character, not for everybody.
        mockMvc.perform(get(STUDENTS + "?q=%25").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void filtersByStatusAndReturnsStudentsWhoHaveLeftUnlessAsked() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createStudent(session, "2026/0031", "Aarav Kulkarni");
        UUID gone = createStudent(session, "2026/0032", "Meera Joshi");
        mockMvc.perform(request(put(STUDENTS + "/" + gone), session, """
                        {"admissionNumber": "2026/0032", "fullName": "Meera Joshi",
                         "dateOfBirth": "2015-03-09", "gender": "FEMALE", "status": "TRANSFERRED"}
                        """)).andExpect(status().isOk());

        // Unfiltered means every status: hiding leavers by default would hide the records a
        // transfer certificate is produced from.
        mockMvc.perform(get(STUDENTS).cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get(STUDENTS + "?status=TRANSFERRED").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Meera Joshi"));
        mockMvc.perform(get(STUDENTS + "?status=ACTIVE").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void filtersBySectionAndCarriesTheCurrentPlacementOnEveryRow() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID sectionB = addSection(session, classFive, "B");

        UUID inA = createStudent(session, "2026/0041", "Aarav Kulkarni");
        UUID inB = createStudent(session, "2026/0042", "Meera Joshi");
        createStudent(session, "2026/0043", "Unplaced Child");
        enrol(session, inA, year, sectionA, "12");
        enrol(session, inB, year, sectionB, "7");

        mockMvc.perform(get(STUDENTS + "?sectionId=" + sectionA).cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Aarav Kulkarni"))
                .andExpect(jsonPath("$.data.content[0].currentEnrolment.sessionName")
                        .value("2026-27"))
                .andExpect(
                        jsonPath("$.data.content[0].currentEnrolment.className").value("Class 5"))
                .andExpect(jsonPath("$.data.content[0].currentEnrolment.sectionName")
                        .value("A"))
                .andExpect(jsonPath("$.data.content[0].currentEnrolment.rollNumber")
                        .value("12"));

        // A student with no placement is still on the list, with no current enrolment.
        mockMvc.perform(get(STUDENTS + "?q=Unplaced").cookie(session))
                .andExpect(jsonPath("$.data.content[0].currentEnrolment").doesNotExist());
    }

    /**
     * "Current" means the year the school says it is in, not the newest year it has created.
     *
     * <p>A school setting up 2027-28 in February would otherwise see next year's class against every
     * child while still teaching 2026-27, with nothing on the row to say the number was for a
     * different year — a wrong answer that looks like a right one.
     */
    @Test
    void showsNoCurrentPlacementForAnEnrolmentInAYearTheSchoolIsNotIn() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID nextYear = createSession(session, "2027-28", "2027-04-01", "2028-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID student = createStudent(session, "2026/0051", "Aarav Kulkarni");

        enrol(session, student, nextYear, sectionA, "1");

        mockMvc.perform(get(STUDENTS).cookie(session))
                .andExpect(jsonPath("$.data.content[0].currentEnrolment").doesNotExist());
        mockMvc.perform(get(STUDENTS + "?sectionId=" + sectionA).cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // The placement is still on the record, which is where a history belongs.
        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(jsonPath("$.data.enrolments.length()").value(1))
                .andExpect(jsonPath("$.data.enrolments[0].sessionName").value("2027-28"))
                .andExpect(jsonPath("$.data.enrolments[0].sessionId").value(nextYear.toString()))
                .andExpect(jsonPath("$.data.currentEnrolment").doesNotExist());
    }

    // ── Enrolment ────────────────────────────────────────────────────────────────────────────

    @Test
    void enrolsAStudentAndAnswersWithTheClassBehindTheSection() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID student = createStudent(session, "2026/0061", "Aarav Kulkarni");

        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/enrolments"), session, enrolmentBody(year, sectionA, "12")))
                .andExpect(status().isCreated())
                // The class comes back although it was never sent: a section belongs to exactly one.
                .andExpect(jsonPath("$.data.classId").value(classFive.toString()))
                .andExpect(jsonPath("$.data.className").value("Class 5"))
                .andExpect(jsonPath("$.data.sectionName").value("A"))
                .andExpect(jsonPath("$.data.sessionName").value("2026-27"))
                .andExpect(jsonPath("$.data.rollNumber").value("12"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.enrolledOn").exists());

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, student, "STUDENT_ENROLMENT");
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        // Against the student's id, so "what happened to this child" is one query (StudentAudit).
        assertThat(row.get("entity_id")).isEqualTo(student.toString());
        assertThat(String.valueOf(row.get("changed_fields")).split(","))
                .containsExactlyInAnyOrder("academicSessionId", "sectionId", "rollNumber");
    }

    /**
     * The test {@code uq_student_enrolment_one_active} exists for.
     *
     * <p>Refused with a code the office can act on, rather than with a bare conflict from an index
     * name nobody outside the module has seen.
     */
    @Test
    void refusesASecondActiveEnrolmentInTheSameYear() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID sectionB = addSection(session, classFive, "B");
        UUID student = createStudent(session, "2026/0071", "Aarav Kulkarni");
        enrol(session, student, year, sectionA, "12");

        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/enrolments"), session, enrolmentBody(year, sectionB, "3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STU_002"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(enrolmentCount(RIVERBANK_SCHEMA, student)).isEqualTo(1);
    }

    /**
     * An ended enrolment alongside a live one in the same year is fine, which is why the index is
     * partial: a student genuinely moves from 5A to 5B mid-term.
     */
    @Test
    void allowsAnEndedEnrolmentBesideALiveOneInTheSameYear() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID sectionB = addSection(session, classFive, "B");
        UUID student = createStudent(session, "2026/0081", "Aarav Kulkarni");
        UUID first = enrol(session, student, year, sectionA, "12");

        mockMvc.perform(request(
                        put(STUDENTS + "/" + student + "/enrolments/" + first), session, """
                        {"sectionId": "%s", "rollNumber": "12", "active": false}
                        """.formatted(sectionA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        enrol(session, student, year, sectionB, "3");

        assertThat(enrolmentCount(RIVERBANK_SCHEMA, student)).isEqualTo(2);
        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(jsonPath("$.data.enrolments.length()").value(2))
                .andExpect(jsonPath("$.data.currentEnrolment.sectionName").value("B"));
    }

    /** Roll numbers are unique per section and year, which is the requirement's "per class-section-session". */
    @Test
    void refusesADuplicateRollNumberInOneSectionAndYear() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID sectionB = addSection(session, classFive, "B");
        UUID first = createStudent(session, "2026/0091", "Aarav Kulkarni");
        UUID second = createStudent(session, "2026/0092", "Meera Joshi");
        enrol(session, first, year, sectionA, "12");

        mockMvc.perform(request(
                        post(STUDENTS + "/" + second + "/enrolments"), session, enrolmentBody(year, sectionA, "12")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STU_003"));

        // The same number in another section of the same class is not a clash.
        enrol(session, second, year, sectionB, "12");
        assertThat(enrolmentCount(RIVERBANK_SCHEMA, second)).isEqualTo(1);
    }

    /** Nulls do not collide in a unique index, so a class list before roll numbers are assigned works. */
    @Test
    void allowsManyStudentsWithNoRollNumberYet() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID sectionA = addSection(session, createClass(session, "Class 5"), "A");

        enrol(session, createStudent(session, "2026/0101", "Aarav Kulkarni"), year, sectionA, null);
        enrol(session, createStudent(session, "2026/0102", "Meera Joshi"), year, sectionA, null);
        // A cleared box is not a roll number either — an empty string would be a value, and exactly
        // one student per section could hold it.
        enrol(session, createStudent(session, "2026/0103", "Rohan Pillai"), year, sectionA, "");

        assertThat(jdbc.sql("select count(*) from " + RIVERBANK_SCHEMA + ".student_enrolment where roll_number is null")
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
    }

    @Test
    void refusesAnEnrolmentNamingAnotherSchoolsSectionOrYear() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        UUID theirYear = currentSession(cloverdale, "2026-27", "2026-04-01", "2027-03-31");
        UUID theirSection = addSection(cloverdale, createClass(cloverdale, "Class 5"), "A");

        UUID myYear = currentSession(riverbank, "2026-27", "2026-04-01", "2027-03-31");
        UUID mySection = addSection(riverbank, createClass(riverbank, "Class 5"), "A");
        UUID student = createStudent(riverbank, "2026/0111", "Aarav Kulkarni");

        // 422, not 404: the student in the path exists, and it is the body that named something
        // unreachable. A 404 would tell the screen the child had been removed.
        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/enrolments"),
                        riverbank,
                        enrolmentBody(theirYear, mySection, "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_009"));

        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/enrolments"),
                        riverbank,
                        enrolmentBody(myYear, theirSection, "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_010"));

        assertThat(enrolmentCount(RIVERBANK_SCHEMA, student)).isZero();
    }

    @Test
    void movesAStudentBetweenSectionsAndRecordsOnlyWhatChanged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID sectionB = addSection(session, classFive, "B");
        UUID student = createStudent(session, "2026/0121", "Aarav Kulkarni");
        UUID enrolment = enrol(session, student, year, sectionA, "12");

        mockMvc.perform(request(
                        put(STUDENTS + "/" + student + "/enrolments/" + enrolment), session, """
                        {"sectionId": "%s", "rollNumber": "12", "active": true}
                        """.formatted(sectionB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionName").value("B"))
                .andExpect(jsonPath("$.data.rollNumber").value("12"));

        assertThat(latestAuditFor(RIVERBANK_SCHEMA, student, "STUDENT_ENROLMENT")
                        .get("changed_fields"))
                .isEqualTo("sectionId");
    }

    @Test
    void refusesAnEnrolmentEditThatOmitsWhetherItIsActive() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID sectionA = addSection(session, createClass(session, "Class 5"), "A");
        UUID student = createStudent(session, "2026/0131", "Aarav Kulkarni");
        UUID enrolment = enrol(session, student, year, sectionA, "12");

        mockMvc.perform(request(
                        put(STUDENTS + "/" + student + "/enrolments/" + enrolment), session, """
                        {"sectionId": "%s", "rollNumber": "12"}
                        """.formatted(sectionA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.active").exists());
    }

    // ── Guardians ────────────────────────────────────────────────────────────────────────────

    /**
     * The test ADR-0020 §5 exists for.
     *
     * <p>One person, two children, one correction. With a guardian copied per student, the school
     * that fixes one child's record leaves the other holding a number that no longer answers.
     */
    @Test
    void sharesOneGuardianBetweenSiblingsAndAPhoneCorrectionReachesBoth() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID elder = createStudent(session, "2026/0141", "Aarav Kulkarni");
        UUID younger = createStudent(session, "2026/0142", "Anaya Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");

        linkGuardian(session, elder, father, "FATHER", true);
        linkGuardian(session, younger, father, "FATHER", true);

        // One row in the guardian table, not two.
        assertThat(guardianCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        mockMvc.perform(get(GUARDIANS + "?q=Suresh").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].linkedStudentCount").value(2));

        mockMvc.perform(request(put(GUARDIANS + "/" + father), session, """
                        {"fullName": "Suresh Kulkarni", "phone": "+91 90000 00099",
                         "occupation": "Engineer"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+91 90000 00099"))
                .andExpect(jsonPath("$.data.linkedStudentCount").value(2));

        // One write, and both children see it.
        for (UUID child : List.of(elder, younger)) {
            mockMvc.perform(get(STUDENTS + "/" + child).cookie(session))
                    .andExpect(jsonPath("$.data.guardians.length()").value(1))
                    .andExpect(jsonPath("$.data.guardians[0].guardianId").value(father.toString()))
                    .andExpect(jsonPath("$.data.guardians[0].phone").value("+91 90000 00099"));
        }

        // Recorded against the person, not against either child: it is one change to one record.
        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, father, "GUARDIAN");
        assertThat(row.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(row.get("changed_fields")).isEqualTo("phone");
    }

    @Test
    void refusesTheSameGuardianTwiceOnOneChild() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(session, "2026/0151", "Aarav Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");
        linkGuardian(session, student, father, "FATHER", true);

        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/guardians"), session, linkBody(father, "GUARDIAN", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STU_004"));
    }

    /**
     * The test {@code uq_student_guardian_one_primary} exists for.
     *
     * <p>A partial unique index cannot be deferred, so making one link primary has to clear the
     * previous one and flush that clear before setting the new one. Any implementation that does not
     * is rejected by the database here, on the second reassignment rather than the first.
     */
    @Test
    void makingAGuardianPrimaryClearsThePreviousOneInTheSameTransaction() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(session, "2026/0161", "Aarav Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");
        UUID mother = createGuardian(session, "Lata Kulkarni", "+91 90000 00002");
        UUID uncle = createGuardian(session, "Prakash Kulkarni", "+91 90000 00003");

        linkGuardian(session, student, father, "FATHER", true);
        UUID motherLink = linkGuardian(session, student, mother, "MOTHER", false);
        UUID uncleLink = linkGuardian(session, student, uncle, "LOCAL_GUARDIAN", false);
        assertThat(primaryGuardianNames(RIVERBANK_SCHEMA, student)).containsExactly("Suresh Kulkarni");

        makePrimary(session, student, motherLink, "MOTHER");
        assertThat(primaryGuardianNames(RIVERBANK_SCHEMA, student)).containsExactly("Lata Kulkarni");

        // The second reassignment is the one a clear-after-set implementation fails on.
        makePrimary(session, student, uncleLink, "LOCAL_GUARDIAN");
        assertThat(primaryGuardianNames(RIVERBANK_SCHEMA, student)).containsExactly("Prakash Kulkarni");

        mockMvc.perform(get(STUDENTS + "/" + student).cookie(session))
                .andExpect(jsonPath("$.data.guardians.length()").value(3))
                // Primary contact first.
                .andExpect(jsonPath("$.data.guardians[0].linkId").value(uncleLink.toString()))
                .andExpect(jsonPath("$.data.guardians[0].primary").value(true))
                .andExpect(jsonPath("$.data.guardians[1].primary").value(false))
                .andExpect(jsonPath("$.data.guardians[2].primary").value(false));

        // The link that stopped being primary is recorded too, or the child's history would show
        // three guardians becoming the first contact and none ever stopping.
        // Three creations, and two reassignments each recording both the link that gained primary
        // and the link that lost it.
        assertThat(auditCountFor(RIVERBANK_SCHEMA, student, "STUDENT_GUARDIAN")).isEqualTo(7);
    }

    /** Two siblings may each have their own primary contact: the index is per student. */
    @Test
    void twoChildrenMayEachHaveAPrimaryGuardian() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID elder = createStudent(session, "2026/0171", "Aarav Kulkarni");
        UUID younger = createStudent(session, "2026/0172", "Anaya Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");

        linkGuardian(session, elder, father, "FATHER", true);
        linkGuardian(session, younger, father, "FATHER", true);

        assertThat(primaryGuardianNames(RIVERBANK_SCHEMA, elder)).containsExactly("Suresh Kulkarni");
        assertThat(primaryGuardianNames(RIVERBANK_SCHEMA, younger)).containsExactly("Suresh Kulkarni");
    }

    /**
     * The one DELETE in this module, and what it does not do.
     *
     * <p>The link goes; the person stays, along with every other child they are responsible for.
     * That is what makes this delete safe where a delete on a student or a guardian would not be.
     */
    @Test
    void detachingAGuardianFromOneChildLeavesThePersonAndTheirOtherChild() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID elder = createStudent(session, "2026/0181", "Aarav Kulkarni");
        UUID younger = createStudent(session, "2026/0182", "Anaya Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");
        UUID wrongLink = linkGuardian(session, elder, father, "FATHER", true);
        linkGuardian(session, younger, father, "FATHER", true);

        mockMvc.perform(request(delete(STUDENTS + "/" + elder + "/guardians/" + wrongLink), session, null))
                .andExpect(status().isNoContent());

        assertThat(guardianCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        mockMvc.perform(get(STUDENTS + "/" + elder).cookie(session))
                .andExpect(jsonPath("$.data.guardians").isEmpty());
        mockMvc.perform(get(STUDENTS + "/" + younger).cookie(session))
                .andExpect(jsonPath("$.data.guardians.length()").value(1));
        mockMvc.perform(get(GUARDIANS + "?q=Suresh").cookie(session))
                .andExpect(jsonPath("$.data.content[0].linkedStudentCount").value(1));

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, elder, "STUDENT_GUARDIAN");
        assertThat(row.get("action")).isEqualTo("ENTITY_DELETED");
        assertThat(row.get("entity_id")).isEqualTo(elder.toString());
    }

    /** There is no DELETE for a guardian, and none for a student (ADR-0020 §6). */
    @Test
    void offersNoWayToDeleteAPerson() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(session, "2026/0191", "Aarav Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");

        mockMvc.perform(request(delete(STUDENTS + "/" + student), session, null))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(request(delete(GUARDIANS + "/" + father), session, null))
                .andExpect(status().isMethodNotAllowed());

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        assertThat(guardianCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    @Test
    void refusesALinkNamingAGuardianFromAnotherSchool() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        UUID theirs = createGuardian(cloverdale, "Suresh Kulkarni", "+91 90000 00001");
        UUID student = createStudent(riverbank, "2026/0201", "Aarav Kulkarni");

        mockMvc.perform(request(
                        post(STUDENTS + "/" + student + "/guardians"), riverbank, linkBody(theirs, "FATHER", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_011"));
    }

    @Test
    void pagesAndSearchesTheGuardianDirectory() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createGuardian(session, "Zubin Wadia", "+91 90000 00009");
        createGuardian(session, "Anita Roy", "+91 90000 00010");
        createGuardian(session, "Suresh Kulkarni", "+91 90000 00011");

        mockMvc.perform(get(GUARDIANS).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Anita Roy"))
                .andExpect(jsonPath("$.data.content[2].fullName").value("Zubin Wadia"))
                // Nobody is linked yet, and the count says so rather than being absent.
                .andExpect(jsonPath("$.data.content[0].linkedStudentCount").value(0));

        mockMvc.perform(get(GUARDIANS + "?q=00011").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Suresh Kulkarni"));

        mockMvc.perform(get(GUARDIANS + "?page=0&size=2").cookie(session))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    /**
     * <strong>The bug this slice exists for.</strong>
     *
     * <p>The number is stored the way the office typed it — spaces, country code and all — and it is
     * searched for the way the next clerk types it, which is a different way. Before the digits
     * column, {@code phone like '%q%'} against the raw value found none of these three, and a phone
     * number is exactly the field that separates two people who share a surname. So the search
     * failed in the one case it had to work in, the clerk concluded the father was not here, and
     * typed him in a second time (ADR-0020 §5).
     *
     * <p>The country-code case comes for free from the unanchored match: the local ten digits are a
     * suffix of the same number stored with {@code +91} in front.
     */
    @Test
    void findsAGuardianByTheirNumberHoweverEitherSideWroteIt() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createGuardian(session, "Suresh Kulkarni", "+91 98765 43210");
        createGuardian(session, "Anita Roy", "+91 90000 00010");

        for (String typed : List.of("9876543210", "98765 43210", "+919876543210", "+91 98765 43210", "98765-43210")) {
            mockMvc.perform(get(GUARDIANS).param("q", typed).cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].fullName").value("Suresh Kulkarni"));
        }
    }

    /** The number is stored exactly as the school typed it. Only the search is normalised. */
    @Test
    void storesTheNumberExactlyAsItWasTyped() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createGuardian(session, "Suresh Kulkarni", "+91 98765 43210");

        assertThat(guardianPhones(RIVERBANK_SCHEMA)).containsExactly("+91 98765 43210");
        mockMvc.perform(get(GUARDIANS).param("q", "9876543210").cookie(session))
                .andExpect(jsonPath("$.data.content[0].phone").value("+91 98765 43210"));
    }

    /**
     * A term with no digits in it must contribute no phone predicate at all.
     *
     * <p>The obvious implementation — strip the term and always compare — is wrong in a way that
     * only shows up on this row. {@code phone_digits} is {@code ''} rather than null for a guardian
     * with no number, so {@code like '%%'} matches them, and a search for a name nobody has would
     * quietly return every phone-less guardian in the school.
     */
    @Test
    void doesNotMatchAPhonelessGuardianOnASearchWithNoDigitsInIt() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createGuardianWithoutPhone(session, "Anita Roy");
        createGuardian(session, "Suresh Kulkarni", "+91 98765 43210");

        mockMvc.perform(get(GUARDIANS).param("q", "Zubin").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // And she is still findable by the half of the search that does apply to her.
        mockMvc.perform(get(GUARDIANS).param("q", "Anita").cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // An empty search term is still "everybody", which is what the directory opens on.
        mockMvc.perform(get(GUARDIANS).cookie(session))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    /**
     * The endpoint that turns "linked to 2 students" into an answer.
     *
     * <p>A count tells a clerk the shared record is working; it does not tell them <em>which</em>
     * two, which is the question somebody comparing two similar records is actually asking. Without
     * the names the safest-looking move is a third record, which is the duplication the model exists
     * to prevent.
     */
    @Test
    void listsTheChildrenOneGuardianIsResponsibleFor() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID sectionA = addSection(session, createClass(session, "Class 5"), "A");

        UUID elder = createStudent(session, "2026/0141", "Aarav Kulkarni");
        UUID younger = createStudent(session, "2026/0142", "Anaya Kulkarni");
        UUID somebodyElse = createStudent(session, "2026/0143", "Ishaan Bose");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 98765 43210");
        UUID stranger = createGuardian(session, "Ritu Bose", "+91 90000 00021");

        enrol(session, elder, year, sectionA, "12");
        linkGuardian(session, elder, father, "FATHER", true);
        linkGuardian(session, younger, father, "FATHER", true);
        linkGuardian(session, somebodyElse, stranger, "MOTHER", true);

        mockMvc.perform(get(GUARDIANS + "/" + father + "/students").cookie(session))
                .andExpect(status().isOk())
                // Both siblings, by name, and nobody else's child.
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("Aarav Kulkarni"))
                .andExpect(jsonPath("$.data[0].studentId").value(elder.toString()))
                .andExpect(jsonPath("$.data[0].admissionNumber").value("2026/0141"))
                .andExpect(jsonPath("$.data[0].relation").value("FATHER"))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                // Which is the point of including it: this is how you tell two children apart.
                .andExpect(jsonPath("$.data[0].currentEnrolment.className").value("Class 5"))
                .andExpect(jsonPath("$.data[0].currentEnrolment.sectionName").value("A"))
                .andExpect(jsonPath("$.data[0].currentEnrolment.sessionName").value("2026-27"))
                .andExpect(jsonPath("$.data[1].fullName").value("Anaya Kulkarni"))
                // Admitted, not yet placed. A real state, and absent rather than null (ADR-0007).
                .andExpect(jsonPath("$.data[1].currentEnrolment").doesNotExist());

        // A guardian nobody points at answers with an empty list, not a 404.
        UUID unlinked = createGuardian(session, "Zubin Wadia", "+91 90000 00009");
        mockMvc.perform(get(GUARDIANS + "/" + unlinked + "/students").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /** Another school's guardian is not in this schema, so it is a 404 and not a leak (ADR-0011). */
    @Test
    void refusesTheChildrenOfAGuardianAtAnotherSchool() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        UUID theirs = createGuardian(cloverdale, "Ritu Bose", "+91 90000 00021");

        mockMvc.perform(get(GUARDIANS + "/" + theirs + "/students").cookie(riverbank))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"));
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /**
     * The test that makes the placement of these tables mean something. Each school's children live
     * in its own schema, so neither request can even name the other's rows.
     */
    @Test
    void twoSchoolsStudentsAndGuardiansAreInvisibleToEachOther() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID theirChild = createStudent(cloverdale, "2026/0001", "Ishaan Bose");
        UUID theirGuardian = createGuardian(cloverdale, "Ritu Bose", "+91 90000 00021");
        createStudent(riverbank, "2026/0001", "Aarav Kulkarni");
        createStudent(riverbank, "2026/0002", "Meera Joshi");

        mockMvc.perform(get(STUDENTS).cookie(riverbank))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get(STUDENTS).cookie(cloverdale))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(GUARDIANS).cookie(riverbank))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // Naming the other school's child is a 404, not a leak. Not even the name comes back.
        mockMvc.perform(get(STUDENTS + "/" + theirChild).cookie(riverbank))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"))
                .andExpect(jsonPath("$.error.message")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Ishaan"))));

        mockMvc.perform(request(put(GUARDIANS + "/" + theirGuardian), riverbank, """
                        {"fullName": "Renamed", "phone": "+91 90000 00099"}
                        """))
                .andExpect(status().isNotFound());
        assertThat(guardianNames(CLOVERDALE_SCHEMA)).containsExactly("Ritu Bose");
    }

    /** An audit row belongs to the school whose schema the change happened in, and to no other. */
    @Test
    void writesEachSchoolsAuditRowsIntoItsOwnSchema() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID mine = createStudent(riverbank, "2026/0001", "Aarav Kulkarni");
        createStudent(cloverdale, "2026/0001", "Ishaan Bose");

        assertThat(auditCountFor(RIVERBANK_SCHEMA, mine, "STUDENT")).isEqualTo(1);
        assertThat(auditCountFor(CLOVERDALE_SCHEMA, mine, "STUDENT")).isZero();
    }

    // ── The audit log holds no child's name or number ────────────────────────────────────────

    /**
     * <strong>The test AGENTS rule 9 and ADR-0014 exist for, asserted mechanically.</strong>
     *
     * <p>Every write this module can perform is exercised, and then every audit row it produced is
     * searched for anything identifying: each student's name and admission number, each guardian's
     * name and phone number. The audit log is read by more people than the student record is — an
     * inspection can be given {@code platform:audit:read} without {@code student:student:read} —
     * so a log carrying admission numbers would hand that reader a roster of the school's children
     * as a side effect of oversight.
     *
     * <p>It searches every column of every row rather than only {@code changed_fields}, because the
     * mistake this catches is not a bad field list — {@code AuditService} already rejects those — it
     * is somebody putting a name into {@code entity_id} because it read better in the log.
     */
    @Test
    void noAuditRowFromThisModuleContainsAChildsNameOrAdmissionNumber() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID sectionA = addSection(session, createClass(session, "Class 5"), "A");

        UUID student = createStudent(session, "2026/0211", "Aarav Kulkarni");
        UUID sibling = createStudent(session, "2026/0212", "Anaya Kulkarni");
        UUID father = createGuardian(session, "Suresh Kulkarni", "+91 90000 00001");
        UUID mother = createGuardian(session, "Lata Kulkarni", "+91 90000 00002");

        UUID enrolment = enrol(session, student, year, sectionA, "12");
        mockMvc.perform(request(
                        put(STUDENTS + "/" + student + "/enrolments/" + enrolment), session, """
                        {"sectionId": "%s", "rollNumber": "13", "active": true}
                        """.formatted(sectionA)))
                .andExpect(status().isOk());

        linkGuardian(session, student, father, "FATHER", true);
        UUID motherLink = linkGuardian(session, student, mother, "MOTHER", false);
        linkGuardian(session, sibling, father, "FATHER", true);
        makePrimary(session, student, motherLink, "MOTHER");
        mockMvc.perform(request(put(GUARDIANS + "/" + father), session, """
                        {"fullName": "Suresh Kulkarni", "phone": "+91 90000 00099"}
                        """)).andExpect(status().isOk());
        mockMvc.perform(request(delete(STUDENTS + "/" + student + "/guardians/" + motherLink), session, null))
                .andExpect(status().isNoContent());
        mockMvc.perform(request(put(STUDENTS + "/" + student), session, """
                        {"admissionNumber": "2026/0299", "fullName": "Aarav K Kulkarni",
                         "dateOfBirth": "2015-03-09", "gender": "MALE", "status": "ACTIVE"}
                        """)).andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbc.sql("select action, entity_type, entity_id, changed_fields, actor_name"
                        + " from " + RIVERBANK_SCHEMA + ".audit_event where entity_type in"
                        + " ('STUDENT', 'GUARDIAN', 'STUDENT_GUARDIAN', 'STUDENT_ENROLMENT')")
                .query()
                .listOfRows();

        assertThat(rows)
                .as("the writes above produced audit rows, or this test asserts nothing")
                .isNotEmpty();

        List<String> identifying = List.of(
                "Aarav",
                "Kulkarni",
                "Anaya",
                "Suresh",
                "Lata",
                "2026/0211",
                "2026/0212",
                "2026/0299",
                "+91 90000 00001",
                "+91 90000 00002",
                "+91 90000 00099",
                "2015-03-09");

        for (Map<String, Object> row : rows) {
            String whole = String.valueOf(row);
            for (String secret : identifying) {
                assertThat(whole).as("""
                                An audit row carries a child's or a guardian's identifying data. The log records
                                field NAMES and UUIDs only (ADR-0014, ADR-0018, AGENTS rule 9): never a name, an
                                admission number, a phone number or a date of birth, in any column — entity_id
                                included. Row: %s""".formatted(row.get("entity_type"))).doesNotContain(secret);
            }
        }
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    /** A class teacher may look at children and their guardians, and may not touch either. */
    @Test
    void refusesEveryWriteToSomeoneWhoMayOnlyRead() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = currentSession(principal, "2026-27", "2026-04-01", "2027-03-31");
        UUID sectionA = addSection(principal, createClass(principal, "Class 5"), "A");
        UUID student = createStudent(principal, "2026/0221", "Aarav Kulkarni");
        UUID guardian = createGuardian(principal, "Suresh Kulkarni", "+91 90000 00001");
        UUID enrolment = enrol(principal, student, year, sectionA, "12");
        UUID link = linkGuardian(principal, student, guardian, "FATHER", true);

        Cookie teacher = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "CLASS_TEACHER");

        mockMvc.perform(get(STUDENTS).cookie(teacher)).andExpect(status().isOk());
        mockMvc.perform(get(STUDENTS + "/" + student).cookie(teacher)).andExpect(status().isOk());
        mockMvc.perform(get(GUARDIANS).cookie(teacher)).andExpect(status().isOk());

        for (RequestBuilder write : writes(teacher, student, enrolment, guardian, link, year, sectionA)) {
            mockMvc.perform(write)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("PERM_001"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        // Nothing was changed by any of them.
        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        assertThat(guardianCount(RIVERBANK_SCHEMA)).isEqualTo(1);
        assertThat(enrolmentCount(RIVERBANK_SCHEMA, student)).isEqualTo(1);
    }

    /** An accountant reads both, because a fee is charged to a child and chased through a parent. */
    @Test
    void letsAnAccountantReadStudentsAndGuardiansAndChangeNeither() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(principal, "2026/0231", "Aarav Kulkarni");

        Cookie accountant = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "ACCOUNTANT");

        mockMvc.perform(get(STUDENTS).cookie(accountant)).andExpect(status().isOk());
        mockMvc.perform(get(GUARDIANS).cookie(accountant)).andExpect(status().isOk());
        mockMvc.perform(request(put(STUDENTS + "/" + student), accountant, """
                        {"admissionNumber": "2026/0231", "fullName": "Renamed",
                         "dateOfBirth": "2015-03-09", "gender": "MALE", "status": "ACTIVE"}
                        """)).andExpect(status().isForbidden());
    }

    /** A parent holds no student permission at all, so even the list is refused. */
    @Test
    void refusesEvenTheListsToSomeoneWithNoStudentPermission() throws Exception {
        Cookie parent = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PARENT");

        mockMvc.perform(get(STUDENTS).cookie(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        mockMvc.perform(get(GUARDIANS).cookie(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    /**
     * The permission decision on {@code GET /api/guardians/&#123;id&#125;/students}, asserted.
     *
     * <p>The two reads are separate so that a school can hand somebody the guardian directory
     * without handing them the roll ({@code StudentPermissions}). An endpoint that hangs off
     * {@code /api/guardians} but answers with children's names, admission numbers and classes must
     * not be the hole in that — so it is gated on {@code student:student:read}, and someone holding
     * only the guardian read gets the count on the directory and nothing more.
     *
     * <p>No shipped template holds one of these without the other, which is exactly why this role is
     * built by hand: the claim being tested is about the endpoint, not about the templates.
     */
    @Test
    void refusesAGuardiansChildrenToSomeoneWhoMayReadGuardiansButNotStudents() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID student = createStudent(principal, "2026/0241", "Aarav Kulkarni");
        UUID father = createGuardian(principal, "Suresh Kulkarni", "+91 98765 43210");
        linkGuardian(principal, student, father, "FATHER", true);

        Cookie frontDesk =
                signInWithPermissions(RIVERBANK_SCHEMA, RIVERBANK_CODE, "FRONT_DESK", "student:guardian:read");

        // The directory, with the count, is theirs.
        mockMvc.perform(get(GUARDIANS).cookie(frontDesk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].linkedStudentCount").value(1));

        // The names behind the count are not.
        mockMvc.perform(get(GUARDIANS + "/" + father + "/students").cookie(frontDesk))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /** A librarian holds neither read, which is what keeps guardian phone numbers off that desk. */
    @Test
    void refusesTheGuardianDirectoryToARoleThatHasNoBusinessWithIt() throws Exception {
        Cookie librarian = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "LIBRARIAN");

        mockMvc.perform(get(GUARDIANS).cookie(librarian))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void refusesEveryEndpointWithoutASessionAtAll() throws Exception {
        UUID anyId = UUID.randomUUID();

        mockMvc.perform(get(STUDENTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        mockMvc.perform(get(STUDENTS + "/" + anyId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        mockMvc.perform(get(GUARDIANS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        mockMvc.perform(get(GUARDIANS + "/" + anyId + "/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        for (RequestBuilder write : writes(null, anyId, anyId, anyId, anyId, anyId, anyId)) {
            mockMvc.perform(write)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        }
    }

    /** Every endpoint that changes something, in one list, so a new one cannot be forgotten here. */
    private List<RequestBuilder> writes(
            Cookie session, UUID student, UUID enrolment, UUID guardian, UUID link, UUID year, UUID sectionId) {
        return List.of(
                request(post(STUDENTS), session, studentBody("2099/0001", "Nobody At All", "2015-03-09", "OTHER")),
                request(
                        put(STUDENTS + "/" + student),
                        session,
                        studentBody("2099/0001", "Nobody At All", "2015-03-09", "OTHER")),
                request(post(STUDENTS + "/" + student + "/enrolments"), session, enrolmentBody(year, sectionId, "99")),
                request(put(STUDENTS + "/" + student + "/enrolments/" + enrolment), session, """
                        {"sectionId": "%s", "rollNumber": "99", "active": false}
                        """.formatted(sectionId)),
                request(post(GUARDIANS), session, """
                        {"fullName": "Nobody At All", "phone": "+91 90000 00000"}
                        """),
                request(put(GUARDIANS + "/" + guardian), session, """
                        {"fullName": "Nobody At All", "phone": "+91 90000 00000"}
                        """),
                request(post(STUDENTS + "/" + student + "/guardians"), session, linkBody(guardian, "OTHER", false)),
                request(put(STUDENTS + "/" + student + "/guardians/" + link), session, """
                        {"relation": "OTHER", "primary": false}
                        """),
                request(delete(STUDENTS + "/" + student + "/guardians/" + link), session, null));
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

    private static String studentBody(String admissionNumber, String fullName, String dateOfBirth, String gender) {
        return """
                {"admissionNumber": "%s", "fullName": "%s", "dateOfBirth": "%s",
                 "gender": "%s", "status": "ACTIVE"}
                """.formatted(admissionNumber, fullName, dateOfBirth, gender);
    }

    private static String enrolmentBody(UUID sessionId, UUID sectionId, String rollNumber) {
        return """
                {"academicSessionId": "%s", "sectionId": "%s", "rollNumber": %s}
                """.formatted(sessionId, sectionId, rollNumber == null ? "null" : "\"" + rollNumber + "\"");
    }

    private static String linkBody(UUID guardianId, String relation, boolean primary) {
        return """
                {"guardianId": "%s", "relation": "%s", "primary": %s}
                """.formatted(guardianId, relation, primary);
    }

    /**
     * Gender is derived from the invented first name only so the fixtures read plausibly. It has to
     * be deterministic, because a test that edits a student and asserts on {@code changed_fields}
     * would otherwise see a gender it never meant to change.
     */
    private static String genderOf(String fullName) {
        return fullName.split(" ")[0].endsWith("a") ? "FEMALE" : "MALE";
    }

    private UUID createStudent(Cookie session, String admissionNumber, String fullName) throws Exception {
        return idOf(
                mockMvc.perform(request(
                                post(STUDENTS),
                                session,
                                studentBody(admissionNumber, fullName, "2015-03-09", genderOf(fullName))))
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

    /** A record entered from a paper form that had only a name — the row `phone_digits` is `''` for. */
    private UUID createGuardianWithoutPhone(Cookie session, String fullName) throws Exception {
        return idOf(
                mockMvc.perform(request(post(GUARDIANS), session, """
                                {"fullName": "%s"}
                                """.formatted(fullName)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID enrol(Cookie session, UUID student, UUID year, UUID sectionId, String rollNumber) throws Exception {
        return idOf(
                mockMvc.perform(request(
                                post(STUDENTS + "/" + student + "/enrolments"),
                                session,
                                enrolmentBody(year, sectionId, rollNumber)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private UUID linkGuardian(Cookie session, UUID student, UUID guardian, String relation, boolean primary)
            throws Exception {
        return idOf(
                mockMvc.perform(request(
                                post(STUDENTS + "/" + student + "/guardians"),
                                session,
                                linkBody(guardian, relation, primary)))
                        .andExpect(status().isCreated()),
                "linkId");
    }

    private void makePrimary(Cookie session, UUID student, UUID linkId, String relation) throws Exception {
        mockMvc.perform(request(
                        put(STUDENTS + "/" + student + "/guardians/" + linkId), session, """
                        {"relation": "%s", "primary": true}
                        """.formatted(relation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primary").value(true));
    }

    private UUID createSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        return idOf(
                mockMvc.perform(request(post(SESSIONS), session, """
                                {"name": "%s", "startsOn": "%s", "endsOn": "%s"}
                                """.formatted(name, startsOn, endsOn)))
                        .andExpect(status().isCreated()),
                "id");
    }

    /** A year, and the school moved into it — which is what {@code currentEnrolment} is anchored on. */
    private UUID currentSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        UUID id = createSession(session, name, startsOn, endsOn);
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

    private static UUID idOf(ResultActions result, String field) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path(field).asText());
    }

    // ── reading the database directly ────────────────────────────────────────────────────────

    private int studentCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".student")
                .query(Integer.class)
                .single();
    }

    private int guardianCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".guardian")
                .query(Integer.class)
                .single();
    }

    private List<String> guardianPhones(String schema) {
        return jdbc.sql("select phone from " + schema + ".guardian order by full_name")
                .query(String.class)
                .list();
    }

    private List<String> guardianNames(String schema) {
        return jdbc.sql("select full_name from " + schema + ".guardian order by full_name")
                .query(String.class)
                .list();
    }

    private int enrolmentCount(String schema, UUID studentId) {
        return jdbc.sql("select count(*) from " + schema + ".student_enrolment where student_id = ?")
                .param(studentId)
                .query(Integer.class)
                .single();
    }

    private List<String> primaryGuardianNames(String schema, UUID studentId) {
        return jdbc.sql("select g.full_name from " + schema + ".student_guardian sg join " + schema
                        + ".guardian g on g.id = sg.guardian_id where sg.student_id = ? and sg.is_primary")
                .param(studentId)
                .query(String.class)
                .list();
    }

    private Map<String, Object> latestAuditFor(String schema, UUID entityId, String entityType) {
        return jdbc.sql("select action, entity_type, entity_id, actor_name, changed_fields from " + schema
                        + ".audit_event where entity_id = ? and entity_type = ?"
                        + " order by occurred_at desc, id desc limit 1")
                .params(entityId.toString(), entityType)
                .query()
                .singleRow();
    }

    private int auditCountFor(String schema, UUID entityId, String entityType) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_id = ? and entity_type = ?")
                .params(entityId.toString(), entityType)
                .query(Integer.class)
                .single();
    }

    /** Only this module's rows: sign-ins and academics writes make their own, and they are not counted here. */
    private int auditCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_type in"
                        + " ('STUDENT', 'GUARDIAN', 'STUDENT_GUARDIAN', 'STUDENT_ENROLMENT')")
                .query(Integer.class)
                .single();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    /**
     * Someone holding exactly what the named shipped template holds.
     *
     * <p>Granted through the template rather than by adding permissions to the school's own copy,
     * because what these tests assert about authorization is the shipped grant: a class teacher who
     * could edit a child's record only because the fixture said so would prove nothing.
     */
    private Cookie signInAs(String schema, String schoolCode, String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "-" + schema;
        createAccount(schema, username, "Ravi Deshpande");
        grantRole(schema, username, roleCode);
        return signIn(schoolCode, username);
    }

    /**
     * Someone holding exactly the named permissions and nothing else.
     *
     * <p>{@link #signInAs} is preferred everywhere it fits, because what it asserts is the shipped
     * grant. This exists for the one claim the templates cannot express: no shipped template holds
     * {@code student:guardian:read} without {@code student:student:read}, and the endpoint that
     * separates them has to be tested against a caller who really holds only one.
     */
    private Cookie signInWithPermissions(String schema, String schoolCode, String roleCode, String... permissions)
            throws Exception {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("insert into " + schema + ".role (id, code, name, description) values (?, ?, ?, ?)")
                .params(roleId, roleCode, roleCode, "Built by a test, not shipped.")
                .update();
        for (String permission : permissions) {
            jdbc.sql("insert into " + schema + ".role_permission (role_id, permission_code) values (?, ?)")
                    .params(roleId, permission)
                    .update();
        }

        String username = roleCode.toLowerCase() + "-" + schema;
        createAccount(schema, username, "Ravi Deshpande");
        grantRole(schema, username, roleCode);
        return signIn(schoolCode, username);
    }

    private Cookie signIn(String schoolCode, String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
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
            jdbc.sql("delete from " + schema + ".student_guardian").update();
            jdbc.sql("delete from " + schema + ".student_enrolment").update();
            jdbc.sql("delete from " + schema + ".guardian").update();
            jdbc.sql("delete from " + schema + ".student").update();
            jdbc.sql("delete from " + schema + ".section").update();
            jdbc.sql("delete from " + schema + ".school_class").update();
            jdbc.sql("delete from " + schema + ".academic_session").update();
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
