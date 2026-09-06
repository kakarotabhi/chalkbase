package in.chalkbase.academics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new division of a class. The class is in the path, not in the body — a section outside a class
 * is not a thing.
 */
public record CreateSectionRequest(@NotBlank @Size(max = 20) String name) {}
