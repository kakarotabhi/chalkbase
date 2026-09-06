package in.chalkbase.academics.api;

import in.chalkbase.academics.domain.Section;
import java.util.UUID;

/**
 * A section, and the class it divides, as another module sees it.
 *
 * <p>Carries its class inline because a section id alone is never enough for a caller: "A" means
 * nothing without "Class 5", and a module that had to ask twice would either make two calls per row
 * or reach into {@code academics.domain} for the second half — which is exactly what the named
 * interface exists to prevent.
 *
 * <p>Deliberately not {@link SectionResponse}, which is nested inside its class on the wire and so
 * has no class id at all.
 *
 * @param active false for a section the school has retired. Returned rather than hidden: an
 *     enrolment made before a section was retired still names it, and the caller has to be able to
 *     render that row.
 */
public record SectionRef(UUID id, String name, UUID classId, String className, boolean active) {

    public static SectionRef of(Section section) {
        return new SectionRef(
                section.getId(),
                section.getName(),
                section.getSchoolClass().getId(),
                section.getSchoolClass().getName(),
                section.isActive());
    }
}
