package in.chalkbase.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Reading the audit log over HTTP (ADR-0018 §6).
 *
 * <p>Two schools, because a tenant-scoped module needs a negative test more than it needs a happy
 * path: the interesting assertion is the one where an auditor at one school reads the other's log
 * and sees nothing.
 *
 * <p>Deliberately not {@code @Transactional}: permissions are resolved at login from committed
 * rows, and audit events are committed in their own transaction.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuditApiTests {

    private static final String WILLOWBANK_SCHEMA = "willowbank";
    private static final String WILLOWBANK_CODE = "WLB-707";
    private static final String CEDARHILL_SCHEMA = "cedarhill";
    private static final String CEDARHILL_CODE = "CDH-808";

    private static final String PASSWORD = "Willowbank#2026";

    /** Fixed so the range filter has something predictable to sit either side of. */
    private static final Instant NOON = Instant.parse("2026-09-01T12:00:00Z");

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

    @Autowired
    PermissionCatalog catalog;

    @BeforeEach
    void onboardTwoSchools() {
        reset();
        registerSchool(WILLOWBANK_CODE, "Willowbank International", WILLOWBANK_SCHEMA);
        registerSchool(CEDARHILL_CODE, "Cedarhill Academy", CEDARHILL_SCHEMA);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── The permission ───────────────────────────────────────────────────────────────────────

    /** Declared through the same SPI every other module uses, so it exists everywhere or nowhere. */
    @Test
    void theCatalogueDeclaresTheAuditReadPermission() {
        assertThat(catalog.contains(AuditPermissions.AUDIT_READ)).isTrue();
        assertThat(catalog.require(AuditPermissions.AUDIT_READ).module()).isEqualTo("platform");
    }

    /**
     * The auditor template shipped holding nothing at all — honest, and useless. This is its first
     * permission, and no other shipped template holds it: reading who did what to which record is
     * oversight, not a convenience.
     */
    @Test
    void onlyTheAuditorTemplateStartsWithIt() {
        List<String> rolesHolding = jdbc.sql("select r.code from " + WILLOWBANK_SCHEMA + ".role r"
                        + " join " + WILLOWBANK_SCHEMA + ".role_permission rp on rp.role_id = r.id"
                        + " where rp.permission_code = ? order by r.code")
                .param(AuditPermissions.AUDIT_READ)
                .query(String.class)
                .list();

        assertThat(rolesHolding).containsExactly("AUDITOR");
    }

    // ── Enforcement ──────────────────────────────────────────────────────────────────────────

    @Test
    void refusesTheAuditLogToSomeoneWithoutThePermission() throws Exception {
        UUID librarian = createAccount(WILLOWBANK_SCHEMA, "librarian", "Farida Khan");
        grant(WILLOWBANK_SCHEMA, librarian, "LIBRARIAN");

        mockMvc.perform(get("/api/audit").cookie(signIn(WILLOWBANK_CODE, "librarian")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PERM_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void anUnauthenticatedRequestNeverReachesTheEndpoint() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    /**
     * One 403 produces one {@code PERMISSION_DENIED} row. {@code SecurityErrorResponder} is reached
     * once per request by {@code ExceptionTranslationFilter}, which is why the denial is audited
     * there rather than in a filter or an authorization manager that the chain re-evaluates.
     */
    @Test
    void aForbiddenRequestIsAuditedExactlyOnce() throws Exception {
        UUID librarian = createAccount(WILLOWBANK_SCHEMA, "librarian", "Farida Khan");
        grant(WILLOWBANK_SCHEMA, librarian, "LIBRARIAN");
        Cookie session = signIn(WILLOWBANK_CODE, "librarian");
        jdbc.sql("delete from " + WILLOWBANK_SCHEMA + ".audit_event").update();

        mockMvc.perform(get("/api/audit").cookie(session)).andExpect(status().isForbidden());

        List<String> denials = jdbc.sql("select entity_id from " + WILLOWBANK_SCHEMA + ".audit_event"
                        + " where action = ? and outcome = 'DENIED'")
                .param(AuditAction.PERMISSION_DENIED)
                .query(String.class)
                .list();

        assertThat(denials).containsExactly("GET /api/audit");
        assertThat(jdbc.sql("select actor_id from " + WILLOWBANK_SCHEMA + ".audit_event where action = ?")
                        .param(AuditAction.PERMISSION_DENIED)
                        .query(UUID.class)
                        .single())
                .as("a denial names who was refused")
                .isEqualTo(librarian);
    }

    // ── Reading ──────────────────────────────────────────────────────────────────────────────

    @Test
    void returnsThePageNewestFirstInsideTheEnvelope() throws Exception {
        seed(WILLOWBANK_SCHEMA, 3, AuditAction.ENTITY_UPDATED, null);
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(get("/api/audit").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").exists())
                // The sign-in that produced this session is audited too, so there are four rows.
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value(AuditAction.LOGIN_SUCCEEDED))
                .andExpect(jsonPath("$.data.content[1].entityId").value("student-2"));
    }

    @Test
    void pagesThroughTheLog() throws Exception {
        seed(WILLOWBANK_SCHEMA, 30, AuditAction.ENTITY_UPDATED, null);
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(get("/api/audit").param("page", "0").param("size", "10").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(31))
                .andExpect(jsonPath("$.data.totalPages").value(4));

        // The last page is short, which is the whole reason `size` is what was asked for rather
        // than what came back.
        mockMvc.perform(get("/api/audit").param("page", "3").param("size", "10").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    void sortsTheOtherWayWhenAsked() throws Exception {
        seed(WILLOWBANK_SCHEMA, 3, AuditAction.ENTITY_UPDATED, null);
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(get("/api/audit")
                        .param("sort", "occurredAt,asc")
                        .param("action", AuditAction.ENTITY_UPDATED)
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].entityId").value("student-0"));
    }

    /**
     * A caller's typo in {@code ?sort=} is a 400, not a 500.
     *
     * <p>Spring Data only resolves a sort property when the query runs, so an unknown one surfaces
     * as a repository failure rather than as parameter binding — which reaches the catch-all
     * handler and is answered "something went wrong at our end" unless something says otherwise.
     * Nothing went wrong at our end.
     */
    @Test
    void answersAnUnknownSortPropertyWithA400AndNotAServerError() throws Exception {
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/audit").param("sort", "notAField,desc").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("notAField")))
                .andReturn()
                .getResponse();

        // The property list Spring puts in the exception message is a description of the entity.
        // Echoing it would hand anyone who can call a list endpoint a free schema dump.
        assertThat(response.getContentAsString()).doesNotContain("actorName").doesNotContain("ipAddress");
    }

    /**
     * {@code ?size=5000} does not return five thousand rows.
     *
     * <p>Spring's own ceiling is 2000, which nobody chose; {@code application.yml} lowers it to
     * 100. Asserted here because it is a property in a YAML file that no code refers to — the kind
     * of setting that is silently lost in a merge and noticed when a school's whole audit log comes
     * back in one response.
     */
    @Test
    void capsThePageSizeHoweverLargeAnOneIsAskedFor() throws Exception {
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(get("/api/audit").param("size", "5000").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // ── Filtering ────────────────────────────────────────────────────────────────────────────

    @Test
    void filtersByActionByActorAndByDateRange() throws Exception {
        UUID someoneElse = UUID.randomUUID();
        seed(WILLOWBANK_SCHEMA, 3, AuditAction.ENTITY_UPDATED, null);
        seed(WILLOWBANK_SCHEMA, 2, AuditAction.ENTITY_DELETED, someoneElse);
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(get("/api/audit")
                        .param("action", AuditAction.ENTITY_DELETED)
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/audit")
                        .param("actorId", someoneElse.toString())
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // `from` is inclusive and `to` exclusive, so consecutive ranges neither overlap nor skip.
        mockMvc.perform(get("/api/audit")
                        .param("from", NOON.toString())
                        .param("to", NOON.plus(2, ChronoUnit.MINUTES).toString())
                        .cookie(session))
                .andExpect(status().isOk())
                // The five seeded rows, and not the sign-in, which happened now rather than then.
                .andExpect(jsonPath("$.data.totalElements").value(5));

        mockMvc.perform(get("/api/audit")
                        .param("from", NOON.plus(1, ChronoUnit.HOURS).toString())
                        .cookie(session))
                .andExpect(status().isOk())
                // Only the login, which happened now rather than at the seeded instant.
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /** The audit log is the school's record of itself, and the schema is the boundary (ADR-0011). */
    @Test
    void anAuditorAtOneSchoolCannotSeeAnothersLog() throws Exception {
        seed(WILLOWBANK_SCHEMA, 5, AuditAction.ENTITY_UPDATED, null);
        Cookie cedarhill = signInAsAuditor(CEDARHILL_SCHEMA, CEDARHILL_CODE);

        mockMvc.perform(get("/api/audit")
                        .param("action", AuditAction.ENTITY_UPDATED)
                        .cookie(cedarhill))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    // ── There is no write API ────────────────────────────────────────────────────────────────

    /**
     * ADR-0018 §6: append-only in the application. No update, no delete, no administrative "clear
     * the log". A log an administrator can edit says whatever the last person embarrassed by it
     * wanted it to say, and a retention purge is a scheduled platform job rather than an endpoint.
     */
    @Test
    void offersNoWayToWriteOrDeleteAnAuditRow() throws Exception {
        Cookie session = signInAsAuditor(WILLOWBANK_SCHEMA, WILLOWBANK_CODE);

        mockMvc.perform(post("/api/audit")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/audit").cookie(session).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    /** Rows written straight to the table, so the read tests do not depend on what emits them. */
    private void seed(String schema, int count, String action, UUID actorId) {
        for (int index = 0; index < count; index++) {
            jdbc.sql("insert into " + schema + ".audit_event (id, occurred_at, actor_id, actor_name, actor_roles,"
                            + " action, entity_type, entity_id, changed_fields, outcome, ip_address, user_agent,"
                            + " trace_id) values (?, ?, ?, 'Sanjay Bhatt', 'AUDITOR', ?, 'STUDENT', ?, 'section',"
                            + " 'SUCCESS', '203.0.113.9', 'test-agent', ?)")
                    .params(
                            UUID.randomUUID(),
                            OffsetDateTime.ofInstant(NOON.plus(index, ChronoUnit.SECONDS), ZoneOffset.UTC),
                            actorId,
                            action,
                            "student-" + index,
                            UUID.randomUUID().toString())
                    .update();
        }
    }

    private Cookie signInAsAuditor(String schema, String code) throws Exception {
        UUID auditor = createAccount(schema, "auditor", "Sanjay Bhatt");
        grant(schema, auditor, "AUDITOR");
        return signIn(code, "auditor");
    }

    private RequestBuilder login(String schoolCode, String username) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"schoolCode": "%s", "username": "%s", "password": "%s"}
                        """.formatted(
                        schoolCode, username, PASSWORD));
    }

    private Cookie signIn(String schoolCode, String username) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(login(schoolCode, username))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("SESSION");
        assertThat(session).as("session cookie issued by login").isNotNull();
        return session;
    }

    private void registerSchool(String code, String name, String schema) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, "Shimla", "Himachal Pradesh"));
    }

    private UUID createAccount(String schema, String username, String displayName) {
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
        return accountId;
    }

    private void grant(String schema, UUID accountId, String roleCode) {
        UUID roleId = jdbc.sql("select id from " + schema + ".role where code = ?")
                .param(roleCode)
                .query(UUID.class)
                .single();
        jdbc.sql("insert into " + schema
                        + ".user_role_grant (id, user_account_id, role_id, scope_type) values (?, ?, ?, 'SCHOOL')")
                .params(UUID.randomUUID(), accountId, roleId)
                .update();
    }

    private void reset() {
        for (String schema : List.of(WILLOWBANK_SCHEMA, CEDARHILL_SCHEMA)) {
            provisioning.provision(schema);
            jdbc.sql("delete from " + schema + ".audit_event").update();
            jdbc.sql("delete from " + schema + ".user_account").update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
