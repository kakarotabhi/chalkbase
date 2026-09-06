package in.chalkbase.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * ADR-0014 in force. This is the test the ADR promises: "a test that fails the build when a DTO
 * field is unclassified".
 *
 * <p><strong>If one of these failed for you, you have not broken the test.</strong> You added or
 * changed a DTO and the classification did not come with it. Each failure message names the record
 * and the component; the fix is a one-line annotation, not a change here.
 *
 * <p>What is checked, and why each check exists:
 *
 * <ol>
 *   <li>Every component of every DTO record declares a {@link Classification}. Unannotated is not
 *       "probably fine" — {@link Classified} renders it {@code <UNCLASSIFIED>} and this fails the
 *       build, because the alternative is discovering the omission in a log file in production.
 *   <li>Every such record overrides {@code toString}. A record's generated {@code toString} prints
 *       every component, so without the override {@code log.info("saving {}", dto)} is a data leak.
 *   <li>For a populated instance, {@code toString} contains no {@code CONFIDENTIAL} or
 *       {@code RESTRICTED} value. This is the check that would actually catch a mistake.
 *   <li>{@link Classified} itself fails closed on an unannotated component.
 * </ol>
 */
class ClassificationTests {

    /**
     * The DTOs under the rule.
     *
     * <p>Two ways in, because "the DTO" is a role rather than a directory. Anything in an
     * {@code api} package is a boundary DTO by {@code AGENTS.md} rule 4 and is in whether or not
     * anyone remembered to annotate it — that is the whole point. And any record that already
     * carries one {@link Classification} anywhere in the tree is in too, so a half-annotated record
     * outside an {@code api} package (an audit response, a query object) is caught rather than
     * quietly exempt.
     */
    private static final List<Class<?>> CLASSIFIED_DTOS = scanForDtos();

    // ── 1. Everything is classified ──────────────────────────────────────────────────────────

