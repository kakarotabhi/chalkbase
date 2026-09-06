package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.SchoolClass;
import in.chalkbase.academics.domain.Section;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One rung of the school's ladder, with the sections inside it.
 *
 * <p>Sections come nested rather than from a second endpoint because they are never useful on their
 * own: "Section A" means nothing without the class it divides, and the one screen that shows them
 * shows the whole ladder.
 *
 * @param sequence where this rung sits. The client renders in this order and sends it back, as a
 *     whole list, to reorder.
 * @param active false for a class the school has retired. Returned flagged rather than hidden.
 */
public record SchoolClassResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) String name,
        @Classification(Tier.INTERNAL) int sequence,
        @Classification(Tier.INTERNAL) boolean active,
        @Classification(Tier.INTERNAL) List<SectionResponse> sections) {

    /** Sorted here as well as by {@code @OrderBy}, so the contract holds however the rows were loaded. */
    public static SchoolClassResponse of(SchoolClass schoolClass) {
        return new SchoolClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSequence(),
                schoolClass.isActive(),
                schoolClass.getSections().stream()
                        .sorted(Comparator.comparing(Section::getName))
                        .map(SectionResponse::of)
                        .toList());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
