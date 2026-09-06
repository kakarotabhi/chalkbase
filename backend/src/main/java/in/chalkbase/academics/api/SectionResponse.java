package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.Section;
import java.util.UUID;

/**
 * One division of a class.
 *
 * <p>Carries no class id: a section is only ever returned inside the class it divides, so repeating
 * the parent's id on every child would be a second answer to a question the shape has already
 * answered.
 *
 * @param active false for a section the school has retired. Returned rather than filtered out — the
 *     client decides how to show it, and the screen that can reactivate it has to be able to see it
 *     (ADR-0019: rows are deactivated, never deleted).
 */
public record SectionResponse(UUID id, String name, boolean active) {

    public static SectionResponse of(Section section) {
        return new SectionResponse(section.getId(), section.getName(), section.isActive());
    }
}
