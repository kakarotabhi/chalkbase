package in.chalkbase.platform.devdata;

import com.fasterxml.jackson.databind.JsonNode;
import in.chalkbase.platform.devdata.DemoRoster.Child;
import in.chalkbase.platform.devdata.DemoRoster.Guardian;
import in.chalkbase.platform.tenancy.SchemaName;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Builds one invented school on a developer's machine, so that {@code ./mvnw spring-boot:run} opens
 * on something rather than on an empty login screen.
 *
 * <p><strong>This is a local-development tool and it must never be anything else.</strong> Three
 * separate things have to agree before a single row is written: the {@code local} profile has to be
 * active ({@link Profile}), {@code chalkbase.dev.seed-demo-school} must not be turned off
 * ({@link ConditionalOnProperty}), and {@link #refuseAnythingButLocal()} re-checks the environment at
 * run time and stops the application outright if {@code prod} or {@code test} is active. The
 * annotations are a filter on whether the bean exists; the assertion is the one that would still
 * catch a profile list assembled wrongly in a deployment.
 *
 * <p><strong>It is idempotent, and it will not touch a school it did not create.</strong> The first
 * thing it does is ask the API whether {@link #SCHOOL_CODE} is already registered, and if it is, it
 * logs one line and returns. Nothing here updates, deletes or repairs anything: a developer who
 * wants the demo school rebuilt drops the schema and the registry row by hand, deliberately. Twenty
 * restarts produce one school.
 *
 * <p><strong>Why it speaks HTTP to its own API instead of calling services.</strong> A seeder needs
 * to create a school, an academic year, a ladder of classes, students, guardians and accounts —
 * five modules' worth of writes. Services and repositories live in {@code application} and
 * {@code infrastructure} packages, which no other module may import, and the request records in the
 * {@code api} packages are themselves unusable from outside: {@code SaveStudentRequest} names
 * {@code student.domain.Gender}, {@code CreateSchoolRequest} names {@code school.domain.Board}, and
 * {@code ModularityTests} rejects a reference to either from here — correctly. Sending JSON is what
 * is left, and it turns out to be the better answer anyway: every row below is created through the
 * real controller, the real {@code @PreAuthorize}, the real service transaction and the real audit
 * write, signed in as an account this seeder created. A demo school that finishes building is a demo
 * school whose whole stack has just been exercised end to end.
 *
 * <p>The one thing that cannot go over HTTP is the accounts, because there is no endpoint that
 * creates one yet — identity ships sign-in, not user administration. Those four rows are written
 * with {@link JdbcClient} against schema-qualified tables, which is what the module's own tests do,
 * and the password goes through the injected {@link PasswordEncoder} so the stored hash carries its
 * {@code {bcrypt}} prefix. A hand-written hash without that prefix is a 500 on sign-in.
 *
 * <p><strong>The audit log fills up, and that is deliberate.</strong> Roughly two hundred
 * {@code ENTITY_CREATED} rows land in {@code demo_school.audit_event}, attributed to the demo
 * principal, because the seeder goes through the same endpoints a human would. It is not a bug and
 * it is not noise to suppress: the audit screen is a screen, and a screen with nothing on it cannot
 * be judged.
 *
 * <p><strong>No person's name is logged</strong>, here or anywhere below (AGENTS rule 9). The block
 * printed at the end names usernames, role codes and the shared password — an account identifier is
 * not personal data, and the password is a constant in this file on a profile that only ever runs on
 * a laptop. The display names behind those accounts, and every one of the sixty children, stay out
 * of the log.
 */
@Component
@Profile(DemoSchoolSeeder.LOCAL_PROFILE)
@ConditionalOnProperty(prefix = "chalkbase.dev", name = "seed-demo-school", matchIfMissing = true)
public class DemoSchoolSeeder implements ApplicationListener<ApplicationReadyEvent> {

    static final String LOCAL_PROFILE = "local";

    /** Unmistakably a demo, so nobody mistakes it for a school and nobody's script matches it by accident. */
    private static final String SCHOOL_CODE = "DEMO-001";

    private static final String SCHOOL_NAME = "Chalkbase Demo Public School";
    private static final String SCHOOL_SCHEMA = "demo_school";

    /**
     * One password for every demo account, printed at the end of startup. It satisfies
     * {@code PasswordPolicy} — ten characters with a digit and a symbol — so the forced-change flow
     * can be walked through without first inventing a password that passes.
     */
    private static final String PASSWORD = "Chalkbase@2026";

    private static final Logger log = LoggerFactory.getLogger(DemoSchoolSeeder.class);

    /**
     * The four accounts, and why each one is here. All four hold a shipped role template rather than
     * a hand-built permission set, so what a developer sees when they sign in is what a school would
     * see (ADR-0005).
     */
    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("principal", "Nandini Apte", "PRINCIPAL", false),
            new DemoAccount("classteacher", "Ravi Deshpande", "CLASS_TEACHER", false),
            // The audit log is the auditor's and nobody else's. Without this account the audit
            // screen has nobody who can open it.
            new DemoAccount("auditor", "Meenakshi Rao", "AUDITOR", false),
            // Signs in and is sent straight to the change-password screen, which is otherwise a
            // flow nobody sees until a real school's first day.
            new DemoAccount("newteacher", "Farhan Siddiqui", "SUBJECT_TEACHER", true));

    private final Environment environment;
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoSchoolSeeder(Environment environment, JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.environment = environment;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Runs once the web server is accepting connections, which is what {@link ApplicationReadyEvent}
     * means and what an {@code ApplicationRunner} would not guarantee for a seeder that calls its own
     * API. Startup migrations are long finished by then — {@code TenantMigrationRunner} is an
     * {@code InitializingBean} the entity manager factory depends on.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        refuseAnythingButLocal();

        int port = portOf(event);
        SeedApiClient api = new SeedApiClient(port);

        if (alreadySeeded(api)) {
            log.info(
                    "Demo school {} is already registered; leaving it alone. Drop the {} schema and its"
                            + " public.school row by hand if you want it rebuilt.",
                    SCHOOL_CODE,
                    SCHOOL_SCHEMA);
            return;
        }

        try {
            seed(api, port);
        } catch (RuntimeException ex) {
            // Deliberately not fatal. A half-built demo school must not stop a developer working,
            // and the next start will find the registry row and skip — so say plainly what to
            // delete, because the guard cannot tell a finished seed from an abandoned one.
            log.error(
                    "Seeding the demo school failed. It is now half-built and the next start will skip it."
                            + " Drop schema {} cascade and delete from public.school where code = '{}' to try again.",
                    SCHOOL_SCHEMA,
                    SCHOOL_CODE,
                    ex);
        }
    }

    // ── the guard ────────────────────────────────────────────────────────────────────────────

    /**
     * Stops the application rather than seeding into anything that is not a developer's machine.
     *
     * <p>{@link Profile} already decides whether this bean exists at all, so reaching here with
     * {@code prod} active means the profile list itself is wrong — and the honest response to
     * "production might be about to receive sixty invented children" is to refuse to start, loudly,
     * rather than to log a warning nobody reads.
     */
    private void refuseAnythingButLocal() {
        if (environment.matchesProfiles("prod", "test")) {
            throw new IllegalStateException("The demo-data seeder found itself running with profiles "
                    + List.of(environment.getActiveProfiles())
                    + ". It writes invented students and a well-known password and only ever runs on `local`."
                    + " Refusing to start.");
        }
        if (!environment.matchesProfiles(LOCAL_PROFILE)) {
            throw new IllegalStateException("The demo-data seeder is only for the `local` profile, and the active"
                    + " profiles are " + List.of(environment.getActiveProfiles()) + ". Refusing to start.");
        }
    }

    private static int portOf(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext web && web.getWebServer() != null) {
            return web.getWebServer().getPort();
        }
        throw new IllegalStateException("The demo-data seeder needs the running port and there is no web server");
    }

    /** By code, through the same list endpoint a person would read. Active or not: a code is a code. */
    private static boolean alreadySeeded(SeedApiClient api) {
        for (JsonNode school : api.get("/api/schools").path("data")) {
            if (SCHOOL_CODE.equals(school.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    // ── the seed ─────────────────────────────────────────────────────────────────────────────

    private void seed(SeedApiClient api, int port) {
        long started = System.currentTimeMillis();

        // Onboarding is permitAll and CSRF-exempt today, so this one runs before there is anyone to
        // sign in as — which is also the call that creates the schema, migrates it and copies the
        // role templates into it (SchoolProvisioning).
        api.postForData(
                "/api/schools",
                new LinkedHashMap<>(Map.of(
                        "code",
                        SCHOOL_CODE,
                        "name",
                        SCHOOL_NAME,
                        "schemaName",
                        SCHOOL_SCHEMA,
                        "board",
                        "CBSE",
                        "city",
                        "Nagpur",
                        "state",
                        "Maharashtra")));
        log.info("Demo school {} registered and schema {} provisioned", SCHOOL_CODE, SCHOOL_SCHEMA);

        createAccounts();

        signIn(api, ACCOUNTS.getFirst().username());

        AcademicYear year = currentIndianSchoolYear(LocalDate.now());
        UUID sessionId = createAcademicSession(api, year);
        List<SeedSection> sections = createLadder(api);
        int guardianCount = admitEveryone(api, sessionId, year, sections);

        announce(port, year, sections.size(), guardianCount, (System.currentTimeMillis() - started) / 1000);
    }

    // ── accounts ─────────────────────────────────────────────────────────────────────────────

    /**
     * The four sign-ins, written straight to the school's schema.
     *
     * <p>SQL rather than an API call because identity has no endpoint that creates a user yet. The
     * tables are schema-qualified rather than reached through a bound tenant, for the same reason
     * the module's own tests qualify them: {@link JdbcClient} takes an ordinary pooled connection
     * whose {@code search_path} belongs to whoever used it last, and only Hibernate goes through
     * {@code SchemaMultiTenantConnectionProvider}.
     *
     * <p>The role is granted by pointing at the school's own copy of the shipped template, which
     * {@code SchoolProvisioning} has already installed. Building a role here would prove nothing
     * about what a real principal can actually do.
     */
    private void createAccounts() {
        String schema = SchemaName.requireValid(SCHOOL_SCHEMA);
        for (DemoAccount account : ACCOUNTS) {
            UUID accountId = uuid();
            jdbc.sql("insert into " + schema
                            + ".user_account (id, display_name, status, must_change_password, failed_attempts)"
                            + " values (?, ?, 'ACTIVE', ?, 0)")
                    .params(accountId, account.displayName(), account.mustChangePassword())
                    .update();
            jdbc.sql("insert into " + schema + ".user_identifier (id, user_account_id, type, value)"
                            + " values (?, ?, 'USERNAME', ?)")
                    .params(uuid(), accountId, account.username())
                    .update();
            // Through the injected encoder, never a literal: the stored value carries the
            // `{bcrypt}` prefix DelegatingPasswordEncoder needs to know how to check it.
            jdbc.sql("insert into " + schema + ".user_credential (id, user_account_id, type, secret, status)"
                            + " values (?, ?, 'PASSWORD', ?, 'ACTIVE')")
                    .params(uuid(), accountId, passwordEncoder.encode(PASSWORD))
                    .update();
            jdbc.sql("insert into " + schema + ".user_role_grant (id, user_account_id, role_id, scope_type)"
                            + " values (?, ?, (select id from " + schema + ".role where code = ?), 'SCHOOL')")
                    .params(uuid(), accountId, account.roleCode())
                    .update();
        }
        log.info("Created {} demo accounts in {}", ACCOUNTS.size(), schema);
    }

    private static void signIn(SeedApiClient api, String username) {
        api.post(
                "/api/auth/login",
                new LinkedHashMap<>(Map.of("schoolCode", SCHOOL_CODE, "username", username, "password", PASSWORD)));
    }

    // ── academic structure ───────────────────────────────────────────────────────────────────

    private static UUID createAcademicSession(SeedApiClient api, AcademicYear year) {
        UUID id = idOf(api.postForData(
                "/api/academics/sessions",
                new LinkedHashMap<>(Map.of(
                        "name", year.name(),
                        "startsOn", year.startsOn().toString(),
                        "endsOn", year.endsOn().toString()))));
        // Creating a year deliberately does not enter it (AcademicSessionService), so the school is
        // moved into it by the endpoint that exists for that and clears whatever held it before.
        api.post("/api/academics/sessions/" + id + "/current", Map.of());
        return id;
    }

    /**
     * Nursery through Class 8, two sections each.
     *
     * <p>Created in ladder order and never renumbered: {@code POST /api/academics/classes} appends at
     * {@code max(sequence) + 1}, so the order these calls are made in <em>is</em> the sequence
     * (ADR-0019). Nothing here sets a sequence, because nothing may.
     */
    private static List<SeedSection> createLadder(SeedApiClient api) {
        List<SeedSection> sections = new ArrayList<>();
        for (int rung = 0; rung < DemoRoster.CLASS_NAMES.size(); rung++) {
            String className = DemoRoster.CLASS_NAMES.get(rung);
            UUID classId =
                    idOf(api.postForData("/api/academics/classes", new LinkedHashMap<>(Map.of("name", className))));
            for (String sectionName : DemoRoster.SECTION_NAMES) {
                UUID sectionId = idOf(api.postForData(
                        "/api/academics/classes/" + classId + "/sections",
                        new LinkedHashMap<>(Map.of("name", sectionName))));
                sections.add(new SeedSection(rung, sectionId));
            }
        }
        log.info("Created {} classes and {} sections", DemoRoster.CLASS_NAMES.size(), sections.size());
        return List.copyOf(sections);
    }

    // ── children and the people responsible for them ─────────────────────────────────────────

    /**
     * Every guardian household once, then every child, then the links between them.
     *
     * <p>The order is the whole demonstration. Guardians are created first and their ids kept, so a
     * family's three children link to <em>one</em> person record (ADR-0020 §5) — which is what makes
     * correcting a phone number on the guardian screen visibly correct it for all three. Creating a
     * guardian per child would have been fewer lines and would have modelled the thing this product
     * exists not to do.
     *
     * @return how many guardian records were created, which is meaningfully fewer than the number of
     *     links
     */
    private static int admitEveryone(SeedApiClient api, UUID sessionId, AcademicYear year, List<SeedSection> sections) {
        Map<String, List<UUID>> guardianIds = new LinkedHashMap<>();
        for (Map.Entry<String, List<Guardian>> family : DemoRoster.families().entrySet()) {
            List<UUID> created = new ArrayList<>();
            for (Guardian guardian : family.getValue()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("fullName", guardian.fullName());
                body.put("phone", guardian.phone());
                body.put("occupation", guardian.occupation());
                if (guardian.email() != null) {
                    body.put("email", guardian.email());
                }
                created.add(idOf(api.postForData("/api/guardians", body)));
            }
            guardianIds.put(family.getKey(), List.copyOf(created));
        }
        log.info(
                "Created {} guardian records",
                guardianIds.values().stream().mapToInt(List::size).sum());

        Map<UUID, Integer> rollNumbers = new HashMap<>();
        List<Child> children = DemoRoster.children();
        for (int i = 0; i < children.size(); i++) {
            Child child = children.get(i);
            SeedSection section = sections.get(i % sections.size());

            Map<String, Object> student = new LinkedHashMap<>();
            student.put("admissionNumber", "%d/%04d".formatted(year.startsOn().getYear(), i + 1));
            student.put("fullName", child.fullName());
            student.put("dateOfBirth", dateOfBirth(year, section.rung(), i).toString());
            student.put("gender", child.gender());
            student.put("status", "ACTIVE");
            // Every seventh record has no admission date, because a register copied off paper often
            // does not have one and the field is optional for that reason.
            if (i % 7 != 3) {
                student.put("admittedOn", year.startsOn().plusDays(i % 21L).toString());
            }
            UUID studentId = idOf(api.postForData("/api/students", student));

            int roll = rollNumbers.merge(section.sectionId(), 1, Integer::sum);
            api.post(
                    "/api/students/" + studentId + "/enrolments",
                    new LinkedHashMap<>(Map.of(
                            "academicSessionId", sessionId.toString(),
                            "sectionId", section.sectionId().toString(),
                            "rollNumber", "%02d".formatted(roll))));

            // Progress, because this is slow against a hosted database — sixty children is roughly
            // two hundred and forty requests, and a developer watching a silent log wants to know it
            // is working rather than hung.
            if ((i + 1) % 10 == 0) {
                log.info("Admitted {} of {} students", i + 1, children.size());
            }

            List<Guardian> guardians = DemoRoster.guardiansOf(child);
            List<UUID> ids = child.family() == null ? List.of() : guardianIds.get(child.family());
            for (int g = 0; g < guardians.size(); g++) {
                api.post(
                        "/api/students/" + studentId + "/guardians",
                        new LinkedHashMap<>(Map.of(
                                "guardianId", ids.get(g).toString(),
                                "relation", guardians.get(g).relation(),
                                "primary", guardians.get(g).primary())));
            }
        }
        log.info("Admitted and enrolled {} students", children.size());
        return guardianIds.values().stream().mapToInt(List::size).sum();
    }

    /**
     * A date of birth that matches the rung the child is on, varied so no two records look copied.
     *
     * <p>Nursery is three, and every class above adds a year. The month and day move with the
     * child's position in the roster, which keeps every date comfortably in the past and stops sixty
     * students sharing one birthday.
     */
    private static LocalDate dateOfBirth(AcademicYear year, int rung, int index) {
        int born = year.startsOn().getYear() - (DemoRoster.YOUNGEST_AGE + rung);
        return LocalDate.of(born, 1 + index % 12, 1 + (index * 7) % 28);
    }

    // ── the academic year ────────────────────────────────────────────────────────────────────

    /**
     * The Indian school year containing {@code today}: April to the following March.
     *
     * <p>Derived rather than hardcoded, so the demo school is in the current year whenever somebody
     * clones this repository. April is the boundary — a run in February belongs to the year that
     * started last April.
     */
    private static AcademicYear currentIndianSchoolYear(LocalDate today) {
        int startYear = today.getMonthValue() >= Month.APRIL.getValue() ? today.getYear() : today.getYear() - 1;
        return new AcademicYear(
                "%d-%02d".formatted(startYear, (startYear + 1) % 100),
                LocalDate.of(startYear, Month.APRIL, 1),
                LocalDate.of(startYear + 1, Month.MARCH, 31));
    }

    // ── the block a developer actually reads ─────────────────────────────────────────────────

    /**
     * What to type to get in, in one block at the end of startup.
     *
     * <p>Usernames, role codes and the shared password only. The display names behind these accounts
     * and every one of the sixty children stay out of the log, because AGENTS rule 9 does not have an
     * exception for invented people — the habit is what protects the real ones.
     */
    private void announce(int port, AcademicYear year, int sectionCount, int guardianCount, long seconds) {
        StringBuilder block =
                new StringBuilder("""

                ────────────────────────────────────────────────────────────────────────────
                 Demo school seeded. Sign in at http://localhost:%d/swagger-ui.html
                 or point the frontend at it.

                   School code      %s   (%s, schema %s)
                   Academic year    %s
                   Password         %s   — the same for every account below

                """.formatted(port, SCHOOL_CODE, SCHOOL_NAME, SCHOOL_SCHEMA, year.name(), PASSWORD));
        for (DemoAccount account : ACCOUNTS) {
            block.append("   %-16s %s%s%n"
                    .formatted(
                            account.username(),
                            account.roleCode(),
                            account.mustChangePassword() ? "   (must change password on first sign-in)" : ""));
        }
        block.append("""

                   %d students · %d classes · %d sections · %d guardian records
                   The audit log now has content, because the seeder used the real endpoints.
                   Seeded in %d s. This runs once: restart and it is skipped.
                ────────────────────────────────────────────────────────────────────────────
                """.formatted(
                DemoRoster.children().size(), DemoRoster.CLASS_NAMES.size(), sectionCount, guardianCount, seconds));
        log.info("{}", block);
    }

    // ── small things ─────────────────────────────────────────────────────────────────────────

    private static UUID idOf(JsonNode data) {
        String id = data.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Demo seed expected an id in the response and there was none");
        }
        return UUID.fromString(id);
    }

    /**
     * A v7 id, matching what the entities generate. Ids from different schools meet during any
     * cross-school rollup (ADR-0011), so a demo account with a v4 id would be the odd one out in
     * every ordering the rest of the product relies on.
     */
    private static UUID uuid() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }

    private record DemoAccount(String username, String displayName, String roleCode, boolean mustChangePassword) {
        /** Never logged: {@code displayName} is a person's name, invented or not (AGENTS rule 9). */
        @Override
        public String toString() {
            return "DemoAccount[" + username + "]";
        }
    }

    private record SeedSection(int rung, UUID sectionId) {}

    private record AcademicYear(String name, LocalDate startsOn, LocalDate endsOn) {}
}
