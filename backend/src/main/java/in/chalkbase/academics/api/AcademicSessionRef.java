package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.AcademicSession;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The minimum another module needs to know about an academic year.
 *
 * <p>Deliberately not {@link AcademicSessionResponse}. That is a read model for HTTP clients and
 * will grow fields as the screen does; this is the cross-module contract and stays small, for the
 * same reason {@code school.api.SchoolRef} is not {@code SchoolResponse}.
 *
 * @param name what the school calls the year — "2026-27". Internal under ADR-0014, so it may appear
 *     in a response and in a log; it says nothing about any person.
 * @param startsOn when the year begins, so a caller holding several can order them without asking
 *     again
 * @param current true for the year the school says it is in. At most one session per school has
 *     this set.
 */
public record AcademicSessionRef(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) LocalDate startsOn,
        @Classification(Tier.INTERNAL) boolean current) {

    public static AcademicSessionRef of(AcademicSession session) {
        return new AcademicSessionRef(session.getId(), session.getName(), session.getStartsOn(), session.isCurrent());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
