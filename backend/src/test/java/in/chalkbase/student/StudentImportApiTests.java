package in.chalkbase.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Bulk student import: a school's existing roll, arriving as one CSV file (ADR-0021).
 *
 * <p>Two schools throughout, like {@code StudentApiTests}, because the claim is not "a CSV can be
 * parsed" — it is that six hundred children land in <em>one</em> school and that the schema boundary
 * is what makes that true.
 *
 * <p>Deliberately not {@code @Transactional}. Three of the things asserted here only happen at the
 * database: the all-or-nothing rollback, the unique constraints the in-memory checks are meant to
 * pre-empt, and the single audit row joining the caller's transaction. A rolled-back test would
 * report success for writes production refuses.
 *
 * <p><strong>Every child in this file is invented</strong> (AGENTS rule 9, ADR-0014) — and one test
 * here exists precisely to prove that none of them reaches a log line or an audit row, because a
 * file of six hundred real children is what this endpoint will be pointed at on its first day.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StudentImportApiTests {

    private static final String RIVERBANK_SCHEMA = "importriverbank";
    private static final String RIVERBANK_CODE = "IRV-707";
    private static final String CLOVERDALE_SCHEMA = "importcloverdale";
    private static final String CLOVERDALE_CODE = "ICL-808";

    private static final String PASSWORD = "Riverbank#2026";

    private static final String IMPORT = "/api/students/import";
    private static final String VALIDATE = "/api/students/import/validate";
    private static final String STUDENTS = "/api/students";
    private static final String SESSIONS = "/api/academics/sessions";
    private static final String CLASSES = "/api/academics/classes";

    /** Every column, in the order the contract lists them. Individual tests reorder and drop them. */
    private static final String HEADER =
            "admission_number,full_name,date_of_birth,gender,status,admitted_on,class,section,roll_number";

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

    // ── The happy path ───────────────────────────────────────────────────────────────────────

    /**
     * The whole point of the slice: a school's spreadsheet becomes its roll.
     *
     * <p>The academic year is <em>not</em> the school's current one here, on purpose. It is a form
     * field precisely so that a school setting up next year in February imports into the year it
     * meant rather than the year it happens to be in.
     */
    @Test
    void importsEveryStudentInACleanFile() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,2026-04-01,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,2026-04-01,Class 5,A,2",
                                "2026/0003,Ishaan Bose,2014-12-02,MALE,ACTIVE,,Class 6,B,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.validRows").value(3))
                .andExpect(jsonPath("$.data.imported").value(3))
                .andExpect(jsonPath("$.data.errorCount").value(0))
                .andExpect(jsonPath("$.data.errors").isEmpty());

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(3);
        assertThat(enrolmentCount(RIVERBANK_SCHEMA)).isEqualTo(3);
        assertThat(names(RIVERBANK_SCHEMA)).containsExactly("Aarav Kulkarni", "Ishaan Bose", "Lakshmi");
        // The one optional date left blank is absent rather than guessed at (ADR-0020).
        assertThat(admittedOn(RIVERBANK_SCHEMA, "2026/0003")).isNull();
    }

    /** One row per import, not six hundred (ADR-0021 §7). */
    @Test
    void recordsOneAuditRowForTheWholeImport() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2"))
                .andExpect(status().isOk());

        Map<String, Object> audited = jdbc.sql(
                        "select action, entity_type, entity_id, changed_fields, record_count from " + RIVERBANK_SCHEMA
                                + ".audit_event where action = 'STUDENTS_IMPORTED'")
                .query()
                .singleRow();
        assertThat(audited.get("entity_type")).isEqualTo("STUDENT_IMPORT");
        // The year, not a child: an import is a fact about the session it loaded.
        assertThat(audited.get("entity_id")).isEqualTo(ladder.year.toString());
        assertThat(String.valueOf(audited.get("changed_fields"))).contains("admissionNumber");

        // How many. Without this the row says who imported into which year and not whether that was
        // two children or six hundred, and the number would have to be reconstructed afterwards from
        // `created_at` timestamps — the forensic work an audit log exists to spare somebody.
        assertThat(audited.get("record_count")).isEqualTo(2);

        // And not two hundred ENTITY_CREATED rows burying everything else that happened that day.
        assertThat(auditCount(RIVERBANK_SCHEMA, "STUDENT")).isZero();
        assertThat(auditCount(RIVERBANK_SCHEMA, "STUDENT_ENROLMENT")).isZero();
    }

    /**
     * `M` and `F` are what an Indian school's spreadsheet actually holds.
     *
     * <p>Refusing them makes an office edit six hundred cells to say what the file already said, and
     * the file is not wrong — the initial is how gender is written on every admission form in the
     * country. Accepted only where exactly one value of the enum starts with that letter, so the
     * import can never quietly pick between two.
     */
    @Test
    void acceptsTheSingleLetterCodesASchoolActuallyTypes() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,M,A,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,F,A,,Class 5,A,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2));

        assertThat(jdbc.sql("select gender from " + RIVERBANK_SCHEMA + ".student order by admission_number")
                        .query(String.class)
                        .list())
                .containsExactly("MALE", "FEMALE");
    }

    /** An unrecognised word is still refused, and the message says the whole words. */
    @Test
    void stillRefusesAGenderItCannotResolve() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, file("2026/0001,Aarav Kulkarni,2015-03-09,BOY,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errors[0].column").value("gender"))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("MALE")));
    }

    // ── Validate writes nothing ──────────────────────────────────────────────────────────────

    /** The whole reason validation is a separate endpoint rather than a flag (ADR-0021 §1). */
    @Test
    void validatesACleanFileAndWritesNothingAtAll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        VALIDATE,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(2))
                // Zero because this endpoint does not import, not because the import failed.
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errors").isEmpty());

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
        assertThat(enrolmentCount(RIVERBANK_SCHEMA)).isZero();
        assertThat(auditCount(RIVERBANK_SCHEMA, "STUDENT_IMPORT")).isZero();
    }

    /** Validation reports the problems too, and still writes nothing. */
    @Test
    void validatesAFileWithProblemsWithoutWritingTheGoodRows() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        VALIDATE,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,14/06/2015,FEMALE,ACTIVE,,Class 5,A,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].column").value("date_of_birth"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    // ── All or nothing ───────────────────────────────────────────────────────────────────────

    /**
     * One bad row imports nobody (ADR-0021 §2), and the row number is the one on the left of the
     * person's spreadsheet — header row 1, first student row 2, so the third student is row 4.
     */
    @Test
    void importsNobodyWhenOneRowIsWrongAndSaysWhichRowItWas() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2",
                                "2026/0003,Ishaan Bose,14-12-2014,MALE,ACTIVE,,Class 6,B,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(4))
                .andExpect(jsonPath("$.data.errors[0].column").value("date_of_birth"))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("yyyy")));

        // Not "two imported, one failed": the two good rows are not in the school's database.
        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
        assertThat(enrolmentCount(RIVERBANK_SCHEMA)).isZero();
        assertThat(auditCount(RIVERBANK_SCHEMA, "STUDENT_IMPORT")).isZero();
    }

    /** The first student is row 2, and getting this wrong makes every message useless. */
    @Test
    void numbersTheFirstStudentAsRowTwo() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, file(",Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].column").value("admission_number"));
    }

    /** Every problem in the file, not the first one — a school fixing one per upload gives up. */
    @Test
    void reportsEveryProblemInTheFileRatherThanTheFirst() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        VALIDATE,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,ROBOT,ACTIVE,,Class 5,A,2",
                                "2026/0003,Ishaan Bose,2014-12-02,MALE,ACTIVE,,Class 9,B,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(3))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].column").value("full_name"))
                .andExpect(jsonPath("$.data.errors[1].row").value(3))
                .andExpect(jsonPath("$.data.errors[1].column").value("gender"))
                .andExpect(jsonPath("$.data.errors[2].row").value(4))
                .andExpect(jsonPath("$.data.errors[2].column").value("class"));
    }

    /** Everything is counted; only the first two hundred are listed, and the report says so. */
    @Test
    void capsTheListOfErrorsAndSaysHowManyThereReallyWere() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            rows.add("2026/%04d,Invented Child %d,not-a-date,MALE,ACTIVE,,Class 5,A,".formatted(i, i));
        }

        upload(VALIDATE, session, ladder.year, file(rows.toArray(String[]::new)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(250))
                .andExpect(jsonPath("$.data.validRows").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(250))
                .andExpect(jsonPath("$.data.errors.length()").value(200))
                // The first two hundred ROWS, not whichever two hundred checks ran first.
                .andExpect(jsonPath("$.data.errors[199].row").value(201));
    }

    // ── Duplicates inside the file, which the database would blame on the wrong row ──────────

    @Test
    void refusesAFileThatUsesOneAdmissionNumberTwice() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2",
                                "2026/0001,Ishaan Bose,2014-12-02,MALE,ACTIVE,,Class 6,B,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                // Reported against the SECOND claim, and it names the first.
                .andExpect(jsonPath("$.data.errors[0].row").value(4))
                .andExpect(jsonPath("$.data.errors[0].column").value("admission_number"))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("row 2")))
                // Never the number itself: it identifies a child (ADR-0014).
                .andExpect(jsonPath("$.data.errors[0].message")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("2026/0001"))));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    @Test
    void refusesAFileThatGivesOneRollNumberTwiceInOneSection() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,14",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].column").value("roll_number"))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("row 2")));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    /** Two 14s in two sections are two different children's roll numbers, and always were. */
    @Test
    void allowsOneRollNumberInEachSection() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,14",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 6,B,14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(2);
    }

    /** Several children with no roll number yet do not collide: nulls are distinct in the index. */
    @Test
    void allowsSeveralStudentsWithNoRollNumberAtAll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(2);
    }

    // ── Duplicates against what the school already has ───────────────────────────────────────

    @Test
    void refusesARowWhoseAdmissionNumberIsAlreadyOnTheRoll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);
        createStudent(session, "2026/0001", "Aarav Kulkarni");

        upload(IMPORT, session, ladder.year, file("2026/0001,Rohan Pillai,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].column").value("admission_number"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    @Test
    void refusesARowWhoseRollNumberIsAlreadyTakenInThatSection() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);
        UUID sitting = createStudent(session, "2026/0009", "Meera Joshi");
        enrol(session, sitting, ladder.year, ladder.classFiveA, "14");

        upload(IMPORT, session, ladder.year, file("2026/0001,Rohan Pillai,2015-03-09,MALE,ACTIVE,,Class 5,A,14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].column").value("roll_number"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    // ── Names, resolved against this school's own ladder (ADR-0021 §3) ───────────────────────

    /** "Class V" did not match "Class 5", and the message has to be good enough to explain that. */
    @Test
    void namesTheClassesItDoesHaveWhenAClassNameDoesNotMatch() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(VALIDATE, session, ladder.year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class V,A,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].column").value("class"))
                // The school's own ladder, which is Internal under ADR-0014 and is the half of the
                // comparison that actually helps.
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("Class 5")));
    }

    @Test
    void namesTheSectionsAClassDoesHaveWhenASectionNameDoesNotMatch() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(VALIDATE, session, ladder.year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,Z,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors[0].column").value("section"))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("Class 5")))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("A")));
    }

    /** Capitals and spacing are forgiven, because the school typed the name and the product chose it. */
    @Test
    void matchesAClassAndSectionWithoutRegardToCaseOrSpacing() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,, class  5 , a ,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));
    }

    // ── Reading the file ─────────────────────────────────────────────────────────────────────

    /** Excel writes one, and left in place it becomes part of the first column's name. */
    @Test
    void readsAFileThatStartsWithAByteOrderMark() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, "﻿" + file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));

        assertThat(names(RIVERBANK_SCHEMA)).containsExactly("Aarav Kulkarni");
    }

    /** {@code Nair, Meera} is a name a school will send, and it has a comma in it. */
    @Test
    void readsQuotedFieldsContainingCommasAndQuotes() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,\"Nair, Meera\",2015-03-09,FEMALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,\"Anand \"\"Bunny\"\" Rao\",2015-06-14,MALE,ACTIVE,,Class 5,A,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.imported").value(2));

        assertThat(names(RIVERBANK_SCHEMA)).containsExactly("Anand \"Bunny\" Rao", "Nair, Meera");
    }

    /** Windows line endings and a trailing blank line, which is what Excel actually saves. */
    @Test
    void readsWindowsLineEndingsAndIgnoresBlankLines() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        String csv = HEADER + "\r\n"
                + "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1\r\n"
                + "\r\n"
                + "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2\r\n"
                + "\r\n";

        upload(IMPORT, session, ladder.year, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.imported").value(2));
    }

    /** A row with an unquoted comma in it is a row error, not the end of the report. */
    @Test
    void reportsAMalformedRowAndKeepsReadingTheRest() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        VALIDATE,
                        session,
                        ladder.year,
                        file(
                                "2026/0001,Nair, Meera,2015-03-09,FEMALE,ACTIVE,,Class 5,A,1",
                                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                // The empty string, not an absent field: the problem is the row's rather than a
                // cell's, and a screen groups those with a comparison rather than a null check.
                .andExpect(jsonPath("$.data.errors[0].column").value(""))
                .andExpect(jsonPath("$.data.errors[0].message")
                        .value(org.hamcrest.Matchers.containsString("double quotes")));
    }

    @Test
    void reportsARowWhoseQuoteIsNeverClosed() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        VALIDATE,
                        session,
                        ladder.year,
                        HEADER + "\n"
                                + "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1\n"
                                + "2026/0002,\"Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3))
                .andExpect(jsonPath("$.data.errors[0].message").value(org.hamcrest.Matchers.containsString("quote")));
    }

    /** Order is irrelevant, case is forgiven, and the school's own extra columns are ignored. */
    @Test
    void matchesTheHeaderInAnyOrderAndIgnoresColumnsItDoesNotKnow() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        String csv = "Section,Class,Father's Name,Date Of Birth,Gender,Full Name,Admission Number\n"
                + "A,Class 5,Vikram Kulkarni,2015-03-09,male,Aarav Kulkarni,2026/0001\n";

        upload(IMPORT, session, ladder.year, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));

        assertThat(names(RIVERBANK_SCHEMA)).containsExactly("Aarav Kulkarni");
        // The guardian columns are read past, not imported (ADR-0021 §4).
        assertThat(guardianCount(RIVERBANK_SCHEMA)).isZero();
    }

    /** Only the six required columns. Status defaults, the other two are simply unknown. */
    @Test
    void acceptsAFileWithOnlyTheRequiredColumns() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        "admission_number,full_name,date_of_birth,gender,class,section\n"
                                + "2026/0001,Aarav Kulkarni,2015-03-09,MALE,Class 5,A\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));

        assertThat(statusOf(RIVERBANK_SCHEMA, "2026/0001")).isEqualTo("ACTIVE");
    }

    // ── Failures of the file rather than of a row ────────────────────────────────────────────

    @Test
    void refusesAnEmptyFile() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_012"));
    }

    @Test
    void refusesAFileWithNothingButAHeader() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, HEADER + "\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_012"));
    }

    /** A file that starts straight in with data names none of the columns, which is the same thing. */
    @Test
    void refusesAFileWhoseFirstRowIsNotAHeader() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(IMPORT, session, ladder.year, "2026/0001,Aarav Kulkarni,2015-03-09,MALE,Class 5,A\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_013"))
                .andExpect(jsonPath("$.error.details.full_name").exists());
    }

    /** A school that wrote {@code dob} has to see {@code dob} in the answer, not only what is missing. */
    @Test
    void namesTheColumnsItDoesNotRecogniseAsWellAsTheOnesItNeeds() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        "admission_number,full_name,dob,gender,class,section\n"
                                + "2026/0001,Aarav Kulkarni,2015-03-09,MALE,Class 5,A\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_013"))
                .andExpect(jsonPath("$.error.details.date_of_birth").exists())
                .andExpect(jsonPath("$.error.details.dob").exists());
    }

    @Test
    void refusesAFileThatNamesOneColumnTwice() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        upload(
                        IMPORT,
                        session,
                        ladder.year,
                        "admission_number,full_name,full_name,date_of_birth,gender,class,section\n"
                                + "2026/0001,Aarav Kulkarni,Aarav K,2015-03-09,MALE,Class 5,A\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_014"));
    }

    /**
     * Uploading before setting up the classes is an ordinary first-day mistake, and it gets one
     * sentence rather than six hundred identical row errors.
     */
    @Test
    void refusesTheWholeFileWhenTheSchoolHasNoClassLadderYet() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        upload(IMPORT, session, year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_018"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Academics")));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    /** Classes but no sections is the same mistake half made, and it needs the other sentence. */
    @Test
    void refusesTheWholeFileWhenTheClassesHaveNoSectionsYet() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID year = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        createClass(session, "Class 5");

        upload(VALIDATE, session, year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_018"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("sections")));
    }

    /** 2,000 rows is the cap, and the 2,001st is refused before anything is written (ADR-0021 §6). */
    @Test
    void refusesAFileWithMoreRowsThanOneImportMayCarry() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 2001; i++) {
            rows.add("2026/%05d,Invented Child %d,2015-03-09,MALE,ACTIVE,,Class 5,A,".formatted(i, i));
        }

        upload(IMPORT, session, ladder.year, file(rows.toArray(String[]::new)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_015"))
                // The screen names the cap this build enforces rather than holding a copy of it.
                .andExpect(jsonPath("$.error.details.maxRows").value("2000"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    /** Exactly the cap goes through, so the limit is the number written down and not one less. */
    @Test
    void acceptsAFileOfExactlyTheRowCap() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        List<String> rows = new ArrayList<>();
        for (int i = 1; i <= 2000; i++) {
            rows.add("2026/%05d,Invented Child %d,2015-03-09,MALE,ACTIVE,,Class 5,A,".formatted(i, i));
        }

        upload(IMPORT, session, ladder.year, file(rows.toArray(String[]::new)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2000));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(2000);
    }

    /** The commonest way this will be got wrong, and it deserves better than "your header is bad". */
    @Test
    void refusesAWorkbookAndSaysToSaveItAsCsv() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        byte[] xlsx = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};

        mockMvc.perform(multipart(IMPORT)
                        .file(new MockMultipartFile("file", "students.xlsx", "application/octet-stream", xlsx))
                        .param("academicSessionId", ladder.year.toString())
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("STU_016"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("CSV")));
    }

    @Test
    void refusesAnAcademicYearThatIsNotThisSchools() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        ladder(riverbank);
        Ladder theirs = ladder(cloverdale);

        upload(IMPORT, riverbank, theirs.year, file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STU_009"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    @Test
    void refusesAnUploadWithNoAcademicYearAtAll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(multipart(IMPORT)
                        .file(new MockMultipartFile("file", "students.csv", "text/csv", HEADER.getBytes(UTF_8())))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    @Test
    void refusesAnUploadWithNoFileAtAll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        mockMvc.perform(multipart(IMPORT)
                        .param("academicSessionId", ladder.year.toString())
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"));
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /**
     * Two schools import the same file and neither sees the other's children.
     *
     * <p>The same admission numbers on purpose: a schema is the uniqueness scope (ADR-0020 §3), and
     * two campuses of one trust really do both hold 2026/0001.
     */
    @Test
    void keepsTwoSchoolsImportsApart() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        Ladder ours = ladder(riverbank);
        Ladder theirs = ladder(cloverdale);

        String csv = file(
                "2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1",
                "2026/0002,Lakshmi,2015-06-14,FEMALE,ACTIVE,,Class 5,A,2");

        upload(IMPORT, riverbank, ours.year, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2));
        upload(IMPORT, cloverdale, theirs.year, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(2);
        assertThat(studentCount(CLOVERDALE_SCHEMA)).isEqualTo(2);
        assertThat(auditCount(RIVERBANK_SCHEMA, "STUDENT_IMPORT")).isEqualTo(1);
        assertThat(auditCount(CLOVERDALE_SCHEMA, "STUDENT_IMPORT")).isEqualTo(1);
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    /** A librarian may not load six hundred children into the school's register. */
    @Test
    void refusesBothEndpointsToARoleWithoutThePermission() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(principal);
        Cookie librarian = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "LIBRARIAN");

        String csv = file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1");
        upload(IMPORT, librarian, ladder.year, csv)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        // Validation is gated too: naming the admission numbers that already exist is a read of the
        // register by another name (ADR-0021).
        upload(VALIDATE, librarian, ladder.year, csv)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        assertThat(studentCount(RIVERBANK_SCHEMA)).isZero();
    }

    @Test
    void refusesBothEndpointsWithNoSessionAtAll() throws Exception {
        String csv = file("2026/0001,Aarav Kulkarni,2015-03-09,MALE,ACTIVE,,Class 5,A,1");

        upload(IMPORT, null, UUID.randomUUID(), csv)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        upload(VALIDATE, null, UUID.randomUUID(), csv)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ── The one that matters most ────────────────────────────────────────────────────────────

    /**
     * <strong>Nothing out of the file reaches a log line or an audit row</strong> (ADR-0014,
     * ADR-0021 §6).
     *
     * <p>Asserted by capturing every log event produced during a validation, a refused import and a
     * successful one, rather than by reading the code — the failure mode is a future edit that adds
     * a name to a message "to make it debuggable", and that edit will look reasonable.
     *
     * <p>The names and dates below are deliberately unlike anything else in the codebase, so that
     * finding one in the captured output means it came from this file and nowhere else.
     */
    @Test
    void putsNoNameOrDateOfBirthIntoTheAuditLogOrTheLogFile() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Ladder ladder = ladder(session);

        String name = "Zubeda Farooqui";
        String otherName = "Thangavelu Ponnambalam";
        String dateOfBirth = "2013-11-22";
        String admissionNumber = "9911/IMPORT-77";

        String clean = file(admissionNumber + "," + name + "," + dateOfBirth + ",FEMALE,ACTIVE,,Class 5,A,41");
        String dirty = file(
                "9911/IMPORT-78," + otherName + ",not-a-date,MALE,ACTIVE,,Class 5,A,42",
                "9911/IMPORT-79," + otherName + ",2013-11-22,MALE,ACTIVE,,Class Nine,A,43");

        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            upload(VALIDATE, session, ladder.year, clean).andExpect(status().isOk());
            upload(IMPORT, session, ladder.year, dirty).andExpect(status().isOk());
            upload(IMPORT, session, ladder.year, clean).andExpect(status().isOk());
        } finally {
            root.detachAppender(captured);
            captured.stop();
        }

        String logged = captured.list.stream()
                .map(event -> event.getFormattedMessage() + " " + event.getThrowableProxy())
                .reduce("", String::concat);

        assertThat(logged)
                .as("a child's name must never reach a log sink, at any level")
                .doesNotContain(name)
                .doesNotContain(otherName);
        assertThat(logged)
                .as("nor a date of birth, nor an admission number: both identify one child")
                .doesNotContain(dateOfBirth)
                .doesNotContain(admissionNumber);
        // The import did happen — otherwise this test passes by doing nothing.
        assertThat(studentCount(RIVERBANK_SCHEMA)).isEqualTo(1);

        String audited = String.join(
                " ",
                jdbc.sql("select coalesce(actor_name, '') || ' ' || action || ' ' || coalesce(entity_type, '')"
                                + " || ' ' || coalesce(entity_id, '') || ' ' || coalesce(changed_fields, '')"
                                + " from " + RIVERBANK_SCHEMA + ".audit_event")
                        .query(String.class)
                        .list());
        assertThat(audited)
                .as("the audit log records field names, never values (ADR-0018 §2)")
                .contains("STUDENTS_IMPORTED")
                .doesNotContain(name)
                .doesNotContain(otherName)
                .doesNotContain(dateOfBirth)
                .doesNotContain(admissionNumber);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static java.nio.charset.Charset UTF_8() {
        return StandardCharsets.UTF_8;
    }

    /** A file: the full header, then the rows given, each ended with a newline. */
    private static String file(String... rows) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (String row : rows) {
            csv.append(row).append('\n');
        }
        return csv.toString();
    }

    private ResultActions upload(String path, Cookie session, UUID academicSessionId, String csv) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(path);
        request.file(new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        request.param("academicSessionId", academicSessionId.toString());
        request.with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request);
    }

    /** The school's academic year and its ladder, which an import resolves names against. */
    private record Ladder(UUID year, UUID classFiveA) {}

    /**
     * A year the school is <em>not</em> currently in, and two classes with a section each.
     *
     * <p>Not the current session on purpose: the import takes the year as a form field, and a test
     * that used the current one would pass just as well if the field were being ignored.
     */
    private Ladder ladder(Cookie session) throws Exception {
        UUID year = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID sectionA = addSection(session, classFive, "A");
        UUID classSix = createClass(session, "Class 6");
        addSection(session, classSix, "B");
        return new Ladder(year, sectionA);
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

    private UUID createStudent(Cookie session, String admissionNumber, String fullName) throws Exception {
        return idOf(
                mockMvc.perform(request(post(STUDENTS), session, """
                                {"admissionNumber": "%s", "fullName": "%s", "dateOfBirth": "2015-03-09",
                                 "gender": "OTHER", "status": "ACTIVE"}
                                """.formatted(admissionNumber, fullName)))
                        .andExpect(status().isCreated()),
                "id");
    }

    private void enrol(Cookie session, UUID student, UUID year, UUID sectionId, String rollNumber) throws Exception {
        mockMvc.perform(request(post(STUDENTS + "/" + student + "/enrolments"), session, """
                        {"academicSessionId": "%s", "sectionId": "%s", "rollNumber": "%s"}
                        """.formatted(
                                year, sectionId, rollNumber)))
                .andExpect(status().isCreated());
    }

    private UUID createSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        return idOf(
                mockMvc.perform(request(post(SESSIONS), session, """
                                {"name": "%s", "startsOn": "%s", "endsOn": "%s"}
                                """.formatted(name, startsOn, endsOn)))
                        .andExpect(status().isCreated()),
                "id");
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

    private int enrolmentCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".student_enrolment")
                .query(Integer.class)
                .single();
    }

    private int guardianCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".guardian")
                .query(Integer.class)
                .single();
    }

    private List<String> names(String schema) {
        return jdbc.sql("select full_name from " + schema + ".student order by full_name")
                .query(String.class)
                .list();
    }

    private String statusOf(String schema, String admissionNumber) {
        return jdbc.sql("select status from " + schema + ".student where admission_number = ?")
                .param(admissionNumber)
                .query(String.class)
                .single();
    }

    private Object admittedOn(String schema, String admissionNumber) {
        return jdbc.sql("select admitted_on from " + schema + ".student where admission_number = ?")
                .param(admissionNumber)
                .query()
                .singleRow()
                .get("admitted_on");
    }

    private int auditCount(String schema, String entityType) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_type = ?")
                .param(entityType)
                .query(Integer.class)
                .single();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    private Cookie signInAs(String schema, String schoolCode, String roleCode) throws Exception {
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
