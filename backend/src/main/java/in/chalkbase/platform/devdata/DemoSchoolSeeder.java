package in.chalkbase.platform.devdata;

import com.fasterxml.jackson.databind.JsonNode;
import in.chalkbase.platform.devdata.DemoRoster.Child;
import in.chalkbase.platform.devdata.DemoRoster.Guardian;
import in.chalkbase.platform.tenancy.SchemaName;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * <p><strong>It is idempotent, and by default it will not touch a school it did not create.</strong>
 * The first thing it does is ask the API whether {@link #SCHOOL_CODE} is already registered. If it
 * is, it logs one line and returns — unless {@value #TOP_UP_PROPERTY} is set, in which case it signs
 * in and tops the roster up to {@link DemoRoster#TOTAL_CHILDREN} instead of skipping outright. See
 * {@link #topUp} for why that is a top-up and not a second seed: it imports only the children beyond
 * however many the school already has, so restarting with the property left on twenty times still
 * leaves the roster at {@link DemoRoster#TOTAL_CHILDREN}, not twenty times that. Nothing here
 * updates, deletes or repairs a student that is already there; a developer who wants the demo school
 * rebuilt from nothing drops the schema and the registry row by hand, deliberately.
 *
 * <p><strong>Why a school with the smaller, pre-ADR-0021 roster can exist at all.</strong> The
 * shared Supabase project every `local` developer points at already had {@link #SCHOOL_CODE}
 * registered with a few dozen students before this roster grew to {@link DemoRoster#TOTAL_CHILDREN},
 * and the guard above means nobody who runs against that database sees the larger roster by simply
 * restarting — the school is "already registered" the moment the first few students exist. {@value
 * #TOP_UP_PROPERTY} exists for exactly that database: set it once, restart, and the difference is
 * imported through the same bulk endpoint the fresh-school path uses, never a second create-and-skip
 * of what is already there.
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
 * <p><strong>Six hundred students go in as one file, not six hundred requests.</strong> The ladder
 * and the accounts are still built one call per row — there are only a few dozen of those and the
 * point of going through the real endpoints stands. Students, their enrolment and their guardians
 * are built as one CSV file instead and sent to {@code POST /api/students/import} (ADR-0021), the
 * bulk endpoint the product ships for a school's first roll upload. That is not a shortcut around
 * the "through the real stack" rule above — the import endpoint <em>is</em> the real stack for this
 * volume of data, exercised the way a school actually would on its first day, and it turns what
 * would be well over a thousand sequential HTTP round trips (a create, an enrolment and up to two
 * guardian links per child) into one request the server commits in a single transaction. See
 * {@link #admit} for what that call carries and {@link DemoRoster#build} for the roster
 * behind it.
 *
 * <p>The one thing that cannot go over HTTP is the accounts, because there is no endpoint that
 * creates one yet — identity ships sign-in, not user administration. Those four rows are written
 * with {@link JdbcClient} against schema-qualified tables, which is what the module's own tests do,
 * and the password goes through the injected {@link PasswordEncoder} so the stored hash carries its
 * {@code {bcrypt}} prefix. A hand-written hash without that prefix is a 500 on sign-in.
 *
 * <p><strong>The audit log fills up, and that is deliberate.</strong> The ladder, the accounts and
 * the school profile still write one {@code ENTITY_CREATED} row apiece, attributed to the demo
 * principal, because the seeder goes through the same endpoints a human would. The six hundred
 * students do not: {@code STUDENTS_IMPORTED} and, when the roster creates new guardians,
 * {@code GUARDIANS_IMPORTED} are each one row for the whole import (ADR-0021 §7), the same way a
 * real school's onboarding would read on the audit screen. It is not a bug and it is not noise to
 * suppress: the audit screen is a screen, and a screen with nothing on it cannot be judged.
 *
 * <p><strong>No person's name is logged</strong>, here or anywhere below (AGENTS rule 9). The block
 * printed at the end names usernames, role codes and the shared password — an account identifier is
 * not personal data, and the password is a constant in this file on a profile that only ever runs on
 * a laptop. The display names behind those accounts, and every one of the six hundred children, stay
 * out of the log. Where a failed import must say something about the file this seeder built, it
 * reports the row number and the column {@code StudentImportService} named, never a value — exactly
 * what ADR-0021 already requires the endpoint's own report to do.
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
     * Off by default, deliberately: a developer restarting {@code local} against a school that
     * already exists should pay one cheap read to confirm nothing needs to change, not sign in and
     * scan the roster on every boot forever. Set true — or the equivalent env var,
     * {@code CHALKBASE_DEV_TOP_UP_DEMO_SCHOOL} — to grow an existing {@link #SCHOOL_CODE} up to
     * {@link DemoRoster#TOTAL_CHILDREN}. See {@link #topUp}.
     */
    static final String TOP_UP_PROPERTY = "chalkbase.dev.top-up-demo-school";

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
    private final in.chalkbase.platform.tenancy.SchoolProvisioning provisioning;

    public DemoSchoolSeeder(
            Environment environment,
            JdbcClient jdbc,
            PasswordEncoder passwordEncoder,
            in.chalkbase.platform.tenancy.SchoolProvisioning provisioning) {
        this.environment = environment;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.provisioning = provisioning;
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
            if (!environment.getProperty(TOP_UP_PROPERTY, Boolean.class, false)) {
                log.info(
                        "Demo school {} is already registered; leaving it alone. It may not have the full"
                                + " ~{}-student roster this build ships — set {}=true (env var"
                                + " CHALKBASE_DEV_TOP_UP_DEMO_SCHOOL) and restart to top it up without touching a"
                                + " student already there. Drop the {} schema and its public.school row by hand if"
                                + " you want the whole school rebuilt instead.",
                        SCHOOL_CODE,
                        DemoRoster.TOTAL_CHILDREN,
                        TOP_UP_PROPERTY,
                        SCHOOL_SCHEMA);
                return;
            }
            try {
                topUp(api);
            } catch (RuntimeException ex) {
                // Same reasoning as the fresh-seed catch below: not fatal, and the next start (with the
                // property still set) picks up wherever the student count actually landed, because
                // admit(...) below is keyed on that count rather than on a flag saying "done".
                log.error(
                        "Topping up demo school {}'s roster failed. The next start with {}=true will pick up"
                                + " wherever the student count actually landed.",
                        SCHOOL_CODE,
                        TOP_UP_PROPERTY,
                        ex);
            }
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

        // Registered directly rather than through POST /api/schools, and the reason is a real one
        // rather than convenience.
        //
        // That endpoint used to be permitAll, so this call worked before there was anyone to sign in
        // as. It is now an operator endpoint requiring `school:school:create`, which no role holds —
        // so the HTTP call would be refused, and the chicken-and-egg is unresolvable over HTTP: the
        // first account cannot exist until the school does.
        //
        // The seeder is not a caller. It is the application starting itself up, in the same position
        // as TenantMigrationRunner, so it does what the service would do: write the registry row and
        // provision the schema — which migrates it and copies the role templates in.
        jdbc.sql("insert into public.school (id, code, name, schema_name, board, city, state, active)"
                        + " values (?, ?, ?, ?, ?, ?, ?, true) on conflict (code) do nothing")
                .params(
                        java.util.UUID.randomUUID(),
                        SCHOOL_CODE,
                        SCHOOL_NAME,
                        SCHOOL_SCHEMA,
                        "CBSE",
                        "Nagpur",
                        "Maharashtra")
                .update();
        provisioning.provision(SCHOOL_SCHEMA);
        log.info("Demo school {} registered and schema {} provisioned", SCHOOL_CODE, SCHOOL_SCHEMA);

        createAccounts();

        signIn(api, ACCOUNTS.getFirst().username());

        fillInTheSchoolProfile(api);

        AcademicYear year = currentIndianSchoolYear(LocalDate.now());
        UUID sessionId = createAcademicSession(api, year);
        List<SeedSection> sections = createLadder(api);
        int guardianCount = admit(api, sessionId, year, sections, 0);

        announce(port, year, sections.size(), guardianCount, (System.currentTimeMillis() - started) / 1000);
    }

    // ── topping up an existing school ───────────────────────────────────────────────────────────

    /**
     * Grows an already-registered {@link #SCHOOL_CODE} up to {@link DemoRoster#TOTAL_CHILDREN},
     * without rebuilding the school, the ladder or the accounts it already has.
     *
     * <p>Signs in, reads how many students the school already holds, and — if that is short of the
     * roster's current size — imports exactly the difference through the same bulk endpoint
     * {@link #admit} always uses, resuming the roster where it left off rather than restarting it.
     * Running this twice in a row does nothing the second time: the count read on the second run is
     * already {@link DemoRoster#TOTAL_CHILDREN}, so there is nothing left to import — the same
     * property that makes a single {@link #admit} call safe to retry after a failure also makes
     * repeated top-ups safe.
     *
     * <p>The ladder and the current academic session are read back rather than recreated —
     * {@link #existingLadder} and {@link #currentSession} — because a school that already exists
     * already has both, and creating either again would either fail (a class or section with a name
     * that already exists) or leave a second academic year in the school nobody asked for.
     */
    private void topUp(SeedApiClient api) {
        long started = System.currentTimeMillis();
        signIn(api, ACCOUNTS.getFirst().username());

        int existing = studentCount(api);
        if (existing >= DemoRoster.TOTAL_CHILDREN) {
            log.info(
                    "Demo school {} already has {} students, at or past the roster's current size of {};"
                            + " nothing to top up.",
                    SCHOOL_CODE,
                    existing,
                    DemoRoster.TOTAL_CHILDREN);
            return;
        }

        SeedSession session = currentSession(api);
        List<SeedSection> sections = existingLadder(api);
        int guardianCount = admit(api, session.id(), session.year(), sections, existing);

        log.info(
                "Topped up demo school {} from {} to {} students ({} guardian record(s) on file"
                        + " afterwards) in {} s.",
                SCHOOL_CODE,
                existing,
                DemoRoster.children().size(),
                guardianCount,
                (System.currentTimeMillis() - started) / 1000);
    }

    /** How many students {@link #SCHOOL_CODE} already has, read the way the register screen would. */
    private static int studentCount(SeedApiClient api) {
        return api.get("/api/students?page=0&size=1")
                .path("data")
                .path("totalElements")
                .asInt();
    }

    /**
     * The school's current academic session and the year it covers, read back rather than created —
     * {@link #topUp} runs against a school that already has one.
     */
    private static SeedSession currentSession(SeedApiClient api) {
        for (JsonNode session : api.get("/api/academics/sessions").path("data")) {
            if (session.path("current").asBoolean(false)) {
                return new SeedSession(
                        UUID.fromString(session.path("id").asText()),
                        new AcademicYear(
                                session.path("name").asText(),
                                LocalDate.parse(session.path("startsOn").asText()),
                                LocalDate.parse(session.path("endsOn").asText())));
            }
        }
        throw new IllegalStateException(
                "Demo school " + SCHOOL_CODE + " has no current academic session; cannot top up its roster.");
    }

    /**
     * The school's classes and sections, read back in ladder order rather than created again —
     * {@link #createLadder} would either collide on a name already taken or, worse, succeed and
     * leave the school with two Nurseries.
     *
     * <p>{@code GET /api/academics/classes} answers in sequence order already (ADR-0019) and returns
     * retired classes and sections alongside active ones, so both are filtered out here — a top-up
     * placing a child in a retired section would be its own small bug on top of the one this method
     * exists to fix.
     */
    private static List<SeedSection> existingLadder(SeedApiClient api) {
        List<SeedSection> sections = new ArrayList<>();
        int rung = 0;
        for (JsonNode schoolClass : api.get("/api/academics/classes").path("data")) {
            if (!schoolClass.path("active").asBoolean(true)) {
                continue;
            }
            for (JsonNode section : schoolClass.path("sections")) {
                if (!section.path("active").asBoolean(true)) {
                    continue;
                }
                sections.add(new SeedSection(
                        rung,
                        schoolClass.path("name").asText(),
                        section.path("name").asText(),
                        UUID.fromString(section.path("id").asText())));
            }
            rung++;
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException(
                    "Demo school " + SCHOOL_CODE + " has no classes or sections; cannot top up its roster.");
        }
        return List.copyOf(sections);
    }

    /**
     * Fills in the school's own profile, so the screen demonstrates itself rather than its empty
     * state.
     *
     * <p>Left out of the first version of this seeder, and it showed: Settings › School profile was
     * the one screen in the product that a visitor always found saying "not filled in yet". The
     * empty state is worth seeing once; it is not worth being the only thing anyone sees.
     *
     * <p>Goes over HTTP as the principal, unlike the school registration above, because this one has
     * a caller: the principal holds {@code school:school:update} and this is exactly what they would
     * do on their first afternoon.
     */
    private void fillInTheSchoolProfile(SeedApiClient api) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("code", SCHOOL_CODE);
        profile.put("schemaName", SCHOOL_SCHEMA);
        profile.put("name", SCHOOL_NAME);
        profile.put("board", "CBSE");
        profile.put("addressLine1", "Plot 22, Ramdaspeth");
        profile.put("addressLine2", "Near the water tower");
        profile.put("city", "Nagpur");
        profile.put("state", "Maharashtra");
        profile.put("pincode", "440010");
        profile.put("principalName", "Nandini Apte");
        profile.put("phone", "+91 712 255 0100");
        profile.put("email", "office@chalkbase-demo.example.in");
        profile.put("website", "https://chalkbase-demo.example.in");
        profile.put("affiliationNumber", "1130456");
        api.put("/api/school/profile", profile);
        log.info("Filled in the school profile for {}", SCHOOL_CODE);
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
                // The name travels with the id: the bulk import (ADR-0021 §3) places a child by the
                // class and section <em>names</em> in a CSV cell, not by a UUID it was never handed.
                sections.add(new SeedSection(rung, className, sectionName, sectionId));
            }
        }
        log.info("Created {} classes and {} sections", DemoRoster.CLASS_NAMES.size(), sections.size());
        return List.copyOf(sections);
    }

    // ── children and the people responsible for them ─────────────────────────────────────────

    /**
     * The header of the CSV this seeder builds, in the column names {@code StudentImportService}
     * matches (case and separators forgiven, spelling not).
     */
    private static final String IMPORT_CSV_HEADER = "admission_number,full_name,date_of_birth,gender,status,"
            + "admitted_on,class,section,roll_number,guardian_name,guardian_phone,guardian_relation,"
            + "guardian_email,guardian_primary";

    /**
     * The roster from {@code fromIndex} onward, as one file to {@code POST /api/students/import}
     * (ADR-0021). {@code fromIndex} is {@code 0} for a brand-new school admitting the whole roster in
     * one call, or however many students an already-registered school has, for {@link #topUp} — see
     * {@link #buildImportCsv} for why the file only ever carries the rows nobody has seen yet, while
     * the arithmetic behind every row still runs over the roster from the very start.
     *
     * <p><strong>Why one file instead of one call per child.</strong> The bulk import format carries
     * one guardian per row, so it cannot by itself put both parents on the same child — but it dedupes
     * a repeated phone number into one guardian record across the <em>whole file</em> (ADR-0021 §4),
     * which is exactly the sibling-sharing behaviour ADR-0020 §5 exists for and is the common shape of
     * this roster (see {@link DemoRoster#build}). What it cannot do — the second guardian on the
     * handful of households that have two — is done afterwards, the way a school does it after any
     * import: found by the admission number just written, and linked through the same
     * {@code /api/guardians} and {@code /api/students/{id}/guardians} calls the smaller seed always
     * used. See {@link #linkSecondGuardians}.
     *
     * <p>The import is all-or-nothing (ADR-0021 §2): a row this method got wrong comes back as a
     * report with {@code imported} at zero and a row number and column naming the mistake, never an
     * exception from a half-built request. That is treated as a bug in this seeder, not in the
     * endpoint, and fails startup loudly rather than leaving a partial school behind.
     *
     * @return how many guardian records exist afterwards — what the CSV import created or matched,
     *     plus every second guardian linked on top of it
     */
    private static int admit(
            SeedApiClient api, UUID sessionId, AcademicYear year, List<SeedSection> sections, int fromIndex) {
        List<Child> children = DemoRoster.children();
        if (fromIndex >= children.size()) {
            return 0;
        }
        Map<String, String> admissionNumberOfDualGuardianFamily = new LinkedHashMap<>();
        String csv = buildImportCsv(year, sections, children, fromIndex, admissionNumberOfDualGuardianFamily);

        JsonNode report = api.postMultipartForData(
                "/api/students/import?academicSessionId=" + sessionId,
                "demo-school-roster.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        int expected = children.size() - fromIndex;
        int imported = report.path("imported").asInt();
        if (imported != expected) {
            throw new IllegalStateException("The demo seed built a CSV of " + expected
                    + " students and the real import endpoint imported " + imported
                    + ". That is a bug in DemoRoster or DemoSchoolSeeder, not in the import endpoint — "
                    + describeFirstProblem(report));
        }
        log.info(
                "Imported {} students in one request: {} new guardian(s), {} already on file, {} guardian link(s)",
                imported,
                report.path("guardiansCreated").asInt(),
                report.path("guardiansMatched").asInt(),
                report.path("guardianLinksCreated").asInt());

        int secondGuardians = linkSecondGuardians(api, admissionNumberOfDualGuardianFamily);
        return report.path("guardiansCreated").asInt()
                + report.path("guardiansMatched").asInt()
                + secondGuardians;
    }

    /**
     * One CSV row per child from {@code fromIndex} onward, in the column order
     * {@link #IMPORT_CSV_HEADER} names.
     *
     * <p>Placement, date of birth, roll number and the once-in-seven missing admission date are all
     * unchanged from the small seed's own per-child logic — only where the result goes is different:
     * into a cell rather than a request body.
     *
     * <p><strong>The loop runs over every child from the start, even when only a suffix is written.</strong>
     * A child's admission number, section and roll number all depend on how many children before it
     * were assigned to the same section — the same {@code rollNumbers} counter a fresh, whole-roster
     * import already used. Starting that counter at zero for a top-up's shorter file would hand two
     * different children the same roll number in the same section: one from whichever earlier run
     * imported the prefix, one from this one. Running the full loop and skipping the rows before
     * {@code fromIndex} costs nothing — it is in-memory arithmetic, not a request — and it is what
     * keeps a top-up's rows numbered exactly where they would have landed had the whole roster gone in
     * as a single file.
     *
     * @param admissionNumberOfDualGuardianFamily filled in as rows at or after {@code fromIndex} are
     *     written, one entry per household this roster gives two guardians — the admission number is
     *     how {@link #linkSecondGuardians} finds the child again after the import has run. A household
     *     entirely before {@code fromIndex} is left out, because a previous run already linked its
     *     second guardian and this one does not touch that child again.
     */
    private static String buildImportCsv(
            AcademicYear year,
            List<SeedSection> sections,
            List<Child> children,
            int fromIndex,
            Map<String, String> admissionNumberOfDualGuardianFamily) {
        Map<UUID, Integer> rollNumbers = new HashMap<>();
        StringBuilder csv = new StringBuilder(IMPORT_CSV_HEADER).append('\n');

        for (int i = 0; i < children.size(); i++) {
            Child child = children.get(i);
            SeedSection section = sections.get(i % sections.size());
            String admissionNumber = "%d/%04d".formatted(year.startsOn().getYear(), i + 1);
            LocalDate dateOfBirth = dateOfBirth(year, section.rung(), i);
            // Every seventh record has no admission date, because a register copied off paper often
            // does not have one and the column is optional for that reason.
            String admittedOn = i % 7 != 3 ? year.startsOn().plusDays(i % 21L).toString() : "";
            int roll = rollNumbers.merge(section.sectionId(), 1, Integer::sum);

            if (i < fromIndex) {
                // Already imported by an earlier run. The bookkeeping above still had to run for this
                // row so the rows that follow land on the right roll number — see the method javadoc —
                // but nothing about this particular child is written again.
                continue;
            }

            List<Guardian> guardians = DemoRoster.guardiansOf(child);
            Guardian onThisRow = guardians.isEmpty() ? null : guardians.getFirst();
            if (guardians.size() > 1 && child.family() != null) {
                admissionNumberOfDualGuardianFamily.put(child.family(), admissionNumber);
            }

            csv.append(csvField(admissionNumber))
                    .append(',')
                    .append(csvField(child.fullName()))
                    .append(',')
                    .append(dateOfBirth)
                    .append(',')
                    .append(child.gender())
                    .append(",ACTIVE,")
                    .append(admittedOn)
                    .append(',')
                    .append(csvField(section.className()))
                    .append(',')
                    .append(csvField(section.sectionName()))
                    .append(',')
                    .append("%02d".formatted(roll))
                    .append(',')
                    .append(onThisRow == null ? "" : csvField(onThisRow.fullName()))
                    .append(',')
                    .append(onThisRow == null ? "" : onThisRow.phone())
                    .append(',')
                    .append(onThisRow == null ? "" : onThisRow.relation())
                    .append(',')
                    .append(onThisRow == null || onThisRow.email() == null ? "" : csvField(onThisRow.email()))
                    .append(',')
                    .append(onThisRow == null ? "" : onThisRow.primary())
                    .append('\n');
        }
        return csv.toString();
    }

    /** RFC 4180 quoting, applied defensively — nothing this roster generates actually needs it. */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * The second guardian for every household the roster gives two, linked after the import rather
     * than in it — the CSV format has nowhere to put a second guardian on one row (ADR-0021 §4).
     *
     * <p>A small, fixed number of extra calls rather than a second bulk path: this roster gives both
     * parents to a few dozen children out of six hundred, and finding each by the admission number the
     * import just wrote, then calling the same {@code /api/guardians} and
     * {@code /api/students/{id}/guardians} endpoints the small seed always used, costs a handful of
     * requests rather than a second file format.
     */
    private static int linkSecondGuardians(SeedApiClient api, Map<String, String> admissionNumberOfFamily) {
        int linked = 0;
        for (Map.Entry<String, String> household : admissionNumberOfFamily.entrySet()) {
            List<Guardian> guardians = DemoRoster.families().get(household.getKey());
            if (guardians == null || guardians.size() < 2) {
                continue;
            }
            Guardian second = guardians.get(1);
            UUID studentId = findByAdmissionNumber(api, household.getValue());

            Map<String, Object> guardianBody = new LinkedHashMap<>();
            guardianBody.put("fullName", second.fullName());
            guardianBody.put("phone", second.phone());
            guardianBody.put("occupation", second.occupation());
            if (second.email() != null) {
                guardianBody.put("email", second.email());
            }
            UUID guardianId = idOf(api.postForData("/api/guardians", guardianBody));
            api.post(
                    "/api/students/" + studentId + "/guardians",
                    new LinkedHashMap<>(Map.of(
                            "guardianId", guardianId.toString(),
                            "relation", second.relation(),
                            "primary", second.primary())));
            linked++;
        }
        if (linked > 0) {
            log.info("Linked a second guardian for {} student(s) whose demo household has two", linked);
        }
        return linked;
    }

    /**
     * Finds the student the import just created, by the admission number this seeder gave it.
     *
     * <p>The import's report carries counts, not the ids it created (ADR-0021's report is a summary
     * of a file, not a list of what it wrote), so a student needing a second guardian is found the way
     * the register screen finds anyone: {@code GET /api/students?q=}. An admission number is unique
     * and this search matches it exactly, so one result is always the whole answer.
     */
    private static UUID findByAdmissionNumber(SeedApiClient api, String admissionNumber) {
        JsonNode content = api.get("/api/students?q=" + URLEncoder.encode(admissionNumber, StandardCharsets.UTF_8))
                .path("data")
                .path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new IllegalStateException(
                    "The demo seed could not find the student it just imported by admission number");
        }
        return idOf(content.get(0));
    }

    /**
     * The first thing wrong with a rejected import, from the report's own {@code errors} list — a row
     * number and a column name, never a value (ADR-0021, ADR-0014).
     */
    private static String describeFirstProblem(JsonNode report) {
        JsonNode errors = report.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return "the report named no specific problem";
        }
        JsonNode first = errors.get(0);
        return "row " + first.path("row").asInt() + ", column '"
                + first.path("column").asText() + "': " + first.path("message").asText();
    }

    /**
     * A date of birth that matches the rung the child is on, varied so no two records look copied.
     *
     * <p>Nursery is three, and every class above adds a year. The month and day move with the
     * child's position in the roster, which keeps every date comfortably in the past and stops six
     * hundred students sharing one birthday.
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
     * and every one of the six hundred children stay out of the log, because AGENTS rule 9 does not
     * have an exception for invented people — the habit is what protects the real ones.
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
                   The roster came in as one file, through the same bulk import a real school's
                   onboarding uses (ADR-0021) — see the audit log for STUDENTS_IMPORTED.
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

    private record SeedSection(int rung, String className, String sectionName, UUID sectionId) {}

    private record AcademicYear(String name, LocalDate startsOn, LocalDate endsOn) {}

    /** The school's current academic session, read back by {@link #currentSession} for a top-up. */
    private record SeedSession(UUID id, AcademicYear year) {}
}
