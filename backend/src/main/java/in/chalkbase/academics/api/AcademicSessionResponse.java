package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.AcademicSession;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
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
public record AcademicSessionResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) LocalDate startsOn,
        @Classification(Tier.INTERNAL) LocalDate endsOn,
        @Classification(Tier.INTERNAL) boolean current) {

    public static AcademicSessionResponse of(AcademicSession session) {
        return new AcademicSessionResponse(
                session.getId(), session.getName(), session.getStartsOn(), session.getEndsOn(), session.isCurrent());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
