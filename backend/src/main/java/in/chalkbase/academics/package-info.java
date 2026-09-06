/**
 * Academics: the school's academic structure — its academic sessions, its ladder of classes and the
 * sections inside them.
 *
 * <p>Classes and sections are <strong>structural, not session-scoped</strong> (ADR-0019). "Class 5"
 * and its "Section A" are one row each, facts about the school rather than about a year, and the
 * session appears on whatever references them — enrolment first, and later the class-teacher
 * assignment, which genuinely changes every year.
 *
 * <p>{@code academic_session} lives here rather than in {@code school} because it is the time axis
 * the rest of the academic model hangs off. Leaving it beside the school registry would make every
 * academics query reach across a module boundary for it.
 *
 * <p>Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Academics")
package in.chalkbase.academics;
