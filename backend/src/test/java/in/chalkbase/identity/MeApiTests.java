package in.chalkbase.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.navigation.NavigationCatalog;
import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.tenancy.SchoolProvisioning;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.infrastructure.SchoolRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bootstrap call (ADR-0008): one request that tells a reloading client who it is, what it may
 * do, and what to put in the menu.
 *
 * <p>The tests that matter here are the negative ones. A navigation tree is only correct if two
 * users with different roles get different trees, and if the smaller tree contains nothing its
 * owner cannot use — so the fixture is a principal, an auditor and a parent at the same school,
 * holding three genuinely different permission sets from the shipped templates.
 *
 * <p>Deliberately not {@code @Transactional}: the effective permission set is resolved at login
 * from committed rows, so a rolled-back grant would be a grant this test invented.
 *
 * <p>Every fixture here is an invented school and an invented person. Never real student data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MeApiTests {

    private static final String ORCHARD_SCHEMA = "orchard";
    private static final String ORCHARD_CODE = "ORC-505";
    private static final String ORCHARD_NAME = "Orchard Valley School";
    private static final String MEADOW_SCHEMA = "meadow";
    private static final String MEADOW_CODE = "MDW-606";
    private static final String MEADOW_NAME = "Meadowlands Vidyalaya";

    private static final String PASSWORD = "Orchard#2026";

    private static final String SCHOOL_READ = "school:school:read";
    private static final String SCHOOL_UPDATE = "school:school:update";
    private static final String USER_READ = "identity:user:read";
    private static final String ROLE_MANAGE = "identity:role:manage";
    private static final String SESSION_READ = "academics:session:read";
    private static final String SESSION_MANAGE = "academics:session:manage";
    private static final String CLASS_READ = "academics:class:read";
    private static final String CLASS_MANAGE = "academics:class:manage";
    private static final String STUDENT_READ = "student:student:read";
    private static final String STUDENT_MANAGE = "student:student:manage";
    private static final String GUARDIAN_READ = "student:guardian:read";
    private static final String GUARDIAN_MANAGE = "student:guardian:manage";

    /**
     * Anything that would make a navigation node say <em>where</em> to go rather than <em>what</em>
     * it is. ADR-0008's central constraint: the server sends stable ids, the frontend owns routes.
     */
    private static final Set<String> ROUTING_SHAPED_KEYS =
            Set.of("href", "url", "uri", "path", "route", "routerlink", "link", "to", "component", "template", "view");

    /** Built here rather than autowired: this test reads the wire bytes, not the application's view of them. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NavigationCatalog navigation;

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
        registerSchool(ORCHARD_CODE, ORCHARD_NAME, ORCHARD_SCHEMA);
        registerSchool(MEADOW_CODE, MEADOW_NAME, MEADOW_SCHEMA);
    }

    @AfterEach
    void clearFixtures() {
        reset();
    }

    // ── A session is required, and nothing more ──────────────────────────────────────────────

    /** The answer a reloading client with an expired cookie gets, and the one it can act on. */
    @Test
    void refusesToBootstrapWithoutASession() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * A parent holds no permission at all, and must still be able to bootstrap. An endpoint that
     * required one would produce an account that can sign in and then not be shown anything, not
     * even the reason.
     */
    @Test
    void everySignedInUserCanBootstrapEvenHoldingNoPermissions() throws Exception {
        createAccount(ORCHARD_SCHEMA, "parent", "Suresh Pillai");
        grant(ORCHARD_SCHEMA, "parent", "PARENT");

        JsonNode me = bootstrap("parent");

        assertThat(me.path("user").path("displayName").asText()).isEqualTo("Suresh Pillai");
        assertThat(me.path("permissions")).isEmpty();
        assertThat(me.path("navigation")).isEmpty();
        assertThat(me.path("permissionsVersion").asText()).isNotBlank();
    }

    // ── The payload ──────────────────────────────────────────────────────────────────────────

    @Test
    void returnsTheUserTheSchoolThePermissionsAndTheMenuInOneCall() throws Exception {
        createAccount(ORCHARD_SCHEMA, "principal", "Ravi Deshpande");
        grant(ORCHARD_SCHEMA, "principal", "PRINCIPAL");

        mockMvc.perform(get("/api/me").cookie(signIn(ORCHARD_CODE, "principal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.id").exists())
                .andExpect(jsonPath("$.data.user.displayName").value("Ravi Deshpande"))
                .andExpect(jsonPath("$.data.user.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.school.code").value(ORCHARD_CODE))
                .andExpect(jsonPath("$.data.school.name").value(ORCHARD_NAME))
                .andExpect(jsonPath("$.data.permissions")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                SCHOOL_READ,
                                SCHOOL_UPDATE,
                                SESSION_READ,
                                SESSION_MANAGE,
                                CLASS_READ,
                                CLASS_MANAGE,
                                STUDENT_READ,
                                STUDENT_MANAGE,
                                GUARDIAN_READ,
                                GUARDIAN_MANAGE,
                                USER_READ,
                                ROLE_MANAGE)))
                // `schools` is deliberately gone. It pointed at the platform REGISTER — every campus
                // on the deployment — which no school user may read; leaving it in the menu meant
                // every user was shown a link to a list of every other school.
                .andExpect(jsonPath("$.data.navigation[0].id").value("students"))
                .andExpect(jsonPath("$.data.navigation[0].labelKey").value("nav.students"))
                // The students container at 25, between the register (20) and academics (30):
                // the class ladder is set up once and revisited rarely, the student list is opened
                // every day. Both of its children are declared inline by the module that owns them.
                .andExpect(jsonPath("$.data.navigation[0].children[0].id").value("students.all"))
                .andExpect(jsonPath("$.data.navigation[0].children[1].id").value("students.guardians"))
                // The academics container. It has no screen of its own; both of its children are
                // declared inline by the module that owns them, which is what makes it a container
                // rather than a leaf.
                .andExpect(jsonPath("$.data.navigation[1].id").value("academics"))
                .andExpect(jsonPath("$.data.navigation[1].children[0].id").value("academics.sessions"))
                .andExpect(jsonPath("$.data.navigation[1].children[1].id").value("academics.classes"))
                .andExpect(jsonPath("$.data.navigation[2].id").value("settings"))
                .andExpect(jsonPath("$.data.navigation[2].children[0].id").value("settings.access"))
                // Contributed by the school module under identity's settings container, placed by
                // its dotted id. A principal holding school:school:update sees both children.
                .andExpect(jsonPath("$.data.navigation[2].children[1].id").value("settings.profile"))
                // A leaf still carries children, as an empty array rather than as an absent field:
                // a client walking the tree must not have to special-case the bottom of it.
                .andExpect(jsonPath("$.data.navigation[0].children[0].children").isEmpty())
                // The gate is not on the wire. Every item that survived filtering is one whose
                // permission the caller holds, so sending it would only invite a second copy of the
                // authorization model on the client (ADR-0008).
                .andExpect(jsonPath("$.data.navigation[0].requiredPermission").doesNotExist())
                .andExpect(jsonPath("$.data.navigation[2].children[0].requiredPermission")
                        .doesNotExist())
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** Read from the account, not from the session, so the flag a login set is still the truth here. */
    @Test
    void reportsThatAnIssuedPasswordMustStillBeChanged() throws Exception {
        createAccountWithIssuedPassword(ORCHARD_SCHEMA, "newjoiner", "Arun Shetty");

        assertThat(bootstrap("newjoiner")
                        .path("user")
                        .path("mustChangePassword")
                        .asBoolean())
                .isTrue();
    }

    /** The school comes off the session, so it must be the school that was signed in to. */
    @Test
    void namesTheSchoolThisSessionSignedInTo() throws Exception {
        createAccount(ORCHARD_SCHEMA, "sharedname", "Anita Roy");
        createAccount(MEADOW_SCHEMA, "sharedname", "Anita Roy");

        assertThat(bootstrap(ORCHARD_CODE, "sharedname")
                        .path("school")
                        .path("code")
                        .asText())
                .isEqualTo(ORCHARD_CODE);
        assertThat(bootstrap(MEADOW_CODE, "sharedname")
                        .path("school")
                        .path("name")
                        .asText())
                .isEqualTo(MEADOW_NAME);
    }

    // ── Navigation is per user ───────────────────────────────────────────────────────────────

    /**
     * The test ADR-0008 exists for. Two users at one school get two menus, and the smaller one
     * contains nothing its owner could not use — asserted against the catalogue's own gating
     * permissions rather than against a hardcoded list, so it keeps holding as modules are added.
     */
    @Test
    void aPrincipalAndAParentGetDifferentMenusAndTheParentsContainsNothingTheyMayNotUse() throws Exception {
        createAccount(ORCHARD_SCHEMA, "principal", "Ravi Deshpande");
        grant(ORCHARD_SCHEMA, "principal", "PRINCIPAL");
        createAccount(ORCHARD_SCHEMA, "parent", "Suresh Pillai");
        grant(ORCHARD_SCHEMA, "parent", "PARENT");

        JsonNode principal = bootstrap("principal");
        JsonNode parent = bootstrap("parent");

        assertThat(idsIn(parent)).isNotEqualTo(idsIn(principal));
        assertThat(idsIn(principal)).contains("students", "settings", "settings.access");

        assertEveryItemIsOneTheCallerMayUse(parent);
        assertEveryItemIsOneTheCallerMayUse(principal);
    }

    /**
     * The auditor holds {@code platform:audit:read} but not {@code identity:role:manage}. Settings
     * has no permission of its own, so it survives on its own account and is then dropped because
     * the only thing inside it is gone — a section that opens onto nothing is worse than no section.
     * The one leaf the auditor may open is still there, which is what makes the dropped section a
     * decision rather than an empty menu.
     *
     * <p>The auditor's menu is now a single item, and that is the honest answer: an auditor reads
     * the audit log and nothing else. It used to be two, the other being the platform register,
     * which no school user may read.
     */
    @Test
    void dropsASectionWhoseOnlyChildTheUserMayNotOpen() throws Exception {
        createAccount(ORCHARD_SCHEMA, "auditor", "Sanjay Bhatt");
        grant(ORCHARD_SCHEMA, "auditor", "AUDITOR");

        JsonNode auditor = bootstrap("auditor");

        assertThat(idsIn(auditor)).containsExactly("audit");
        assertEveryItemIsOneTheCallerMayUse(auditor);
    }

    /**
     * ADR-0008's central constraint, asserted structurally rather than by inspection: the record has
     * no field a URL could go in, so what is left to check is that nothing has smuggled one in as a
     * key or a value, and that every id is a dotted identifier rather than an address.
     */
    @Test
    void noNavigationItemCarriesAnythingUrlShaped() throws Exception {
        createAccount(ORCHARD_SCHEMA, "principal", "Ravi Deshpande");
        grant(ORCHARD_SCHEMA, "principal", "PRINCIPAL");

        JsonNode tree = bootstrap("principal").path("navigation");
        assertThat(tree)
                .as("a principal's menu is not empty, or this test asserts nothing")
                .isNotEmpty();

        List<String> offending = new ArrayList<>();
        collectRoutingShapedFields(tree, "navigation", offending);

        assertThat(offending).as("""
                        The server sends stable ids; the frontend owns routes (ADR-0008). A URL, a path or a
                        component name here means a backend deploy can break navigation, and that the
                        authorization model now lives on both sides of the wire.""").isEmpty();

        assertThat(idsIn(bootstrap("principal")))
                .allSatisfy(id -> assertThat(id).matches(NavigationCatalog.ID_PATTERN.pattern()));
    }

    // ── permissionsVersion ───────────────────────────────────────────────────────────────────

    /**
     * It identifies the permission set and nothing else. Two different roles that happen to grant
     * the same thing are the same version — including across schools, because "which permissions"
     * is not "whose".
     *
     * <p><strong>The two roles are made here rather than picked from the shipped templates.</strong>
     * This test previously leaned on two templates that happened to grant identical sets, and it
     * broke twice — each time a slice widened one of them and not the other, which is a true change
     * to the product and a false failure here. Roles are data (ADR-0005), so a test that needs two
     * roles granting the same thing can simply make two, and then says what it means for as long as
     * the hash does.
     */
    @Test
    void isTheSameVersionForTwoUsersHoldingTheSamePermissions() throws Exception {
        // Two differently named roles, deliberately granting exactly the same one permission.
        createRole(ORCHARD_SCHEMA, "MORNING_TUTOR", SCHOOL_READ);
        createRole(ORCHARD_SCHEMA, "EVENING_TUTOR", SCHOOL_READ);
        createRole(MEADOW_SCHEMA, "MORNING_TUTOR", SCHOOL_READ);

        createAccount(ORCHARD_SCHEMA, "morning", "Priya Menon");
        grant(ORCHARD_SCHEMA, "morning", "MORNING_TUTOR");
        createAccount(ORCHARD_SCHEMA, "evening", "Farida Khan");
        grant(ORCHARD_SCHEMA, "evening", "EVENING_TUTOR");
        createAccount(MEADOW_SCHEMA, "morning", "Nisha Kurup");
        grant(MEADOW_SCHEMA, "morning", "MORNING_TUTOR");

        String morning = versionOf(bootstrap(ORCHARD_CODE, "morning"));
        String evening = versionOf(bootstrap(ORCHARD_CODE, "evening"));
        String elsewhere = versionOf(bootstrap(MEADOW_CODE, "morning"));

        assertThat(evening).as("two different roles granting the same set").isEqualTo(morning);
        assertThat(elsewhere).as("the same set held at another school").isEqualTo(morning);
    }

    @Test
    void isADifferentVersionWhenThePermissionSetDiffers() throws Exception {
        createAccount(ORCHARD_SCHEMA, "principal", "Ravi Deshpande");
        grant(ORCHARD_SCHEMA, "principal", "PRINCIPAL");
        createAccount(ORCHARD_SCHEMA, "auditor", "Sanjay Bhatt");
        grant(ORCHARD_SCHEMA, "auditor", "AUDITOR");
        createAccount(ORCHARD_SCHEMA, "classteacher", "Priya Menon");
        grant(ORCHARD_SCHEMA, "classteacher", "CLASS_TEACHER");
        createAccount(ORCHARD_SCHEMA, "parent", "Suresh Pillai");
        grant(ORCHARD_SCHEMA, "parent", "PARENT");

        assertThat(List.of(
                        versionOf(bootstrap("principal")),
                        versionOf(bootstrap("auditor")),
                        versionOf(bootstrap("classteacher")),
                        versionOf(bootstrap("parent"))))
                .doesNotHaveDuplicates();
    }

    /** Nothing about it is a clock: the same session asked twice gets the same answer. */
    @Test
    void isStableForOneUnchangedPermissionSet() throws Exception {
        createAccount(ORCHARD_SCHEMA, "principal", "Ravi Deshpande");
        grant(ORCHARD_SCHEMA, "principal", "PRINCIPAL");
        Cookie session = signIn(ORCHARD_CODE, "principal");

        assertThat(versionOf(me(session))).isEqualTo(versionOf(me(session)));
    }

    // ── assertions ───────────────────────────────────────────────────────────────────────────

    /**
     * Every id in the returned tree is gated by nothing, or by a permission the response itself
     * says the caller holds. Reads the gate from the catalogue, which is the same place the
     * filtering read it from — so this fails if the filter ever keeps an item it should not.
     */
    private void assertEveryItemIsOneTheCallerMayUse(JsonNode me) {
        Map<String, String> gates = new LinkedHashMap<>();
        collectGates(navigation.all(), gates);

        List<String> held = new ArrayList<>();
        me.path("permissions").forEach(node -> held.add(node.asText()));

        for (String id : idsIn(me)) {
            assertThat(gates)
                    .as("navigation id %s is one the catalogue declares", id)
                    .containsKey(id);
            String gate = gates.get(id);
            if (gate != null) {
                assertThat(held)
                        .as("%s is shown, so its permission %s must be held", id, gate)
                        .contains(gate);
            }
        }
    }

    private static void collectGates(List<NavigationItem> items, Map<String, String> into) {
        for (NavigationItem item : items) {
            into.put(item.id(), item.requiredPermission());
            collectGates(item.children(), into);
        }
    }

    private static void collectRoutingShapedFields(JsonNode node, String path, List<String> offending) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectRoutingShapedFields(node.get(i), path + "[" + i + "]", offending);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            String key = entry.getKey();
            String where = path + "." + key;
            if (ROUTING_SHAPED_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                offending.add(where + " is a routing-shaped field");
            }
            JsonNode value = entry.getValue();
            if (value.isTextual() && looksLikeAnAddress(value.asText())) {
                offending.add(where + " = \"" + value.asText() + "\" looks like an address");
            }
            collectRoutingShapedFields(value, where, offending);
        });
    }

    private static boolean looksLikeAnAddress(String value) {
        return value.startsWith("/") || value.contains("://") || value.contains("\\");
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────

    private JsonNode bootstrap(String username) throws Exception {
        return bootstrap(ORCHARD_CODE, username);
    }

    private JsonNode bootstrap(String schoolCode, String username) throws Exception {
        return me(signIn(schoolCode, username));
    }

    private JsonNode me(Cookie session) throws Exception {
        String body = mockMvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(body).path("data");
    }

    private static String versionOf(JsonNode me) {
        String version = me.path("permissionsVersion").asText();
        assertThat(version).as("permissionsVersion").isNotBlank();
        return version;
    }

    /** Every id in the tree, depth first, in the order it would be rendered. */
    private static List<String> idsIn(JsonNode me) {
        List<String> ids = new ArrayList<>();
        collectIds(me.path("navigation"), ids);
        return ids;
    }

    private static void collectIds(JsonNode items, List<String> into) {
        for (JsonNode item : items) {
            into.add(item.path("id").asText());
            collectIds(item.path("children"), into);
        }
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
        schools.save(new School(code, name, schema, Board.CBSE, "Kochi", "Kerala"));
    }

    private void createAccount(String schema, String username, String displayName) {
        insertAccount(schema, username, displayName, false);
    }

    private void createAccountWithIssuedPassword(String schema, String username, String displayName) {
        insertAccount(schema, username, displayName, true);
    }

    private void insertAccount(String schema, String username, String displayName, boolean mustChangePassword) {
        UUID accountId = UUID.randomUUID();
        jdbc.sql("insert into " + schema
                        + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                        + " values (?, ?, 'ACTIVE', ?, 0)")
                .params(accountId, displayName, mustChangePassword)
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

    private void grant(String schema, String username, String roleCode) {
        jdbc.sql("insert into " + schema
                        + ".user_role_grant (id, user_account_id, role_id, scope_type, scope_id, valid_from, valid_to)"
                        + " values (?, ?, ?, 'SCHOOL', null, null, null)")
                .params(UUID.randomUUID(), accountId(schema, username), roleId(schema, roleCode))
                .update();
    }

    private UUID accountId(String schema, String username) {
        return jdbc.sql("select user_account_id from " + schema + ".user_identifier where value = ?")
                .param(username)
                .query(UUID.class)
                .single();
    }

    /** A role this test owns, so the assertion does not depend on two shipped templates matching. */
    private void createRole(String schema, String code, String... permissions) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("insert into " + schema + ".role (id, code, name, description, template_code)"
                        + " values (?, ?, ?, 'Made by a test', null)")
                .params(roleId, code, code)
                .update();
        for (String permission : permissions) {
            jdbc.sql("insert into " + schema + ".role_permission (role_id, permission_code) values (?, ?)"
                            + " on conflict do nothing")
                    .params(roleId, permission)
                    .update();
        }
    }

    private UUID roleId(String schema, String code) {
        return jdbc.sql("select id from " + schema + ".role where code = ?")
                .param(code)
                .query(UUID.class)
                .single();
    }

    private void reset() {
        for (String schema : List.of(ORCHARD_SCHEMA, MEADOW_SCHEMA)) {
            provisioning.provision(schema);
            // user_account cascades to its grants.
            jdbc.sql("delete from " + schema + ".user_account").update();
        }
        jdbc.sql("delete from public.spring_session").update();
        schools.deleteAll();
    }
}
