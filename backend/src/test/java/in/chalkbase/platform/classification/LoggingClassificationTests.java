package in.chalkbase.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The other half of ADR-0014's enforcement gap, and item 4 of {@code docs/status.md}'s "What to do
 * next" / second bullet under "Blocking the first real school".
 *
 * <p>{@link Classification} and {@link Classified#describe(Object)} stop {@code log.info("saving
 * {}", dto)} — the redacting {@code toString} in {@code ClassificationTests} proves that. Neither
 * stops {@code log.info("saving {}", dto.fullName())}. Reading the value out through the record's
 * own generated accessor bypasses {@code toString} completely; the accessor returns the raw value,
 * exactly as if {@code Classified} did not exist. {@code AGENTS.md} rule 9 and ADR-0014 both say a
 * Confidential or Restricted value is never logged and never put in an error message — this is the
 * build-failing test that makes that a fact about the code rather than a fact about who remembered.
 *
 * <h2>What counts as a logger call</h2>
 *
 * Three sinks, matching what {@code AGENTS.md} names explicitly:
 *
 * <ul>
 *   <li>An {@link org.slf4j.Logger} method: {@code trace}, {@code debug}, {@code info}, {@code
 *       warn}, {@code error}. This is "log at any level" from ADR-0014's Restricted row, taken
 *       literally.
 *   <li>{@link String#format}, because a formatted string built for a log line or a message is the
 *       same leak one call further removed.
 *   <li>The constructor of any {@link Throwable}, because {@code AGENTS.md} says a Confidential or
 *       Restricted value is never put in an error message, and {@code new
 *       SomeException("... " + dto.fullName())} is exactly that.
 * </ul>
 *
 * <p><strong>Not covered, deliberately:</strong> SLF4J's fluent builder ({@code
 * log.atInfo().log(...)}) is not in this codebase today (grep the tree — every logger is the
 * five-method interface, declared the same way {@code Classified}'s own javadoc shows: {@code
 * private static final Logger log = LoggerFactory.getLogger(...)}, no Lombok, per {@code
 * Classified}'s own note that this project uses neither). Add it here the day the codebase adds a
 * fluent call, rather than guessing its shape now.
 *
 * <h2>How a call resolves to a classification</h2>
 *
 * {@link Classification} targets {@link java.lang.annotation.ElementType#RECORD_COMPONENT} only —
 * see that annotation's own javadoc for why — which means it is never visible on the accessor
 * <em>method</em> the way a normal method annotation would be; ArchUnit's call graph can name the
 * method a call resolves to, but has nothing to read off it. So this test builds its own map the
 * same way {@code ClassificationTests} builds {@code CLASSIFIED_DTOS}: for every DTO record already
 * on that list, reflect over its components, and for every one classified {@code CONFIDENTIAL} or
 * {@code RESTRICTED}, record (declaring record, accessor name). A call is "a redacted accessor" when
 * its target's declaring class and method name match an entry in that map and it takes no
 * arguments — which is exactly the shape of a record's generated accessor, and cannot be confused
 * with an overload, because a record accessor cannot be overloaded within its own record.
 *
 * <p><strong>An entity getter is out of scope, on purpose.</strong> ADR-0022 §2 keeps {@code
 * @Classification} on the DTO and never the entity — the DTO is the mandatory boundary under {@code
 * AGENTS.md} rule 4, the entity is not. An entity has no {@link Classification} anywhere on it, so
 * there is nothing in {@link ClassificationTests#CLASSIFIED_DTOS} to match its getters against, and
 * this test cannot see them at all. If {@code StudentEntity.getFullName()} ever reaches a logger
 * directly, this test stays green. That is a real gap; closing it would mean annotating the entity,
 * which is the decision ADR-0022 §2 already made the other way, so it is recorded here instead of
 * silently accepted.
 *
 * <h2>Why line number, not real data flow</h2>
 *
 * ArchUnit's import model is a call graph — "this method calls that method, at this source line" —
 * not a dataflow graph. It has no notion of "the value returned by call A became the argument passed
 * to call B" once a local variable sits between them, and building that would need real bytecode
 * dataflow analysis, which is not a dependency this change adds (AGENTS.md rule 8: ask first, and
 * this does not clear that bar for one build-time check). So the rule this test enforces is coarser
 * and deliberately conservative:
 *
 * <blockquote>A production method that calls a redacted accessor and calls one of the three sinks
 * above <em>on the same source line</em> is a violation.</blockquote>
 *
 * That catches {@code log.info("saving {}", dto.fullName())} and {@code new
 * IllegalStateException("no guardian for " + student.fullName())} — the accessor call and the sink
 * call, and any string concatenation between them, all compile onto the one line the {@code throw}
 * or {@code log} statement is written on. It does not catch:
 *
 * <ul>
 *   <li>{@code String name = dto.fullName(); log.info("saving {}", name);} — two lines, a local
 *       variable in between. The value still reaches the log; this rule does not trace {@code name}
 *       back to where it came from.
 *   <li>The same call wrapped onto a second line by a formatter, e.g. {@code log.info("saving {}",
 *       \n    dto.fullName());} — same reasoning, different cause.
 * </ul>
 *
 * <p>A missed case is a gap to close later. A false positive is a reason this whole check gets
 * disabled within a week — {@code ClassificationTests}'s own comments make exactly this argument
 * about itself. This test is written to fail closed on precision: it flags far fewer call sites than
 * a real dataflow check would, and every one it flags is a call that genuinely sits on the same line
 * as a sink. {@link #ruleCatchesADirectAccessorPassedToASink()} and {@link
 * #ruleDoesNotFlagAnAccessorReadForAnUnrelatedReason()} below exercise both edges of that trade-off
 * against fixtures built for the purpose, because a claim like this is worth a fixture and not just
 * a paragraph.
 */
class LoggingClassificationTests {

    private static final Set<String> LOGGER_METHODS = Set.of("trace", "debug", "info", "warn", "error");

    /** Every DTO accessor that must never reach a sink, as (declaring record's binary name) -> (component names). */
    private static final Map<String, Set<String>> REDACTED_ACCESSORS =
            redactedAccessorsOf(ClassificationTests.CLASSIFIED_DTOS);

    // ── The real check, against the whole production tree ───────────────────────────────────

    @Test
    void noConfidentialOrRestrictedAccessorReachesALoggerFormatOrExceptionMessage() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("in.chalkbase");

        List<String> violations = findViolations(production, REDACTED_ACCESSORS);

        assertThat(violations)
                .withFailMessage("""
                        %d place(s) pass a Confidential or Restricted value to a logger, String.format \
                        or an exception message through a DTO accessor (ADR-0014, AGENTS.md rule 9):

                        %s

                        @Classification's redacting toString only stops log.info("saving {}", dto). It \
                        does nothing about log.info("saving {}", dto.fullName()) — the accessor reads \
                        the raw value straight past toString. Do not log it, format it, or put it in an \
                        exception message. If the line needs to identify which record it is about, log \
                        an id or another INTERNAL field instead.""", violations.size(), bullets(violations))
                .isEmpty();
    }

    // ── The check proves it catches its own reason for existing ─────────────────────────────

    /**
     * A DTO with one {@code CONFIDENTIAL} component, used only by the two tests below. Never
     * production code — {@code ClassFileImporter.importClasses} takes explicit {@code Class}
     * objects, so this is imported by naming it directly, not by scanning a package.
     */
    private record Fixture(
            @Classification(Tier.CONFIDENTIAL) String secretName) {

        @Override
        public String toString() {
            return Classified.describe(this);
        }
    }

    private static final Map<String, Set<String>> FIXTURE_REDACTED_ACCESSORS =
            Map.of(Fixture.class.getName(), Set.of("secretName"));

    /** Deliberately violates the rule three ways, so the assertion below has three lines to find. */
    private static final class Offender {

        private static final Logger log = LoggerFactory.getLogger(Offender.class);

        static void logsIt(Fixture fixture) {
            log.info("saving {}", fixture.secretName());
        }

        static void formatsIt(Fixture fixture) {
            String.format("saving %s", fixture.secretName());
        }

        static void putsItInAMessage(Fixture fixture) {
            throw new IllegalStateException("cannot save " + fixture.secretName());
        }
    }

    @Test
    void ruleCatchesADirectAccessorPassedToASink() {
        JavaClasses classes = new ClassFileImporter().importClasses(Fixture.class, Offender.class);

        List<String> violations = findViolations(classes, FIXTURE_REDACTED_ACCESSORS);

        assertThat(violations).hasSize(3);
        assertThat(violations)
                .anySatisfy(v -> assertThat(v).contains("logsIt"))
                .anySatisfy(v -> assertThat(v).contains("formatsIt"))
                .anySatisfy(v -> assertThat(v).contains("putsItInAMessage"));
    }

    /**
     * The negative case, covering both edges the class javadoc claims: an accessor read for a
     * legitimate reason does not trip the rule just because a logger is nearby, and the one gap the
     * javadoc admits — a value handed to a logger through a local variable — really does slip
     * through today.
     */
    private static final class SafeUsage {

        private static final Logger log = LoggerFactory.getLogger(SafeUsage.class);

        /** Reads the accessor and logs something, but not on the same line. Must not be flagged. */
        static String capturesAndLogsSeparately(Fixture fixture) {
            log.info("processing a fixture");
            String captured = fixture.secretName();
            return captured.trim();
        }

        /**
         * The documented gap: the value does reach the logger, just through a local variable one
         * line up. This test exists so the javadoc's "does not catch" claim is checked instead of
         * merely asserted — if a future change to this rule starts catching this too, this assertion
         * should change to expect a violation, and the javadoc above it should be corrected to match.
         */
        static void leaksThroughALocalVariableUncaught(Fixture fixture) {
            String name = fixture.secretName();
            log.info("saving {}", name);
        }
    }

    @Test
    void ruleDoesNotFlagAnAccessorReadForAnUnrelatedReason() {
        JavaClasses classes = new ClassFileImporter().importClasses(Fixture.class, SafeUsage.class);

        List<String> violations = findViolations(classes, FIXTURE_REDACTED_ACCESSORS);

        assertThat(violations).isEmpty();
    }

    // ── The check itself ─────────────────────────────────────────────────────────────────────

    private static List<String> findViolations(JavaClasses classes, Map<String, Set<String>> redactedAccessors) {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                violations.addAll(violationsIn(codeUnit, redactedAccessors));
            }
        }
        return violations;
    }

    private static List<String> violationsIn(JavaCodeUnit codeUnit, Map<String, Set<String>> redactedAccessors) {
        Map<Integer, List<JavaCall<?>>> callsByLine = new TreeMap<>();
        for (JavaCall<?> call : codeUnit.getCallsFromSelf()) {
            callsByLine
                    .computeIfAbsent(call.getLineNumber(), unused -> new ArrayList<>())
                    .add(call);
        }

        List<String> violations = new ArrayList<>();
        for (Map.Entry<Integer, List<JavaCall<?>>> line : callsByLine.entrySet()) {
            List<JavaCall<?>> calls = line.getValue();
            List<JavaCall<?>> sinks =
                    calls.stream().filter(LoggingClassificationTests::isSink).toList();
            if (sinks.isEmpty()) {
                continue;
            }
            for (JavaCall<?> call : calls) {
                String accessor = redactedAccessorDescription(call, redactedAccessors);
                if (accessor == null) {
                    continue;
                }
                violations.add(codeUnit.getOwner().getFullName() + "." + codeUnit.getName() + "() line " + line.getKey()
                        + " — " + accessor + " reaches " + describeSink(sinks.getFirst()));
            }
        }
        return violations;
    }

    private static boolean isSink(JavaCall<?> call) {
        AccessTarget target = call.getTarget();
        if (target instanceof AccessTarget.MethodCallTarget methodTarget) {
            JavaClass owner = methodTarget.getOwner();
            if (owner.isAssignableTo(Logger.class) && LOGGER_METHODS.contains(methodTarget.getName())) {
                return true;
            }
            return owner.getFullName().equals(String.class.getName())
                    && methodTarget.getName().equals("format");
        }
        if (target instanceof AccessTarget.ConstructorCallTarget constructorTarget) {
            return constructorTarget.getOwner().isAssignableTo(Throwable.class);
        }
        return false;
    }

    private static String describeSink(JavaCall<?> call) {
        AccessTarget target = call.getTarget();
        if (target instanceof AccessTarget.ConstructorCallTarget constructorTarget) {
            return "new " + constructorTarget.getOwner().getSimpleName() + "(...)";
        }
        return target.getOwner().getSimpleName() + "." + target.getName() + "(...)";
    }

    private static String redactedAccessorDescription(JavaCall<?> call, Map<String, Set<String>> redactedAccessors) {
        if (!(call.getTarget() instanceof AccessTarget.MethodCallTarget methodTarget)) {
            return null;
        }
        if (!methodTarget.getRawParameterTypes().isEmpty()) {
            return null; // a record's generated accessor never takes an argument
        }
        Set<String> components = redactedAccessors.get(methodTarget.getOwner().getFullName());
        if (components == null || !components.contains(methodTarget.getName())) {
            return null;
        }
        return methodTarget.getOwner().getSimpleName() + "." + methodTarget.getName() + "()";
    }

    private static Map<String, Set<String>> redactedAccessorsOf(List<Class<?>> dtos) {
        Map<String, Set<String>> byClass = new HashMap<>();
        for (Class<?> dto : dtos) {
            for (RecordComponent component : dto.getRecordComponents()) {
                Tier tier = Classified.tierOf(component);
                if (tier != null && tier.isRedacted()) {
                    byClass.computeIfAbsent(dto.getName(), unused -> new HashSet<>())
                            .add(component.getName());
                }
            }
        }
        return byClass;
    }

    private static String bullets(List<String> lines) {
        return lines.stream()
                .map(line -> "  - " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