    @Test
    void everyDtoComponentDeclaresAClassification() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> dto : CLASSIFIED_DTOS) {
            for (RecordComponent component : dto.getRecordComponents()) {
                if (Classified.tierOf(component) == null) {
                    offenders.add(dto.getSimpleName() + "." + component.getName() + "  ("
                            + component.getType().getSimpleName() + ")");
                }
            }
        }

        assertThat(offenders)
                .withFailMessage("""
                        %d DTO component(s) have no @Classification (ADR-0014):

                        %s

                        Add one to each, on the record component:

                            public record StudentSummary(
                                    @Classification(Tier.INTERNAL) UUID id,
                                    @Classification(Tier.CONFIDENTIAL) String fullName) { ... }

                        Which tier: a child's or a guardian's name, date of birth, address, phone,
                        email, photograph, admission number, marks, fee amounts or attendance is
                        CONFIDENTIAL. Health, caste, religion, disability, guardian income or an
                        Aadhaar/APAAR reference is RESTRICTED. Class and section structure, roles,
                        timetables, ids, codes, statuses and timestamps are INTERNAL. Only
                        deliberately published school data is PUBLIC. When it is arguable, pick the
                        more protective tier — an over-classified field costs a log line, an
                        under-classified one costs a child's data.""", offenders.size(), bullets(offenders))
                .isEmpty();
    }

    // ── 2. Every DTO redacts its own toString ────────────────────────────────────────────────

    @Test
    void everyDtoOverridesToStringWithClassifiedDescribe() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> dto : CLASSIFIED_DTOS) {
            if (isCompilerGenerated(toStringOf(dto))) {
                offenders.add(dto.getName() + " — no override; the record's generated toString prints every field");
                continue;
            }
            Object populated = Populate.instance(dto);
            String rendered = populated.toString();
            String expected = Classified.describe(populated);
            if (!rendered.equals(expected)) {
                offenders.add(dto.getName() + " — toString does not delegate to Classified.describe."
                        + "\n      it renders: " + rendered + "\n      describe:   " + expected);
            }
        }

        assertThat(offenders)
                .withFailMessage("""
                        %d DTO record(s) do not render through Classified (ADR-0014):

                        %s

                        A record's generated toString prints every component, so one
                        log.info("saving {}", dto) — or one exception message, or one debugger
                        step — publishes the lot. Add this to each, verbatim:

                            /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
                            @Override
                            public String toString() {
                                return Classified.describe(this);
                            }""", offenders.size(), bullets(offenders))
                .isEmpty();
    }

    // ── 3. No confidential value survives toString ───────────────────────────────────────────

    @Test
    void noConfidentialOrRestrictedValueAppearsInToString() {
        List<String> leaks = new ArrayList<>();
        List<String> unrendered = new ArrayList<>();

        for (Class<?> dto : CLASSIFIED_DTOS) {
            Object populated = Populate.instance(dto);
            String rendered = populated.toString();

            for (RecordComponent component : dto.getRecordComponents()) {
                Tier tier = Classified.tierOf(component);
                if (tier == null || !tier.isRedacted()) {
                    continue;
                }

                String marker = component.getName() + "=<" + tier.name() + ">";
                if (!rendered.contains(marker)) {
                    unrendered.add(dto.getSimpleName() + "." + component.getName() + " — expected " + marker + ", got: "
                            + rendered);
                }

                String value = Populate.read(populated, component);
                if (isDistinctive(value, dto) && rendered.contains(value)) {
                    leaks.add(dto.getSimpleName() + "." + component.getName() + " (" + tier + ") leaked " + value
                            + " into: " + rendered);
                }
            }
        }

        assertThat(leaks).withFailMessage("""
                        %d Confidential or Restricted value(s) reached toString (ADR-0014):

                        %s

                        Each record above was built with a sentinel value in every component and its
                        toString still contained one. A record whose toString does this puts a
                        child's data into every log line, error message and stack-trace-adjacent
                        string that touches it. Either the component's tier is wrong, or the
                        toString override is not Classified.describe(this).""", leaks.size(), bullets(leaks)).isEmpty();

        assertThat(unrendered)
                .withFailMessage("""
                        %d redacted component(s) did not render as their tier (ADR-0014):

                        %s

                        Classified.describe writes `name=<TIER>` for every CONFIDENTIAL and
                        RESTRICTED component. Not seeing it means the record's toString is not
                        Classified.describe(this) — so nothing is redacting it.""", unrendered.size(), bullets(unrendered))
                .isEmpty();
    }

    // ── 4. Classified itself fails closed ────────────────────────────────────────────────────

    /** A component with no annotation is never rendered as its value. Fail closed. */
    record Unclassified(
            @Classification(Tier.INTERNAL) String classified,
            String forgotten,
            @Classification(Tier.CONFIDENTIAL) String secret) {}

    @Test
    void describeRendersAnUnannotatedComponentAsUnclassified() {
        String rendered = Classified.describe(new Unclassified("visible", "Zx1SentinelZx", "Zx2SentinelZx"));

        assertThat(rendered)
                .isEqualTo("Unclassified[classified=visible, forgotten=<UNCLASSIFIED>, secret=<CONFIDENTIAL>]");
        assertThat(rendered).doesNotContain("Zx1SentinelZx").doesNotContain("Zx2SentinelZx");
    }

    @Test
    void describeHandlesNullsAndRefusesANonRecord() {
        assertThat(Classified.describe(new Unclassified(null, null, null)))
                .isEqualTo("Unclassified[classified=null, forgotten=<UNCLASSIFIED>, secret=<CONFIDENTIAL>]");
        assertThat(Classified.describe(null)).isEqualTo("null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Classified.describe("not a record"))
                .withMessageContaining("not a record");
    }

    @Test
    void everyTierKnowsWhetherItMayBeLogged() {
        assertThat(Tier.RESTRICTED.isRedacted()).isTrue();
        assertThat(Tier.CONFIDENTIAL.isRedacted()).isTrue();
        assertThat(Tier.INTERNAL.isRedacted()).isFalse();
        assertThat(Tier.PUBLIC.isRedacted()).isFalse();
    }

    /** Nothing is Restricted yet. When the first one lands, encryption at rest lands with it. */
    @Test
    void noRestrictedDataHasBeenIntroducedWithoutEncryption() {
        List<String> restricted = new ArrayList<>();
        for (Class<?> dto : CLASSIFIED_DTOS) {
            for (RecordComponent component : dto.getRecordComponents()) {
                if (Classified.tierOf(component) == Tier.RESTRICTED) {
                    restricted.add(dto.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertThat(restricted)
                .withFailMessage(
                        """
                        %s is classified RESTRICTED, and ADR-0014 requires more of that tier than
                        this slice builds:

                        %s

                        Restricted data must also be encrypted at rest, audited on every read, and
                        masked in the UI behind an explicit permission. None of that exists yet.
                        Do not ship the field until it does — then delete this test.""",
                        restricted.size() == 1 ? "A component" : restricted.size() + " components", bullets(restricted))
                .isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private static String bullets(List<String> lines) {
        return lines.stream()
                .map(line -> "  - " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static Method toStringOf(Class<?> type) {
        try {
            return type.getDeclaredMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("A record always declares toString", e);
        }
    }

    /**
     * A record's implicitly declared {@code toString} is {@code public final} (JLS 8.10.3); an
     * override written by hand is {@code public}. That modifier is the only thing at runtime that
     * tells the two apart, and it matters because a record whose every component happens to be
     * INTERNAL renders identically either way — so the delegation check alone would let such a
     * record through, until someone added a Confidential field to it.
     */
    private static boolean isCompilerGenerated(Method toString) {
        return Modifier.isFinal(toString.getModifiers());
    }

    /**
     * Whether a populated value's string form is specific enough for "this must not appear" to mean
     * anything. {@code true} and {@code 0} appear in half the renderings of half the records, so an
     * absence assertion on them would pass or fail for the wrong reason; those components are
     * covered by the {@code name=<TIER>} marker assertion instead.
     */
    private static boolean isDistinctive(String value, Class<?> owner) {
        return value != null
                && value.length() >= 6
                && !value.equals("null")
                && !owner.getSimpleName().contains(value);
    }

    private static List<Class<?>> scanForDtos() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        // Production code only. Test fixtures are not boundary DTOs and must not be held to the rule.
        scanner.addExcludeFilter(
                (reader, factory) -> reader.getResource().getDescription().contains("test-classes"));

        List<Class<?>> dtos = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("in.chalkbase")) {
            String name = candidate.getBeanClassName();
            if (name == null) {
                continue;
            }
            Class<?> type;
            try {
                // initialize=false: reading annotations must not run anyone's static initialiser.
                type = Class.forName(name, false, ClassificationTests.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
            if (!type.isRecord()) {
                continue;
            }
            boolean atTheBoundary = type.getPackageName().endsWith(".api");
            boolean partlyClassified = false;
            for (RecordComponent component : type.getRecordComponents()) {
                partlyClassified |= Classified.tierOf(component) != null;
            }
            if (atTheBoundary || partlyClassified) {
                dtos.add(type);
            }
        }

        assertThat(dtos)
                .withFailMessage("Found no DTO records under in.chalkbase — the scan is broken, not the code.")
                .isNotEmpty();
        dtos.sort(Comparator.comparing(Class::getName));
        return List.copyOf(dtos);
    }

    /**
     * Builds a record with a distinctive value in every component, so that "this value must not
     * appear in toString" is a question with an answer.
     */
    private static final class Populate {

        private static final AtomicInteger NEXT = new AtomicInteger();

        static Object instance(Class<?> type) {
            return build(type, 0);
        }

        static String read(Object record, RecordComponent component) {
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                return String.valueOf(accessor.invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + component, e);
            }
        }

        private static Object build(Class<?> type, int depth) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                arguments[i] = value(components[i].getType(), type, components[i].getName(), depth);
            }
            try {
                Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
                canonical.setAccessible(true);
                return canonical.newInstance(arguments);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Could not build a populated " + type.getName()
                                + ". The canonical constructor rejected the fixture values; teach"
                                + " ClassificationTests.Populate about this record's component types.",
                        e);
            }
        }

        private static Object value(Class<?> type, Class<?> owner, String component, int depth) {
            int n = NEXT.incrementAndGet();
            String sentinel = "Zx" + n + "SentinelZx";

            if (type == String.class || type == Object.class || type == CharSequence.class) {
                return sentinel;
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.TRUE;
            }
            if (type == int.class || type == Integer.class) {
                return 700_000_000 + n;
            }
            if (type == long.class || type == Long.class) {
                return 800_000_000_000L + n;
            }
            if (type == double.class || type == Double.class) {
                return 900_000_000d + n;
            }
            if (type == BigDecimal.class) {
                return BigDecimal.valueOf(900_000_000L + n, 2);
            }
            if (type == UUID.class) {
                return UUID.randomUUID();
            }
            if (type == LocalDate.class) {
                return LocalDate.of(1999, 1, 1).plusDays(n);
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.of(1999, 1, 1, 0, 0).plusSeconds(n);
            }
            if (type == LocalTime.class) {
                return LocalTime.of(1, 0).plusSeconds(n);
            }
            if (type == Instant.class) {
                return Instant.parse("1999-01-01T00:00:00Z").plusSeconds(n);
            }
            if (type.isEnum()) {
                return type.getEnumConstants()[0];
            }
            // Generics are erased, so a List<StudentGuardian> takes a List<String> here. Only
            // toString is ever called on it, and a sentinel element is what makes the leak visible.
            if (type == List.class || type == Iterable.class || type == Set.class) {
                return type == Set.class ? Set.of(sentinel) : List.of(sentinel);
            }
            if (type == Map.class) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("field", sentinel);
                return map;
            }
            if (type.isRecord() && depth < 4) {
                return build(type, depth + 1);
            }
            throw new IllegalStateException("ClassificationTests cannot populate " + owner.getSimpleName() + "."
                    + component + " of type " + type.getName()
                    + ". Add a case for it in ClassificationTests.Populate#value — the check that no"
                    + " Confidential value reaches toString is only as good as the fixture.");
        }
    }
}
