package in.chalkbase.fee;

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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/**
 * Fee heads, concession types and the session-scoped fee structure (ADR-0012, ADR-0033).
 *
 * <p>The claim under test throughout the structure tests is the one the whole module exists to
 * make true: {@code PUT .../structures/{sessionId}/{classId}} never rewrites a row.
 * {@link #editingAStructureSupersedesThePreviousVersionRatherThanRewritingIt} reads the table
 * directly to check that, and {@link #refusesASecondVersionOnceTheSessionHasAlreadyRun} is the
 * lock rule ADR-0012 rule 6 exists for.
 *
 * <p>Deliberately not {@code @Transactional}, for the same reason {@code AcademicsApiTests} is
 * not: several of these assertions read what the database itself enforced (the partial unique
 * index behind {@code uq_fee_structure_one_current}, the audit row joining the caller's
 * transaction), so a rolled-back test would report success for a write production would refuse.
 *
 * <p>Every fixture is an invented school and an invented person. Never real student or fee data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeeApiTests {

    private static final String SCHEMA = "brightfield";
    private static final String SCHOOL_CODE = "BRF-909";
    private static final String PASSWORD = "Brightfield#2026";

    private static final String SESSIONS = "/api/academics/sessions";
    private static final String CLASSES = "/api/academics/classes";
    private static final String HEADS = "/api/fees/heads";
    private static final String CONCESSION_TYPES = "/api/fees/concession-types";
    private static final String STRUCTURES = "/api/fees/structures";

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
    void onboardTheSchool() {
        reset();
        registerSchool(SCHOOL_CODE, "Brightfield Public School", SCHEMA);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Fee heads ────────────────────────────────────────────────────────────────────────────

    @Test
    void createsAndListsFeeHeadsByName() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        createHead(session, "Tuition Fee", "TUITION", null, true);
        createHead(session, "Admission Fee", "ADMISSION", null, true);

        mockMvc.perform(get(HEADS).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Admission Fee"))
                .andExpect(jsonPath("$.data[1].name").value("Tuition Fee"));
    }

    @Test
    void refusesADuplicateFeeHeadName() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        createHead(session, "Tuition Fee", "TUITION", null, true);

        mockMvc.perform(request(post(HEADS), session, headBody("Tuition Fee", "TUITION", null, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FEE_001"));
    }

    @Test
    void refusesACapOnAHeadThatIsNotAnnualDevelopment() throws Exception {
        Cookie session = signInAs("PRINCIPAL");

        mockMvc.perform(request(post(HEADS), session, headBody("Tuition Fee", "TUITION", "15", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FEE_003"));
    }

    @Test
    void retiringAHeadKeepsItVisibleAndFlagged() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID id = createHead(session, "Sports Fee", "ACTIVITY", null, true);

        mockMvc.perform(request(put(HEADS + "/" + id), session, headBody("Sports Fee", "ACTIVITY", null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get(HEADS).cookie(session))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].active").value(false));
    }

    // ── Concession types ─────────────────────────────────────────────────────────────────────

    @Test
    void createsAConcessionTypeThatRequiresApprovalByDefault() throws Exception {
        Cookie session = signInAs("PRINCIPAL");

        mockMvc.perform(request(post(CONCESSION_TYPES), session, """
                        {"name": "Sibling discount", "category": "SIBLING", "requiresApproval": true, "active": true}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requiresApproval").value(true))
                .andExpect(jsonPath("$.data.category").value("SIBLING"));
    }

    // ── Fee structure: versioning ────────────────────────────────────────────────────────────

    @Test
    void writesTheFirstVersionOfAStructureAndReadsItBack() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID futureSession = createSession(session, "2099-00", "2099-04-01", "2100-03-31");
        UUID classId = createClass(session, "Class 5");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);

        mockMvc.perform(request(
                        put(STRUCTURES + "/" + futureSession + "/" + classId),
                        session,
                        structureBody(item(tuition, "12000.00", "ANNUAL", installment("2099-04-10", "12000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].feeHeadName").value("Tuition Fee"))
                .andExpect(jsonPath("$.data.items[0].amount").value(12000.00))
                .andExpect(jsonPath("$.data.items[0].installments[0].dueDate").value("2099-04-10"));

        mockMvc.perform(get(STRUCTURES + "/" + futureSession + "/" + classId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    /** The claim this whole module exists to make true: an "edit" is a new row, never a rewrite. */
    @Test
    void editingAStructureSupersedesThePreviousVersionRatherThanRewritingIt() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID futureSession = createSession(session, "2099-00", "2099-04-01", "2100-03-31");
        UUID classId = createClass(session, "Class 5");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);

        saveStructure(
                session,
                futureSession,
                classId,
                item(tuition, "12000.00", "ANNUAL", installment("2099-04-10", "12000.00")));
        saveStructure(
                session,
                futureSession,
                classId,
                item(tuition, "13000.00", "ANNUAL", installment("2099-04-10", "13000.00")));

        // Two rows exist. The first is superseded, never rewritten; the second is what a read answers.
        assertThat(structureVersions(futureSession, classId)).containsExactly(1, 2);
        assertThat(supersededFlags(futureSession, classId)).containsExactly(true, false);

        mockMvc.perform(get(STRUCTURES + "/" + futureSession + "/" + classId).cookie(session))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.items[0].amount").value(13000.00));
    }

    /**
     * ADR-0012 rule 6, made concrete: once a session has started and is no longer current, its
     * structure can never be rewritten again — only the very first version, backfilled after the
     * fact, is ever allowed on a session in that state.
     */
    @Test
    void refusesASecondVersionOnceTheSessionHasAlreadyRun() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID pastSession = createSession(session, "2024-25", "2024-04-01", "2025-03-31");
        UUID classId = createClass(session, "Class 5");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);

        // The very first version may still be recorded, for the audit trail, even after the fact.
        mockMvc.perform(request(
                        put(STRUCTURES + "/" + pastSession + "/" + classId),
                        session,
                        structureBody(item(tuition, "9000.00", "ANNUAL", installment("2024-04-10", "9000.00")))))
                .andExpect(status().isOk());

        // A second version of the same, already-run session is refused.
        mockMvc.perform(request(
                        put(STRUCTURES + "/" + pastSession + "/" + classId),
                        session,
                        structureBody(item(tuition, "9500.00", "ANNUAL", installment("2024-04-10", "9500.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FEE_009"));

        assertThat(structureVersions(pastSession, classId)).containsExactly(1);
    }

    @Test
    void enforcesTheDevelopmentFeeCapAgainstTuitionInTheSameStructure() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID futureSession = createSession(session, "2099-00", "2099-04-01", "2100-03-31");
        UUID classId = createClass(session, "Class 5");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);
        UUID development = createHead(session, "Development Fee", "ANNUAL_DEVELOPMENT", "15", true);

        // 2,000 is 20% of 10,000 — over the 15% cap set on the head.
        mockMvc.perform(request(
                        put(STRUCTURES + "/" + futureSession + "/" + classId),
                        session,
                        structureBody(
                                item(tuition, "10000.00", "ANNUAL", installment("2099-04-10", "10000.00")),
                                item(development, "2000.00", "ANNUAL", installment("2099-04-10", "2000.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("FEE_004"));

        // 1,400 is 14% — inside the cap.
        mockMvc.perform(request(
                        put(STRUCTURES + "/" + futureSession + "/" + classId),
                        session,
                        structureBody(
                                item(tuition, "10000.00", "ANNUAL", installment("2099-04-10", "10000.00")),
                                item(development, "1400.00", "ANNUAL", installment("2099-04-10", "1400.00")))))
                .andExpect(status().isOk());
    }

    @Test
    void refusesInstallmentsThatDoNotSumToTheItemAmount() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID futureSession = createSession(session, "2099-00", "2099-04-01", "2100-03-31");
        UUID classId = createClass(session, "Class 5");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);

        mockMvc.perform(request(
                        put(STRUCTURES + "/" + futureSession + "/" + classId),
                        session,
                        structureBody(item(
                                tuition,
                                "12000.00",
                                "QUARTERLY",
                                installment("2099-04-10", "3000.00"),
                                installment("2099-07-10", "3000.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("FEE_006"));
    }

    // ── Copy from a previous session ─────────────────────────────────────────────────────────

    @Test
    void copiesFromAPreviousSessionAndSkipsAClassThatAlreadyHasOne() throws Exception {
        Cookie session = signInAs("PRINCIPAL");
        UUID lastSession = createSession(session, "2098-00", "2098-04-01", "2099-03-31");
        UUID nextSession = createSession(session, "2099-00", "2099-04-01", "2100-03-31");
        UUID classFive = createClass(session, "Class 5");
        UUID classSix = createClass(session, "Class 6");
        UUID tuition = createHead(session, "Tuition Fee", "TUITION", null, true);

        saveStructure(
                session,
                lastSession,
                classFive,
                item(tuition, "10000.00", "ANNUAL", installment("2098-04-10", "10000.00")));
        saveStructure(
                session,
                lastSession,
                classSix,
                item(tuition, "11000.00", "ANNUAL", installment("2098-04-10", "11000.00")));
        // Class 6 already has its own structure for the destination session, entered by hand.
        saveStructure(
                session,
                nextSession,
                classSix,
                item(tuition, "11500.00", "ANNUAL", installment("2099-04-10", "11500.00")));

        long dayShift = ChronoUnit.DAYS.between(LocalDate.parse("2098-04-01"), LocalDate.parse("2099-04-01"));
        String expectedDueDate =
                LocalDate.parse("2098-04-10").plusDays(dayShift).toString();

        mockMvc.perform(request(post(STRUCTURES + "/copy"), session, """
                        {"fromSessionId": "%s", "toSessionId": "%s"}
                        """.formatted(lastSession, nextSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.copied.length()").value(1))
                .andExpect(jsonPath("$.data.copied[0].schoolClassName").value("Class 5"))
                .andExpect(jsonPath("$.data.copied[0].items[0].amount").value(10000.00))
                .andExpect(jsonPath("$.data.copied[0].items[0].installments[0].dueDate")
                        .value(expectedDueDate))
                .andExpect(jsonPath("$.data.skippedClassNames").value(org.hamcrest.Matchers.contains("Class 6")));

        // Class 6's own entry is untouched — copying never overwrites what a school already entered.
        mockMvc.perform(get(STRUCTURES + "/" + nextSession + "/" + classSix).cookie(session))
                .andExpect(jsonPath("$.data.items[0].amount").value(11500.00));
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

    private static String headBody(String name, String category, String capPercentOfTuition, boolean active) {
        return capPercentOfTuition == null
                ? """
                {"name": "%s", "category": "%s", "active": %b}
                """.formatted(name, category, active)
                : """
                {"name": "%s", "category": "%s", "capPercentOfTuition": %s, "active": %b}
                """.formatted(name, category, capPercentOfTuition, active);
    }

    private static String item(UUID feeHeadId, String amount, String frequency, String... installments) {
        return """
                {"feeHeadId": "%s", "amount": %s, "frequency": "%s", "installments": [%s]}
                """.formatted(feeHeadId, amount, frequency, String.join(",", installments));
    }

    private static String installment(String dueDate, String amount) {
        return "{\"dueDate\": \"%s\", \"amount\": %s}".formatted(dueDate, amount);
    }

    private static String structureBody(String... items) {
        return "{\"items\": [" + String.join(",", items) + "]}";
    }

    private UUID createSession(Cookie session, String name, String startsOn, String endsOn) throws Exception {
        return idOf(mockMvc.perform(request(post(SESSIONS), session, sessionBody(name, startsOn, endsOn)))
                .andExpect(status().isCreated()));
    }

    private UUID createClass(Cookie session, String name) throws Exception {
        return idOf(mockMvc.perform(request(post(CLASSES), session, """
                        {"name": "%s"}
                        """.formatted(name)))
                .andExpect(status().isCreated()));
    }

    private UUID createHead(Cookie session, String name, String category, String capPercentOfTuition, boolean active)
            throws Exception {
        return idOf(
                mockMvc.perform(request(post(HEADS), session, headBody(name, category, capPercentOfTuition, active)))
                        .andExpect(status().isCreated()));
    }

    private void saveStructure(Cookie session, UUID sessionId, UUID classId, String... items) throws Exception {
        mockMvc.perform(request(put(STRUCTURES + "/" + sessionId + "/" + classId), session, structureBody(items)))
                .andExpect(status().isOk());
    }

    private static UUID idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    // ── reading the database directly ────────────────────────────────────────────────────────

    private List<Integer> structureVersions(UUID sessionId, UUID classId) {
        return jdbc.sql("select version from " + SCHEMA
                        + ".fee_structure where academic_session_id = ? and school_class_id = ? order by version")
                .params(sessionId, classId)
                .query(Integer.class)
                .list();
    }

    private List<Boolean> supersededFlags(UUID sessionId, UUID classId) {
        return jdbc.sql("select superseded_at is not null from " + SCHEMA
                        + ".fee_structure where academic_session_id = ? and school_class_id = ? order by version")
                .params(sessionId, classId)
                .query(Boolean.class)
                .list();
    }

    // ── onboarding and sign-in ───────────────────────────────────────────────────────────────

    private Cookie signInAs(String roleCode) throws Exception {
        String username = roleCode.toLowerCase() + "-" + SCHEMA;
        createAccount(username, "Meera Iyer");
        grantRole(username, roleCode);
        return signIn(username);
    }

    private Cookie signIn(String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolCode": "%s", "username": "%s", "password": "%s"}
                                """.formatted(SCHOOL_CODE, username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void registerSchool(String code, String name, String schema) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, "Pune", "Maharashtra"));
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
        jdbc.sql("delete from " + SCHEMA + ".fee_installment").update();
        jdbc.sql("delete from " + SCHEMA + ".fee_structure_item").update();
        jdbc.sql("delete from " + SCHEMA + ".fee_structure").update();
        jdbc.sql("delete from " + SCHEMA + ".fee_concession_type").update();
        jdbc.sql("delete from " + SCHEMA + ".fee_head").update();
        jdbc.sql("delete from " + SCHEMA + ".section").update();
        jdbc.sql("delete from " + SCHEMA + ".school_class").update();
        jdbc.sql("delete from " + SCHEMA + ".academic_session").update();
        jdbc.sql("delete from " + SCHEMA + ".audit_event").update();
        jdbc.sql("delete from " + SCHEMA + ".user_account").update();
        jdbc.sql("delete from " + SCHEMA + ".role").update();
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
        provisioning.provision(SCHEMA);
    }
}
