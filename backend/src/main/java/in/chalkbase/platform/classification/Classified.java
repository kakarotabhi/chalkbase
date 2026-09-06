package in.chalkbase.platform.classification;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

/**
 * Renders a DTO record as a string with its {@link Tier#isRedacted() redacted} components replaced
 * by their tier.
 *
 * <p>Every record in an {@code api} package overrides {@code toString} with
 * {@code Classified.describe(this)}, and that one line is what makes ADR-0014 mechanical rather than
 * documentary. A record's generated {@code toString} prints every component, so
 * {@code log.info("saving {}", dto)} is a data leak; with the override it prints
 * {@code StudentDetail[id=01a0…, fullName=<CONFIDENTIAL>, dateOfBirth=<CONFIDENTIAL>,
 * status=ACTIVE]} instead.
 *
 * <h2>Three rules, and the reasons for them</h2>
 *
 * <ul>
 *   <li><strong>Fail closed.</strong> A component with no {@link Classification} renders
 *       {@value #UNCLASSIFIED}, never its value. Forgetting the annotation must not silently expose
 *       data — that failure mode is the one this whole mechanism exists to remove.
 *   <li><strong>No recursion into nested records.</strong> A nested DTO's own {@code toString}
 *       already redacts itself; describing it again here would bypass its declaration. So an
 *       {@code INTERNAL} component holding a record prints that record's {@code toString}, and a
 *       collection prints its elements' {@code toString}s.
 *   <li><strong>Reflection, not code generation.</strong> No annotation processor, no Lombok, no
 *       agent. This runs on a debugging path, not a hot one, and the per-class reflection is cached.
 * </ul>
 */
public final class Classified {

    /** What an unannotated component renders as. Fail closed: never the value. */
    public static final String UNCLASSIFIED = "<UNCLASSIFIED>";

    /** What a component renders as when its accessor cannot be read. Also never the value. */
    public static final String UNREADABLE = "<UNREADABLE>";

    /**
     * Per-record-class reflection, resolved once. {@link ClassValue} rather than a map so the cache
     * cannot outlive the class loader that produced the entries.
     */
    private static final ClassValue<Component[]> COMPONENTS = new ClassValue<>() {
        @Override
        protected Component[] computeValue(Class<?> type) {
            return Arrays.stream(type.getRecordComponents()).map(Component::of).toArray(Component[]::new);
        }
    };

    private Classified() {}

    /**
     * Renders {@code record} as {@code TypeName[component=value, component=<TIER>]}.
     *
     * @param record any record instance, normally {@code this} from an overridden {@code toString}
     * @return the redacted description; never contains the value of a {@code RESTRICTED} or
     *     {@code CONFIDENTIAL} component, and never the value of an unannotated one
     * @throws IllegalArgumentException if {@code record} is not a record. Callers are
     *     {@code toString} overrides on records, so this can only be reached by misuse.
     */
    public static String describe(Object record) {
        if (record == null) {
            return "null";
        }
        Class<?> type = record.getClass();
        if (!type.isRecord()) {
            throw new IllegalArgumentException(
                    "Classified.describe is for record DTOs; " + type.getName() + " is not a record");
        }
        Component[] components = COMPONENTS.get(type);
        StringBuilder out = new StringBuilder(type.getSimpleName()).append('[');
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(components[i].name()).append('=').append(components[i].render(record));
        }
        return out.append(']').toString();
    }

    /**
     * The tier declared on a record component, or null when it declares none.
     *
     * <p>Used by {@code ClassificationTests} to name the offending components, and by anything that
     * needs to decide by tier rather than by rendered string.
     */
    public static Tier tierOf(RecordComponent component) {
        Classification declared = component.getAnnotation(Classification.class);
        return declared == null ? null : declared.value();
    }

    /** One component's name, declared tier and accessor, resolved once per record class. */
    private record Component(String name, Tier tier, Method accessor) {

        static Component of(RecordComponent component) {
            Method accessor = component.getAccessor();
            try {
                accessor.setAccessible(true);
            } catch (RuntimeException ignored) {
                // A package-private record in a module that does not open itself. The accessor is
                // then unusable and render() reports <UNREADABLE> rather than failing a log call.
            }
            return new Component(component.getName(), tierOf(component), accessor);
        }

        String render(Object record) {
            if (tier == null) {
                return UNCLASSIFIED;
            }
            if (tier.isRedacted()) {
                return "<" + tier.name() + ">";
            }
            try {
                return String.valueOf(accessor.invoke(record));
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
                // toString is called from logging and from debuggers. It must not throw, and it
                // must not fall back to anything that could be the value.
                return UNREADABLE;
            }
        }
    }
}
