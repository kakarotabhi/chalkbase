package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.AcademicSession;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One academic year, as a school's own screens see it.
 *
 * @param id the session's identifier, which an enrolment will later name
 * @param name what the school calls the year, e.g. {@code 2026-27}
 * @param startsOn first day of the year
 * @param endsOn last day of the year, always after {@code startsOn}
 * @param current whether this is the year the school is in. At most one session in a school has
 *     this set, and the database is what guarantees it rather than the application remembering.
 */
public record AcademicSessionResponse(UUID id, String name, LocalDate startsOn, LocalDate endsOn, boolean current) {

    public static AcademicSessionResponse of(AcademicSession session) {
        return new AcademicSessionResponse(
                session.getId(), session.getName(), session.getStartsOn(), session.getEndsOn(), session.isCurrent());
    }
}
