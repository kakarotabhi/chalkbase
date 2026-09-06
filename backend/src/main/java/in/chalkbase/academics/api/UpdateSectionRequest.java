package in.chalkbase.academics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A section renamed, retired or brought back.
 *
 * <p>The class it divides is not editable. Moving "A" from Class 5 to Class 6 is not an edit to a
 * section — it is a different section, and treating it as an edit would carry every enrolment that
 * names it across without anyone asking for that.
 *
 * <p>{@code active} is boxed and {@code @NotNull} so that a client which omits it is told, rather
 * than silently retiring the section.
 */
public record UpdateSectionRequest(
        @NotBlank @Size(max = 20) String name, @NotNull Boolean active) {}
