package in.chalkbase.academics.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * How another module resolves an academic session, a class or a section it points at.
 *
 * <p>This is the first cross-module dependency between two feature modules (ADR-0020), and it is
 * the shape that dependency is allowed to take. {@code student} names a session and a section on
 * every enrolment and has to be able to say which year and which class that is; without this it
 * would either import {@code academics.domain} — which {@code ModularityTests} refuses — or join
 * across the tables, which the module map forbids for the same reason.
 *
 * <p>Read-only, and deliberately so. Nothing here creates, edits or retires anything: a module that
 * needs the academic structure changed asks a person to change it, not another module. Adding a
 * write method to this interface would make {@code academics} answerable for edits it never
 * validated.
 *
 * <p>Every method is scoped to the school bound to this request, like everything else — the
 * connection's {@code search_path} selects the schema (ADR-0011). An id belonging to another school
 * is simply absent here, which is why these return {@link Optional} and a map rather than throwing:
 * "not in this school" is an ordinary answer, and what to do about it belongs to the caller.
 *
 * <p>The batch methods exist so a caller rendering a page of rows makes one call rather than one
 * per row. They return a map keyed by id, and an id that resolves to nothing is simply absent from
 * it rather than mapped to null.
 */
public interface AcademicsLookup {

    /** The year the school says it is in, or empty if it has not said. */
    Optional<AcademicSessionRef> currentSession();

    /** One academic year of this school, or empty. */
    Optional<AcademicSessionRef> session(UUID sessionId);

    /** One section of this school with the class it divides, or empty. */
    Optional<SectionRef> section(UUID sectionId);

    /** Several years at once, keyed by id. Unknown ids are absent from the map. */
    Map<UUID, AcademicSessionRef> sessions(Collection<UUID> sessionIds);

    /** Several sections at once, keyed by id. Unknown ids are absent from the map. */
    Map<UUID, SectionRef> sections(Collection<UUID> sectionIds);
}
