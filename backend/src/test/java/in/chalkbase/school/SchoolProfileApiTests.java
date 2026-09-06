package in.chalkbase.school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * The school profile: the first screen where a school edits its own master data.
 *
 * <p>Two schools throughout, because the claim being tested is not "a profile can be saved" — it is
 * that a profile belongs to <em>one</em> school and that the schema boundary is what makes that
 * true. A single-tenant version of this file would pass with the tenancy removed.
 *
 * <p>Deliberately not {@code @Transactional}: the singleton constraint and the check constraints
 * are only enforced when a statement reaches the database, so a rolled-back test would report
 * success for writes production would refuse. These commit and clean up after themselves.
 *
 * <p>Every fixture is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SchoolProfileApiTests {

    private static final String EVERGREEN_SCHEMA = "evergreen";
    private static final String EVERGREEN_CODE = "EVG-101";
    private static final String LAKESIDE_SCHEMA = "lakeside";
    private static final String LAKESIDE_CODE = "LKS-202";

    private static final String PASSWORD = "Evergreen#2026";

    private static final String SCHOOL_READ = "school:school:read";
    private static final String SCHOOL_UPDATE = "school:school:update";

    private static final String PROFILE = "/api/school/profile";

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
        registerSchool(EVERGREEN_CODE, "Evergreen Public School", EVERGREEN_SCHEMA, "Pune", "Maharashtra");
        registerSchool(LAKESIDE_CODE, "Lakeside Academy", LAKESIDE_SCHEMA, "Kochi", "Kerala");
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── Reading ──────────────────────────────────────────────────────────────────────────────

    /**
     * A school that has never saved a profile has no row, and that is not an error. The registry
     * already knows its name, code, board and town; the answer is those, with the rest empty.
     */
    @Test
    void answersWithTheRegistrysOwnDetailsWhenNoProfileHasBeenSavedYet() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(get(PROFILE).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.code").value(EVERGREEN_CODE))
                .andExpect(jsonPath("$.data.name").value("Evergreen Public School"))
                .andExpect(jsonPath("$.data.board").value("CBSE"))
                .andExpect(jsonPath("$.data.city").value("Pune"))
                // Absent rather than null: the envelope drops nulls (ADR-0007).
                .andExpect(jsonPath("$.data.addressLine1").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    // ── Round trip ───────────────────────────────────────────────────────────────────────────

    @Test
    void savesAProfileAndReadsItBack() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.city").value("Pune"))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mockMvc.perform(get(PROFILE).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.addressLine1").value("Plot 14, Baner Road"))
                .andExpect(jsonPath("$.data.city").value("Pune"))
                .andExpect(jsonPath("$.data.pincode").value("411045"))
                .andExpect(jsonPath("$.data.principalName").value("Meera Iyer"))
                .andExpect(jsonPath("$.data.email").value("office@evergreen.example"))
                .andExpect(jsonPath("$.data.affiliationNumber").value("1130456"))
                .andExpect(jsonPath("$.data.board").value("CISCE"));
    }

    // ── The audit trail ──────────────────────────────────────────────────────────────────────

    /**
     * The first save is a creation, and it records the fields that were filled in — by name.
     *
     * <p>This endpoint is the first thing in the system that changes anything, so it is the first
     * proof that the audit log has a producer rather than only a table.
     */
    @Test
    void recordsTheFirstSaveAsACreationWithFieldNamesAndNoValues() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));

        Map<String, Object> row = latestAudit(EVERGREEN_SCHEMA);
        assertThat(row.get("action")).isEqualTo("ENTITY_CREATED");
        assertThat(row.get("entity_type")).isEqualTo("SCHOOL_PROFILE");
        assertThat(row.get("entity_id")).isEqualTo(EVERGREEN_CODE);
        assertThat(row.get("actor_name")).isEqualTo("Ravi Deshpande");

        String fields = String.valueOf(row.get("changed_fields"));
        assertThat(fields.split(",")).contains("city", "pincode", "principalName", "email", "website");
        // The point of ADR-0014, asserted structurally rather than by reading it: none of the
        // values that were just saved appear anywhere in the row.
        assertThat(fields).doesNotContain("Pune").doesNotContain("411045").doesNotContain("Meera Iyer");
        assertThat(fields).doesNotContain("=").doesNotContain(":").doesNotContain("->");
    }

    /** A second save records only what actually differs, not every field on the form. */
    @Test
    void recordsOnlyTheFieldsASubsequentSaveActuallyChanges() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));
        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pimpri", "411018"));

        Map<String, Object> row = latestAudit(EVERGREEN_SCHEMA);
        assertThat(row.get("action")).isEqualTo("ENTITY_UPDATED");
        assertThat(String.valueOf(row.get("changed_fields")).split(",")).containsExactlyInAnyOrder("city", "pincode");
    }

    /**
     * Resubmitting an unchanged form records nothing.
     *
     * <p>An audit log has to stay worth reading, and a screen that writes a row every time someone
     * presses Save without editing anything is the commonest way to make one that is not.
     */
    @Test
    void recordsNothingWhenASaveChangesNothing() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);
        String body = profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045");

        save(session, body);
        int afterFirst = auditCount(EVERGREEN_SCHEMA);
        save(session, body);

        assertThat(auditCount(EVERGREEN_SCHEMA)).isEqualTo(afterFirst);
    }

    /**
     * A rejected save leaves no audit row.
     *
     * <p>ADR-0018 §3: a data-change audit joins the caller's transaction precisely so that a change
     * which did not happen cannot be claimed to have happened. Here the refusal comes before the
     * write, which is the same guarantee arriving earlier.
     */
    @Test
    void recordsNothingWhenTheSaveIsRefused() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);
        int before = auditCount(EVERGREEN_SCHEMA);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("EVG-999", "Evergreen Public School", "Pune", "411045")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(auditCount(EVERGREEN_SCHEMA)).isEqualTo(before);
    }

    /** A second save replaces the row rather than adding one. The schema would refuse a second. */
    @Test
    void savingTwiceLeavesExactlyOneProfileRow() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));
        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pimpri", "411018"));

        assertThat(profileCount(EVERGREEN_SCHEMA)).isEqualTo(1);
        mockMvc.perform(get(PROFILE).cookie(session))
                .andExpect(jsonPath("$.data.city").value("Pimpri"));
    }

    /**
     * The registry keeps its own copy of the name, board and town so the platform can list schools
     * without binding a tenant. Saving the profile has to keep that copy honest, or the school
     * register quietly disagrees with the school.
     */
    @Test
    void keepsTheRegistrysCopyOfTheDisplayDetailsInStep() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School, Baner", "Pimpri", "411018"));

        School registry = schools.findBySchemaName(EVERGREEN_SCHEMA).orElseThrow();
        assertThat(registry.getName()).isEqualTo("Evergreen Public School, Baner");
        assertThat(registry.getCity()).isEqualTo("Pimpri");
        assertThat(registry.getBoard()).isEqualTo(Board.CISCE);
        // And the parts that address the tenant are untouched.
        assertThat(registry.getCode()).isEqualTo(EVERGREEN_CODE);
        assertThat(registry.getSchemaName()).isEqualTo(EVERGREEN_SCHEMA);
    }

    // ── Tenancy ──────────────────────────────────────────────────────────────────────────────

    /**
     * The test that makes the placement of {@code school_profile} mean something. Each school's
     * profile lives in its own schema, so neither request can even name the other's row.
     */
    @Test
    void eachSchoolHasItsOwnProfileAndSeesOnlyThat() throws Exception {
        Cookie evergreen = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);
        Cookie lakeside = signInAsAdministrator(LAKESIDE_SCHEMA, LAKESIDE_CODE);

        save(evergreen, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));
        save(lakeside, profileBody(LAKESIDE_CODE, "Lakeside Academy", "Kochi", "682016"));

        mockMvc.perform(get(PROFILE).cookie(evergreen))
                .andExpect(jsonPath("$.data.code").value(EVERGREEN_CODE))
                .andExpect(jsonPath("$.data.city").value("Pune"))
                .andExpect(jsonPath("$.data.pincode").value("411045"));

        mockMvc.perform(get(PROFILE).cookie(lakeside))
                .andExpect(jsonPath("$.data.code").value(LAKESIDE_CODE))
                .andExpect(jsonPath("$.data.city").value("Kochi"))
                .andExpect(jsonPath("$.data.pincode").value("682016"));

        assertThat(profileCount(EVERGREEN_SCHEMA)).isEqualTo(1);
        assertThat(profileCount(LAKESIDE_SCHEMA)).isEqualTo(1);
    }

    /** One school editing its profile must not touch another's, even to the same value. */
    @Test
    void editingOneSchoolsProfileLeavesTheOthersAlone() throws Exception {
        Cookie evergreen = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);
        Cookie lakeside = signInAsAdministrator(LAKESIDE_SCHEMA, LAKESIDE_CODE);

        save(evergreen, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));
        save(lakeside, profileBody(LAKESIDE_CODE, "Lakeside Academy", "Kochi", "682016"));
        save(evergreen, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Nashik", "422009"));

        mockMvc.perform(get(PROFILE).cookie(lakeside))
                .andExpect(jsonPath("$.data.city").value("Kochi"))
                .andExpect(jsonPath("$.data.pincode").value("682016"));
    }

    // ── What cannot be changed ───────────────────────────────────────────────────────────────

    @Test
    void refusesAnAttemptToChangeTheSchoolCode() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("EVG-999", "Evergreen Public School", "Pune", "411045")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SCHOOL_002"))
                .andExpect(jsonPath("$.error.details.code").exists())
                .andExpect(jsonPath("$.data").doesNotExist());

        // Refused, not partly applied: nothing was written.
        assertThat(schools.findBySchemaName(EVERGREEN_SCHEMA).orElseThrow().getCode())
                .isEqualTo(EVERGREEN_CODE);
        assertThat(profileCount(EVERGREEN_SCHEMA)).isZero();
    }

    @Test
    void refusesAnAttemptToChangeTheSchemaName() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045")
                                .replace(
                                        "\"schemaName\": \"" + EVERGREEN_SCHEMA + "\"",
                                        "\"schemaName\": \"lakeside\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SCHOOL_002"))
                .andExpect(jsonPath("$.error.details.schemaName").exists());

        assertThat(schools.findBySchemaName(EVERGREEN_SCHEMA)).isPresent();
        assertThat(profileCount(LAKESIDE_SCHEMA)).isZero();
    }

    /** Sending them back unchanged is the normal case and must not be mistaken for an edit. */
    @Test
    void acceptsTheCodeAndSchemaNameSentBackUnchanged() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        save(session, profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045"));

        assertThat(profileCount(EVERGREEN_SCHEMA)).isEqualTo(1);
    }

    // ── Validation ───────────────────────────────────────────────────────────────────────────

    @Test
    void namesTheFieldThatFailedValidation() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "011045")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.pincode").exists());

        assertThat(profileCount(EVERGREEN_SCHEMA)).isZero();
    }

    @Test
    void refusesAProfileWithNoAddressOrPrincipal() throws Exception {
        Cookie session = signInAsAdministrator(EVERGREEN_SCHEMA, EVERGREEN_CODE);

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evergreen Public School",
                                  "board": "CBSE",
                                  "addressLine1": "",
                                  "city": "",
                                  "state": "Maharashtra",
                                  "pincode": "411045",
                                  "principalName": "",
                                  "phone": "+91 20 2721 0000",
                                  "email": "not-an-address"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_001"))
                .andExpect(jsonPath("$.error.details.addressLine1").exists())
                .andExpect(jsonPath("$.error.details.city").exists())
                .andExpect(jsonPath("$.error.details.principalName").exists())
                .andExpect(jsonPath("$.error.details.email").exists());
    }

    // ── Authorization ────────────────────────────────────────────────────────────────────────

    @Test
    void refusesToShowTheProfileToSomeoneWithoutSchoolRead() throws Exception {
        createAccount(EVERGREEN_SCHEMA, "parent", "Suresh Pillai");
        grantRoleWithPermissions(EVERGREEN_SCHEMA, "parent", "PARENT");
        Cookie session = signIn(EVERGREEN_CODE, "parent");

        mockMvc.perform(get(PROFILE).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * Reading and editing are separate permissions, so holding only {@code school:school:read} must
     * not be enough to save. This is the case a single "school" permission would have got wrong.
     */
    @Test
    void refusesToSaveForSomeoneWhoMayOnlyRead() throws Exception {
        createAccount(EVERGREEN_SCHEMA, "librarian", "Farida Khan");
        grantRoleWithPermissions(EVERGREEN_SCHEMA, "librarian", "LIBRARIAN");
        Cookie session = signIn(EVERGREEN_CODE, "librarian");

        mockMvc.perform(get(PROFILE).cookie(session)).andExpect(status().isOk());

        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        assertThat(profileCount(EVERGREEN_SCHEMA)).isZero();
    }

    @Test
    void refusesBothEndpointsWithoutASessionAtAll() throws Exception {
        mockMvc.perform(get(PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        mockMvc.perform(put(PROFILE)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody(EVERGREEN_CODE, "Evergreen Public School", "Pune", "411045")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    /** The whole form, every time. `board` is CISCE so a change from the registry's CBSE shows. */
    private Map<String, Object> latestAudit(String schema) {
        return jdbc.sql("select action, entity_type, entity_id, actor_name, changed_fields from " + schema
                        + ".audit_event where entity_type = 'SCHOOL_PROFILE' order by occurred_at desc limit 1")
                .query()
                .singleRow();
    }

    private int auditCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".audit_event where entity_type = 'SCHOOL_PROFILE'")
                .query(Integer.class)
                .single();
    }

    private static String profileBody(String code, String name, String city, String pincode) {
        return """
                {
                  "code": "%s",
                  "schemaName": "%s",
                  "name": "%s",
                  "board": "CISCE",
                  "addressLine1": "Plot 14, Baner Road",
                  "addressLine2": "Near the water tower",
                  "city": "%s",
                  "state": "Maharashtra",
                  "pincode": "%s",
                  "principalName": "Meera Iyer",
                  "phone": "+91 20 2721 0000",
                  "email": "office@evergreen.example",
                  "website": "https://evergreen.example",
                  "affiliationNumber": "1130456"
                }
                """.formatted(code, schemaFor(code), name, city, pincode);
    }

    private static String schemaFor(String code) {
        return LAKESIDE_CODE.equals(code) ? LAKESIDE_SCHEMA : EVERGREEN_SCHEMA;
    }

    private void save(Cookie session, String body) throws Exception {
        mockMvc.perform(put(PROFILE)
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private int profileCount(String schema) {
        return jdbc.sql("select count(*) from " + schema + ".school_profile")
                .query(Integer.class)
                .single();
    }

    /**
     * Someone who may both read and edit.
     *
     * <p>The permission is added to that school's own copy of the principal role rather than
     * granted through a shipped template, because no template holds {@code school:school:update}
     * yet — {@code RoleTemplates} belongs to identity and the grant lands there. Editing a school's
     * own role is exactly what a school would do in the meantime, so this is a real path.
     */
    private Cookie signInAsAdministrator(String schema, String schoolCode) throws Exception {
        String username = "principal-" + schema;
        createAccount(schema, username, "Ravi Deshpande");
        grantRoleWithPermissions(schema, username, "PRINCIPAL");
        Cookie session = signIn(schoolCode, username);
        // Not granted here on purpose. The shipped PRINCIPAL template holds school:school:update,
        // and adding it to the school's own copy first would make this assertion pass whether that
        // stayed true or not.
        assertThat(permissionsOf(schema, "PRINCIPAL")).contains(SCHOOL_READ, SCHOOL_UPDATE);
        return session;
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

    private void registerSchool(String code, String name, String schema, String city, String state) {
        provisioning.provision(schema);
        schools.save(new School(code, name, schema, Board.CBSE, city, state));
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

    private void grantRoleWithPermissions(String schema, String username, String roleCode) {
        jdbc.sql("insert into " + schema + ".user_role_grant (id, user_account_id, role_id, scope_type)"
                        + " values (?, (select user_account_id from " + schema
                        + ".user_identifier where type = 'USERNAME' and value = ?), ?, 'SCHOOL')")
                .params(UUID.randomUUID(), username, roleId(schema, roleCode))
                .update();
    }

    private UUID roleId(String schema, String code) {
        return jdbc.sql("select id from " + schema + ".role where code = ?")
                .param(code)
                .query(UUID.class)
                .single();
    }

    private List<String> permissionsOf(String schema, String roleCode) {
        return jdbc.sql("select permission_code from " + schema + ".role_permission rp join " + schema
                        + ".role r on r.id = rp.role_id where r.code = ? order by permission_code")
                .param(roleCode)
                .query(String.class)
                .list();
    }

    private void addPermission(String schema, String roleCode, String permission) {
        jdbc.sql("insert into " + schema + ".role_permission (role_id, permission_code) values (?, ?)"
                        + " on conflict do nothing")
                .params(roleId(schema, roleCode), permission)
                .update();
    }

    /** Roles are reinstalled because these tests edit one school's copy of a shipped template. */
    private void reset() {
        for (String schema : List.of(EVERGREEN_SCHEMA, LAKESIDE_SCHEMA)) {
            provisioning.provision(schema);
            jdbc.sql("delete from " + schema + ".school_profile").update();
            jdbc.sql("delete from " + schema + ".user_account").update();
            jdbc.sql("delete from " + schema + ".role").update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
        for (String schema : List.of(EVERGREEN_SCHEMA, LAKESIDE_SCHEMA)) {
            provisioning.provision(schema);
        }
    }
}
