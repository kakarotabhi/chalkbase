package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.Subject;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * A subject this school teaches.
 *
 * <p>Carries no class or section id: a subject is a flat catalogue entry in this build, and which
 * classes teach it is a subject allocation that belongs to the timetable and marks modules once
 * they exist.
 *
 * @param active false for a subject the school has retired. Returned flagged rather than hidden,
 *     for the same reason a retired class is (ADR-0019): the screen that can bring it back has to
 *     be able to see it.
 */
public record SubjectResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) String code,
        @Classification(Tier.INTERNAL) boolean active) {

    public static SubjectResponse of(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getCode(), subject.isActive());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
