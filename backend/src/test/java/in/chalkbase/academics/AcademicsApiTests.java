package in.chalkbase.academics;

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

/**
 * Academic sessions, classes and sections: the structure a school's academic model hangs off
 * (ADR-0019).
 *
 * <p>Two schools throughout, for the reason {@code SchoolProfileApiTests} uses two: the claim being
 * tested is not "a class can be saved", it is that a ladder belongs to <em>one</em> school and that
 * the schema boundary is what makes that true. A single-tenant version of this file would pass with
 * the tenancy removed.
 *
 * <p>Deliberately not {@code @Transactional}. Three of the things being tested only happen at the
 * database — the partial unique index that allows one current session, the deferred unique
 * constraint that lets a reorder pass through an invalid state, and the audit row joining the
 * caller's transaction — so a rolled-back test would report success for writes production refuses.
 * These commit and clean up after themselves.
 *
 * <p>Every fixture is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AcademicsApiTests {

    private static final String RIVERBANK_SCHEMA = "riverbank";
    private static final String RIVERBANK_CODE = "RVB-707";
    private static final String CLOVERDALE_SCHEMA = "cloverdale";
    private static final String CLOVERDALE_CODE = "CLV-808";

    private static final String PASSWORD = "Riverbank#2026";

    private static final String SESSIONS = "/api/academics/sessions";
    private static final String CLASSES = "/api/academics/classes";
    private static final String CLASS_ORDER = "/api/academics/classes/order";

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

    // ── Academic sessions ────────────────────────────────────────────────────────────────────

    @Test
    void listsSessionsNewestFirstAndUnpaged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSession(session, "2025-26", "2025-04-01", "2026-03-31");
        createSession(session, "2027-28", "2027-04-01", "2028-03-31");
        createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        mockMvc.perform(get(SESSIONS).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // A bare array, not a page: a school gains one of these a year.
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("2027-28"))
                .andExpect(jsonPath("$.data[1].name").value("2026-27"))
                .andExpect(jsonPath("$.data[2].name").value("2025-26"))
                .andExpect(jsonPath("$.data[0].current").value(false));
    }

    /** A session created in February for next year must not move the school into it. */
    @Test
    void createsASessionThatIsNotYetTheCurrentOne() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(SESSIONS), session, sessionBody("2026-27", "2026-04-01", "2027-03-31")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("2026-27"))
                .andExpect(jsonPath("$.data.startsOn").value("2026-04-01"))
                .andExpect(jsonPath("$.data.endsOn").value("2027-03-31"))
                .andExpect(jsonPath("$.data.current").value(false));
    }

    /**
     * The test the partial unique index exists for.
     *
     * <p>{@code uq_academic_session_one_current} cannot be deferred — it is an index, not a
     * constraint — so the endpoint has to clear the previous session and flush that clear before
     * setting the new one. Any implementation that does not is rejected by the database here.
     */
    @Test
    void makingASessionCurrentClearsThePreviousOneInTheSameTransaction() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID first = createSession(session, "2025-26", "2025-04-01", "2026-03-31");
        UUID second = createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        makeCurrent(session, first);
        assertThat(currentSessionNames(RIVERBANK_SCHEMA)).containsExactly("2025-26");

        makeCurrent(session, second);
        assertThat(currentSessionNames(RIVERBANK_SCHEMA)).containsExactly("2026-27");

        // Newest first, so the year just switched into is first and the one it left is second.
        mockMvc.perform(get(SESSIONS).cookie(session))
                .andExpect(jsonPath("$.data[0].name").value("2026-27"))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[1].name").value("2025-26"))
                .andExpect(jsonPath("$.data[1].current").value(false));
    }

    /**
     * The switch answers with every session, not just the one switched into.
     *
     * <p>It changes two rows. A response carrying only the winner would leave the caller holding a
     * list in which two years both claim to be current — the new one from this response and the old
     * one it already had — with nothing in the answer to say otherwise. The bug would not look like
     * a bug: the screen would show two current years and both would be something the server had
     * said. So the endpoint answers with the set it rearranged, as the class reorder does.
     */
    @Test
    void answersTheSwitchWithEverySessionSoTheCallerNeedsNoRefetch() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID first = createSession(session, "2025-26", "2025-04-01", "2026-03-31");
        UUID second = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        makeCurrent(session, first);

        mockMvc.perform(request(post(SESSIONS + "/" + second + "/current"), session, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("2026-27"))
                .andExpect(jsonPath("$.data[0].current").value(true))
                // The year just left, in the same response, already saying it is no longer current.
                .andExpect(jsonPath("$.data[1].name").value("2025-26"))
                .andExpect(jsonPath("$.data[1].current").value(false));
    }

    /**
     * The switch changes two rows, so it records two — one naming the year the school moved into,
     * one against the year it left. Without the second, asking what happened to last year's session
     * would show it becoming current and never stopping.
     */
    @Test
    void recordsTheSwitchAgainstBothSessionsAndByFieldNameOnly() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID first = createSession(session, "2025-26", "2025-04-01", "2026-03-31");
        UUID second = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        makeCurrent(session, first);

        makeCurrent(session, second);

        Map<String, Object> arriving = latestAuditFor(RIVERBANK_SCHEMA, second);
        assertThat(arriving.get("action")).isEqualTo("SESSION_MADE_CURRENT");
        assertThat(arriving.get("entity_type")).isEqualTo("ACADEMIC_SESSION");
        assertThat(arriving.get("actor_name")).isEqualTo("Ravi Deshpande");
        assertThat(arriving.get("changed_fields")).isEqualTo("current");

        Map<String, Object> leaving = latestAuditFor(RIVERBANK_SCHEMA, first);
        assertThat(leaving.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(leaving.get("changed_fields")).isEqualTo("current");

        // Field names, never values (ADR-0014): neither row says which year anything became.
        assertThat(String.valueOf(arriving.get("changed_fields")))
                .doesNotContain("2026-27")
                .doesNotContain("true")
                .doesNotContain("=");
    }

    /** A double-clicked button is not an event. Nothing is written and nothing is recorded. */
    @Test
    void recordsNothingWhenTheSessionIsAlreadyTheCurrentOne() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID only = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        makeCurrent(session, only);
        int afterFirst = auditCount(RIVERBANK_SCHEMA);

        makeCurrent(session, only);

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterFirst);
        assertThat(currentSessionNames(RIVERBANK_SCHEMA)).containsExactly("2026-27");
    }

    @Test
    void recordsACreatedSessionByFieldNameAndNeverByValue() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        UUID created = createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, created);
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        assertThat(row.get("entity_type")).isEqualTo("ACADEMIC_SESSION");
        String fields = String.valueOf(row.get("changed_fields"));
        assertThat(fields.split(",")).containsExactlyInAnyOrder("endsOn", "name", "startsOn");
        assertThat(fields).doesNotContain("2026-27").doesNotContain("2026-04-01");
    }

    @Test
    void updatesASessionAndRecordsOnlyWhatActuallyChanged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        mockMvc.perform(request(put(SESSIONS + "/" + id), session, sessionBody("2026-27", "2026-04-01", "2027-03-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endsOn").value("2027-03-30"));

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, id);
        assertThat(row.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(row.get("changed_fields")).isEqualTo("endsOn");
    }

    @Test
    void recordsNothingWhenAnUpdateChangesNothing() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSession(session, "2026-27", "2026-04-01", "2027-03-31");
        int afterCreate = auditCount(RIVERBANK_SCHEMA);

        mockMvc.perform(request(put(SESSIONS + "/" + id), session, sessionBody("2026-27", "2026-04-01", "2027-03-31")))
                .andExpect(status().isOk());

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterCreate);
    }

    /**
     * Rejected in the DTO, so the client is told which box is wrong.
     *
     * <p>{@code ck_academic_session_dates} says the same thing at the table, and would surface as a
     * conflict with no field attached to it — which is the right answer for something writing
     * without going through the API, and the wrong one for a form.
     */
    @Test
    void namesTheFieldWhenASessionWouldEndBeforeItStarts() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(SESSIONS), session, sessionBody("2026-27", "2027-04-01", "2026-03-31")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.endsOn").exists())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(sessionCount(RIVERBANK_SCHEMA)).isZero();
    }

    @Test
    void refusesASessionWithNoNameOrNoDates() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(SESSIONS), session, """
                        {"name": "", "startsOn": null, "endsOn": null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.details.startsOn").exists())
                .andExpect(jsonPath("$.error.details.endsOn").exists());
    }

    /** The unique constraint is claimed by this module, so the school is told what it means. */
    @Test
    void refusesASecondSessionWithTheSameName() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSession(session, "2026-27", "2026-04-01", "2027-03-31");

        mockMvc.perform(request(post(SESSIONS), session, sessionBody("2026-27", "2027-04-01", "2028-03-31")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACAD_001"));

        assertThat(sessionCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    /** Each school has its own current session, and the same name in both is not a clash. */
    @Test
    void eachSchoolHasItsOwnSessionsAndItsOwnCurrentOne() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID here = createSession(riverbank, "2026-27", "2026-04-01", "2027-03-31");
        UUID there = createSession(cloverdale, "2026-27", "2026-04-01", "2027-03-31");
        makeCurrent(riverbank, here);
        makeCurrent(cloverdale, there);

        assertThat(currentSessionNames(RIVERBANK_SCHEMA)).containsExactly("2026-27");
        assertThat(currentSessionNames(CLOVERDALE_SCHEMA)).containsExactly("2026-27");

        // And neither can name the other's row.
        mockMvc.perform(request(post(SESSIONS + "/" + there + "/current"), riverbank, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"));
    }

    // ── Classes ──────────────────────────────────────────────────────────────────────────────

    /**
     * Create appends. A school inserting a rung between two existing ones would collide with
     * whichever class already holds that position, so the only thing create can honestly do is put
     * the new one at the end and leave reordering to the operation built for it.
     */
    @Test
    void appendsEachNewClassAtTheEndOfTheLadder() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(CLASSES), session, """
                        {"name": "Nursery"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.sections").isEmpty());

        createClass(session, "Class 1");
        createClass(session, "Class 2");

        mockMvc.perform(get(CLASSES).cookie(session))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Nursery"))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[2].name").value("Class 2"))
                .andExpect(jsonPath("$.data[2].sequence").value(3));
    }

    @Test
    void listsTheLadderInSequenceOrderWithEachClassesSectionsByName() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID nursery = createClass(session, "Nursery");
        UUID classOne = createClass(session, "Class 1");
        addSection(session, classOne, "C");
        addSection(session, classOne, "A");
        addSection(session, classOne, "B");
        addSection(session, nursery, "A");

        mockMvc.perform(get(CLASSES).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Nursery"))
                .andExpect(jsonPath("$.data[0].sections.length()").value(1))
                .andExpect(jsonPath("$.data[1].name").value("Class 1"))
                .andExpect(jsonPath("$.data[1].sections[0].name").value("A"))
                .andExpect(jsonPath("$.data[1].sections[1].name").value("B"))
                .andExpect(jsonPath("$.data[1].sections[2].name").value("C"));
    }

    /**
     * A retired class stays in the answer, flagged.
     *
     * <p>Filtering it out server-side would hide it from the only screen able to bring it back, and
     * there is no DELETE to reach for instead (ADR-0019).
     */
    @Test
    void deactivatesAClassAndStillReturnsItFlagged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createClass(session, "Class 11");

        mockMvc.perform(request(put(CLASSES + "/" + id), session, """
                        {"name": "Class 11", "active": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get(CLASSES).cookie(session))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].active").value(false));

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, id);
        assertThat(row.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(row.get("entity_type")).isEqualTo("SCHOOL_CLASS");
        assertThat(row.get("changed_fields")).isEqualTo("active");
    }

    @Test
    void recordsNothingWhenAClassEditChangesNothing() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createClass(session, "Class 5");
        int afterCreate = auditCount(RIVERBANK_SCHEMA);

        mockMvc.perform(request(put(CLASSES + "/" + id), session, """
                        {"name": "Class 5", "active": true}
                        """)).andExpect(status().isOk());

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterCreate);
    }

    /** {@code active} is boxed so that omitting it is a validation failure, not a silent retirement. */
    @Test
    void refusesAClassEditThatOmitsWhetherItIsActive() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createClass(session, "Class 5");

        mockMvc.perform(request(put(CLASSES + "/" + id), session, """
                        {"name": "Class 5"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.active").exists());

        assertThat(activeFlagOf(RIVERBANK_SCHEMA, "Class 5")).isTrue();
    }

    @Test
    void refusesASecondClassWithTheSameName() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createClass(session, "Class 5");

        mockMvc.perform(request(post(CLASSES), session, """
                        {"name": "Class 5"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACAD_004"));
    }

    // ── Reordering the ladder ────────────────────────────────────────────────────────────────

    /**
     * The test {@code uq_school_class_sequence deferrable initially deferred} exists for.
     *
     * <p>Swapping two adjacent classes passes through a state where both hold the same sequence.
     * A non-deferred constraint would reject the first update of the pair, and two separate calls
     * could not do it at all without a temporary value nobody wants in the table.
     */
    @Test
    void swapsTwoClassesThroughAStateTheConstraintWouldOtherwiseRefuse() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID lower = createClass(session, "Class 1");
        UUID upper = createClass(session, "Nursery");

        mockMvc.perform(request(put(CLASS_ORDER), session, orderBody(upper, lower)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Nursery"))
                .andExpect(jsonPath("$.data[0].sequence").value(1))
                .andExpect(jsonPath("$.data[1].name").value("Class 1"))
                .andExpect(jsonPath("$.data[1].sequence").value(2));

        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Nursery")).isEqualTo(1);
        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Class 1")).isEqualTo(2);
    }

    @Test
    void reversesTheWholeLadderInOneCall() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID a = createClass(session, "Class 1");
        UUID b = createClass(session, "Class 2");
        UUID c = createClass(session, "Class 3");

        mockMvc.perform(request(put(CLASS_ORDER), session, orderBody(c, b, a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Class 3"))
                .andExpect(jsonPath("$.data[1].name").value("Class 2"))
                .andExpect(jsonPath("$.data[2].name").value("Class 1"));

        // One audit row per class that actually moved. The middle one did not.
        assertThat(String.valueOf(latestAuditFor(RIVERBANK_SCHEMA, a).get("changed_fields")))
                .isEqualTo("sequence");
        assertThat(String.valueOf(latestAuditFor(RIVERBANK_SCHEMA, c).get("changed_fields")))
                .isEqualTo("sequence");
        assertThat(auditCountFor(RIVERBANK_SCHEMA, b)).isEqualTo(1); // only its creation
    }

    @Test
    void recordsNothingWhenTheOrderSentIsTheOrderAlreadyHeld() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID a = createClass(session, "Class 1");
        UUID b = createClass(session, "Class 2");
        int afterCreates = auditCount(RIVERBANK_SCHEMA);

        mockMvc.perform(request(put(CLASS_ORDER), session, orderBody(a, b))).andExpect(status().isOk());

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterCreates);
    }

    /**
     * The refusal this endpoint is mostly about.
     *
     * <p>A client that dropped one class would otherwise have the survivors renumbered into a
     * shorter ladder, and nothing would look wrong until somebody could not enrol a student into
     * the rung that fell off.
     */
    @Test
    void refusesAnOrderThatLeavesAClassOut() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID a = createClass(session, "Class 1");
        createClass(session, "Class 2");
        UUID c = createClass(session, "Class 3");

        mockMvc.perform(request(put(CLASS_ORDER), session, orderBody(c, a)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACAD_007"))
                .andExpect(jsonPath("$.error.details.missing").exists())
                .andExpect(jsonPath("$.data").doesNotExist());

        // Refused, not partly applied.
        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Class 1")).isEqualTo(1);
        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Class 2")).isEqualTo(2);
        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Class 3")).isEqualTo(3);
    }

    @Test
    void refusesAnOrderThatNamesAClassTwice() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID a = createClass(session, "Class 1");
        createClass(session, "Class 2");

        mockMvc.perform(request(put(CLASS_ORDER), session, orderBody(a, a)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACAD_007"))
                .andExpect(jsonPath("$.error.details.duplicated").exists())
                .andExpect(jsonPath("$.error.details.missing").exists());
    }

    /** Another school's class id is not in this schema, so it is unknown rather than reachable. */
    @Test
    void refusesAnOrderNamingAClassFromAnotherSchool() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        createClass(riverbank, "Class 1");
        UUID theirs = createClass(cloverdale, "Class 1");

        mockMvc.perform(request(put(CLASS_ORDER), riverbank, orderBody(theirs)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ACAD_007"))
                .andExpect(jsonPath("$.error.details.unknown").exists())
                .andExpect(jsonPath("$.error.details.missing").exists());
    }

    @Test
    void refusesAnOrderWithNoListAtAll() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(put(CLASS_ORDER), session, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.classIds").exists());
    }

    // ── Sections ─────────────────────────────────────────────────────────────────────────────

    @Test
    void addsASectionToAClassAndRecordsIt() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID classId = createClass(session, "Class 5");

        String body = mockMvc.perform(request(post(CLASSES + "/" + classId + "/sections"), session, """
                        {"name": "A"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("A"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID sectionId =
                UUID.fromString(JSON.readTree(body).path("data").path("id").asText());
        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, sectionId);
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        assertThat(row.get("entity_type")).isEqualTo("SECTION");
        assertThat(String.valueOf(row.get("changed_fields")).split(","))
                .containsExactlyInAnyOrder("name", "schoolClassId");
    }

    /** "A" means something only inside its class: every class has one, and they are different rooms. */
    @Test
    void allowsTheSameSectionNameInTwoClassesButNotTwiceInOne() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID five = createClass(session, "Class 5");
        UUID six = createClass(session, "Class 6");

        addSection(session, five, "A");
        addSection(session, six, "A");

        mockMvc.perform(request(post(CLASSES + "/" + five + "/sections"), session, """
                        {"name": "A"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACAD_006"));
    }

    @Test
    void renamesAndDeactivatesASection() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID classId = createClass(session, "Class 5");
        UUID sectionId = addSection(session, classId, "Secton A");

        mockMvc.perform(request(put("/api/academics/sections/" + sectionId), session, """
                        {"name": "Section A", "active": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Section A"))
                .andExpect(jsonPath("$.data.active").value(false));

        assertThat(String.valueOf(latestAuditFor(RIVERBANK_SCHEMA, sectionId).get("changed_fields"))
                        .split(","))
                .containsExactlyInAnyOrder("active", "name");

        // Deactivated, not deleted: it is still there, flagged, inside its class.
        mockMvc.perform(get(CLASSES).cookie(session))
                .andExpect(jsonPath("$.data[0].sections.length()").value(1))
                .andExpect(jsonPath("$.data[0].sections[0].active").value(false));
    }

    @Test
    void refusesASectionOnAClassThatDoesNotExistHere() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");
        UUID theirs = createClass(cloverdale, "Class 5");

        mockMvc.perform(request(post(CLASSES + "/" + theirs + "/sections"), riverbank, """
                        {"name": "A"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"));
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /**
     * The test that makes the placement of {@code school_class} mean something. Each school's
     * ladder lives in its own schema, so neither request can even name the other's rows — including
     * the sequence numbers, which are unique <em>per school</em> only because the constraint lives
     * in each school's own schema.
     */
    @Test
    void twoSchoolsLaddersAreInvisibleToEachOther() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID theirClassOne = createClass(cloverdale, "Class 1");
        addSection(cloverdale, theirClassOne, "A");
        createClass(riverbank, "Class 1");
        createClass(riverbank, "Class 2");

        mockMvc.perform(get(CLASSES).cookie(riverbank))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sections").isEmpty());

        mockMvc.perform(get(CLASSES).cookie(cloverdale))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sections[0].name").value("A"));

        // Both schools have a class at sequence 1, which is only possible because the unique
        // constraint lives in each school's own schema.
        assertThat(sequenceOf(RIVERBANK_SCHEMA, "Class 1")).isEqualTo(1);
        assertThat(sequenceOf(CLOVERDALE_SCHEMA, "Class 1")).isEqualTo(1);

        // And an edit aimed at the other school's class is a 404, not a leak.
        mockMvc.perform(request(put(CLASSES + "/" + theirClassOne), riverbank, """
                        {"name": "Renamed", "active": true}
                        """))
                .andExpect(status().isNotFound());
        assertThat(classNames(CLOVERDALE_SCHEMA)).containsExactly("Class 1");
    }

    /** An audit row belongs to the school whose schema the change happened in, and to no other. */
    @Test
    void writesEachSchoolsAuditRowsIntoItsOwnSchema() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID mine = createClass(riverbank, "Class 1");
        createClass(cloverdale, "Class 1");

        assertThat(auditCountFor(RIVERBANK_SCHEMA, mine)).isEqualTo(1);
        assertThat(auditCountFor(CLOVERDALE_SCHEMA, mine)).isZero();
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    /**
     * A class teacher may look and may not touch. This is the case a single {@code academics:*}
     * permission would have got wrong.
     */
    @Test
    void refusesEveryWriteToSomeoneWhoMayOnlyRead() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID sessionId = createSession(principal, "2026-27", "2026-04-01", "2027-03-31");
        UUID classId = createClass(principal, "Class 5");
        UUID sectionId = addSection(principal, classId, "A");

        Cookie teacher = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "CLASS_TEACHER");

        mockMvc.perform(get(SESSIONS).cookie(teacher)).andExpect(status().isOk());
        mockMvc.perform(get(CLASSES).cookie(teacher)).andExpect(status().isOk());

        for (RequestBuilder write : writes(teacher, sessionId, classId, sectionId)) {
            mockMvc.perform(write)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("PERM_001"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        // Nothing was changed by any of them.
        assertThat(classNames(RIVERBANK_SCHEMA)).containsExactly("Class 5");
        assertThat(sessionCount(RIVERBANK_SCHEMA)).isEqualTo(1);
    }

    /** A parent holds no academics permission at all, so even reading is refused. */
    @Test
    void refusesEvenTheListsToSomeoneWithNoAcademicsPermission() throws Exception {
        Cookie parent = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PARENT");

        mockMvc.perform(get(SESSIONS).cookie(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        mockMvc.perform(get(CLASSES).cookie(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void refusesEveryEndpointWithoutASessionAtAll() throws Exception {
        UUID anyId = UUID.randomUUID();

        mockMvc.perform(get(SESSIONS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        mockMvc.perform(get(CLASSES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        for (RequestBuilder write : writes(null, anyId, anyId, anyId)) {
            mockMvc.perform(write)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        }
    }

    /** Every endpoint that changes something, in one list, so a new one cannot be forgotten here. */
    private List<RequestBuilder> writes(Cookie session, UUID sessionId, UUID classId, UUID sectionId) {
        return List.of(
                request(post(SESSIONS), session, sessionBody("2099-00", "2099-04-01", "2100-03-31")),
                request(put(SESSIONS + "/" + sessionId), session, sessionBody("2099-00", "2099-04-01", "2100-03-31")),
                request(post(SESSIONS + "/" + sessionId + "/current"), session, null),
                request(post(CLASSES), session, """
                        {"name": "Class 99"}
                        """),
                request(put(CLASSES + "/" + classId), session, """
                        {"name": "Class 99", "active": false}
                        """),
                request(put(CLASS_ORDER), session, orderBody(classId)),
                request(post(CLASSES + "/" + classId + "/sections"), session, """
                        {"name": "Z"}
                        """),
                request(put("/api/academics/sections/" + sectionId), session, """
                        {"name": "Z", "active": false}
                        """));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Cookie session,
            String body) {
        builder.with(csrf());
        if (session != null) {
            builder.cookie(session);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return builder;
    }

    private static String sessionBody(String name, String startsOn, String endsOn) {
        return """
                {"name": "%s", "startsOn": "%s", "endsOn": "%s"}
                """.formatted(name, startsOn, endsOn);
    }

    private static String orderBody(UUID... ids) {
        return "{\"classIds\": ["
                + String.join(
                        ", ",
                        java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"").toList())
                + "]}";
    }

    private UUID createSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        return idOf(mockMvc.perform(request(post(SESSIONS), session, sessionBody(name, startsOn, endsOn)))
                .andExpect(status().isCreated()));
    }

    /** Switches the school into a year and checks the answer says so — the answer is every session. */
    private void makeCurrent(Cookie session, UUID id) throws Exception {
        mockMvc.perform(request(post(SESSIONS + "/" + id + "/current"), session, null))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data[?(@.id == '" + id + "')].current").value(org.hamcrest.Matchers.hasItem(true)));
    }

    private UUID createClass(Cookie session, String name) throws Exception {
        return idOf(mockMvc.perform(request(post(CLASSES), session, """
                        {"name": "%s"}
                        """.formatted(name)))
                .andExpect(status().isCreated()));
    }

    private UUID addSection(Cookie session, UUID classId, String name) throws Exception {
        return idOf(mockMvc.perform(request(post(CLASSES + "/" + classId + "/sections"), session, """
                        {"name": "%s"}
                        """.formatted(name)))
                .andExpect(status().isCreated()));
    }

    private static UUID idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    // ── reading the database directly ────────────────────────────────────────────────────────

    private List<String> currentSessionNames(String schema) {
        return jdbc.sql("select name from " + schema + ".academic_session where is_current order by name")
                .query(String.class)
                .list();
    }

    private int sessionCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".academic_session")
                .query(Integer.class)
                .single();
    }

    private List<String> classNames(String schema) {
        return jdbc.sql("select name from " + schema + ".school_class order by sequence")
                .query(String.class)
                .list();
    }

    private int sequenceOf(String schema, String name) {
        return jdbc.sql("select sequence from " + schema + ".school_class where name = ?")
                .param(name)
                .query(Integer.class)
                .single();
    }

    private boolean activeFlagOf(String schema, String name) {
        return jdbc.sql("select active from " + schema + ".school_class where name = ?")
                .param(name)
                .query(Boolean.class)
                .single();
    }

    private Map<String, Object> latestAuditFor(String schema, UUID entityId) {
        return jdbc.sql("select action, entity_type, entity_id, actor_name, changed_fields from " + schema
                        + ".audit_event where entity_id = ? order by occurred_at desc, id desc limit 1")
                .param(entityId.toString())
                .query()
                .singleRow();
    }

    private int auditCountFor(String schema, UUID entityId) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_id = ?")
                .param(entityId.toString())
                .query(Integer.class)
                .single();
    }

    /** Only this module's rows: sign-ins write their own, and they are not what these tests count. */
    private int auditCount(String schema) {
        return jdbc.sql("select count(*) from " + schema
                        + ".audit_event where entity_type in ('ACADEMIC_SESSION', 'SCHOOL_CLASS', 'SECTION')")
                .query(Integer.class)
                .single();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    /**
     * Someone holding exactly what the named shipped template holds.
     *
     * <p>Granted through the template rather than by adding permissions to the school's own copy,
     * because what these tests assert about authorization is the shipped grant: a class teacher who
     * could edit the ladder only because the fixture said so would prove nothing.
     */
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
