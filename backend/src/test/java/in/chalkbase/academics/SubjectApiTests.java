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
 * The subject catalogue: the last piece of Phase 1 master data (docs/status.md).
 *
 * <p>Sibling of {@code AcademicsApiTests}, kept in its own file rather than folded into it because
 * this endpoint is paged and the others are not — the fixtures that matter here (a second and third
 * page, a search) have no equivalent over there.
 *
 * <p>Two schools throughout, for the same reason {@code AcademicsApiTests} uses two: the claim being
 * tested is not "a subject can be saved", it is that a catalogue belongs to <em>one</em> school.
 *
 * <p>Deliberately not {@code @Transactional} — the audit row joins the caller's transaction
 * (ADR-0018 §3), so a rolled-back test would report success for a write production refuses. These
 * commit and clean up after themselves.
 *
 * <p>Every fixture is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SubjectApiTests {

    private static final String RIVERBANK_SCHEMA = "riverbank_subjects";
    private static final String RIVERBANK_CODE = "RVB-909";
    private static final String CLOVERDALE_SCHEMA = "cloverdale_subjects";
    private static final String CLOVERDALE_CODE = "CLV-910";

    private static final String PASSWORD = "Riverbank#2026";

    private static final String SUBJECTS = "/api/academics/subjects";

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
    void createsASubjectAndListsItByName() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(SUBJECTS), session, """
                        {"name": "Mathematics", "code": "MATH"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Mathematics"))
                .andExpect(jsonPath("$.data.code").value("MATH"))
                .andExpect(jsonPath("$.data.active").value(true));

        createSubject(session, "English", "ENG");

        mockMvc.perform(get(SUBJECTS).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                // Alphabetical, the default sort: English before Mathematics.
                .andExpect(jsonPath("$.data.content[0].name").value("English"))
                .andExpect(jsonPath("$.data.content[1].name").value("Mathematics"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void pagesTheCatalogue() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSubject(session, "Art", "ART");
        createSubject(session, "Biology", "BIO");
        createSubject(session, "Chemistry", "CHEM");

        mockMvc.perform(get(SUBJECTS + "?page=0&size=2").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get(SUBJECTS + "?page=1&size=2").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Chemistry"));
    }

    @Test
    void searchesByNameOrCode() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSubject(session, "Mathematics", "MATH");
        createSubject(session, "English", "ENG");

        mockMvc.perform(get(SUBJECTS + "?q=math").cookie(session))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Mathematics"));

        mockMvc.perform(get(SUBJECTS + "?q=ENG").cookie(session))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("ENG"));
    }

    /**
     * A retired subject stays in the answer, flagged — the same shape as a retired class
     * (ADR-0019), and the reason is the same: there is no DELETE to reach for instead.
     */
    @Test
    void deactivatesASubjectAndStillReturnsItFlagged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSubject(session, "Sanskrit", "SANS");

        mockMvc.perform(request(put(SUBJECTS + "/" + id), session, """
                        {"name": "Sanskrit", "code": "SANS", "active": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get(SUBJECTS).cookie(session))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].active").value(false));

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, id);
        assertThat(row.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(row.get("entity_type")).isEqualTo("SUBJECT");
        assertThat(row.get("changed_fields")).isEqualTo("active");
    }

    @Test
    void recordsOnlyWhatActuallyChanged() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSubject(session, "History", "HIST");
        int afterCreate = auditCount(RIVERBANK_SCHEMA);

        mockMvc.perform(request(put(SUBJECTS + "/" + id), session, """
                        {"name": "History", "code": "HIST", "active": true}
                        """)).andExpect(status().isOk());

        assertThat(auditCount(RIVERBANK_SCHEMA)).isEqualTo(afterCreate);
    }

    @Test
    void recordsACreatedSubjectByFieldNameAndNeverByValue() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSubject(session, "Geography", "GEO");

        Map<String, Object> row = latestAuditFor(RIVERBANK_SCHEMA, id);
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        String fields = String.valueOf(row.get("changed_fields"));
        assertThat(fields.split(",")).containsExactlyInAnyOrder("name", "code");
        assertThat(fields).doesNotContain("Geography").doesNotContain("GEO");
    }

    @Test
    void refusesASecondSubjectWithTheSameName() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSubject(session, "Physics", "PHY");

        mockMvc.perform(request(post(SUBJECTS), session, """
                        {"name": "Physics", "code": "PHY2"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACAD_008"));
    }

    @Test
    void refusesASecondSubjectWithTheSameCode() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        createSubject(session, "Physics", "PHY");

        mockMvc.perform(request(post(SUBJECTS), session, """
                        {"name": "Physical Education", "code": "PHY"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACAD_009"));
    }

    /** {@code active} is boxed so that omitting it is a validation failure, not a silent retirement. */
    @Test
    void refusesASubjectEditThatOmitsWhetherItIsActive() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID id = createSubject(session, "Computer Science", "CS");

        mockMvc.perform(request(put(SUBJECTS + "/" + id), session, """
                        {"name": "Computer Science", "code": "CS"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.active").exists());

        assertThat(activeFlagOf(RIVERBANK_SCHEMA, "Computer Science")).isTrue();
    }

    @Test
    void refusesASubjectWithNoNameOrNoCode() throws Exception {
        Cookie session = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");

        mockMvc.perform(request(post(SUBJECTS), session, """
                        {"name": "", "code": ""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.name").exists())
                .andExpect(jsonPath("$.error.details.code").exists());
    }

    /** Each school has its own catalogue, and the same name and code in both is not a clash. */
    @Test
    void twoSchoolsCataloguesAreInvisibleToEachOther() throws Exception {
        Cookie riverbank = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        Cookie cloverdale = signInAs(CLOVERDALE_SCHEMA, CLOVERDALE_CODE, "PRINCIPAL");

        UUID theirs = createSubject(cloverdale, "Music", "MUS");
        createSubject(riverbank, "Music", "MUS");

        mockMvc.perform(get(SUBJECTS).cookie(riverbank))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mockMvc.perform(get(SUBJECTS).cookie(cloverdale))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // An edit aimed at the other school's subject is a 404, not a leak.
        mockMvc.perform(request(put(SUBJECTS + "/" + theirs), riverbank, """
                        {"name": "Renamed", "code": "MUS", "active": true}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NF_001"));
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    @Test
    void refusesEveryWriteToSomeoneWhoMayOnlyRead() throws Exception {
        Cookie principal = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PRINCIPAL");
        UUID subjectId = createSubject(principal, "Economics", "ECO");

        Cookie teacher = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "CLASS_TEACHER");

        mockMvc.perform(get(SUBJECTS).cookie(teacher)).andExpect(status().isOk());

        for (RequestBuilder write : writes(teacher, subjectId)) {
            mockMvc.perform(write)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("PERM_001"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        assertThat(subjectNames(RIVERBANK_SCHEMA)).containsExactly("Economics");
    }

    @Test
    void refusesEvenTheListToSomeoneWithNoAcademicsPermission() throws Exception {
        Cookie parent = signInAs(RIVERBANK_SCHEMA, RIVERBANK_CODE, "PARENT");

        mockMvc.perform(get(SUBJECTS).cookie(parent))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void refusesEveryEndpointWithoutASessionAtAll() throws Exception {
        UUID anyId = UUID.randomUUID();

        mockMvc.perform(get(SUBJECTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        for (RequestBuilder write : writes(null, anyId)) {
            mockMvc.perform(write)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        }
    }

    private List<RequestBuilder> writes(Cookie session, UUID subjectId) {
        return List.of(request(post(SUBJECTS), session, """
                        {"name": "Whatever", "code": "WHT"}
                        """), request(put(SUBJECTS + "/" + subjectId), session, """
                        {"name": "Whatever", "code": "WHT", "active": false}
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

    private UUID createSubject(Cookie session, String name, String code) throws Exception {
        return idOf(mockMvc.perform(request(post(SUBJECTS), session, """
                        {"name": "%s", "code": "%s"}
                        """.formatted(name, code)))
                .andExpect(status().isCreated()));
    }

    private static UUID idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        JsonNode body = JSON.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.path("data").path("id").asText());
    }

    // ── reading the database directly ────────────────────────────────────────────────────────

    private List<String> subjectNames(String schema) {
        return jdbc.sql("select name from " + schema + ".subject order by name")
                .query(String.class)
                .list();
    }

    private boolean activeFlagOf(String schema, String name) {
        return jdbc.sql("select active from " + schema + ".subject where name = ?")
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

    /** Only this module's rows: sign-ins write their own, and they are not what these tests count. */
    private int auditCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_type = 'SUBJECT'")
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
            jdbc.sql("delete from " + schema + ".subject").update();
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
