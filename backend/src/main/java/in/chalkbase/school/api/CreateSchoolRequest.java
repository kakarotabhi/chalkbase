package in.chalkbase.school.api;

import in.chalkbase.school.domain.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSchoolRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 200) String name,
        /** The school's PostgreSQL schema. Chosen once, at onboarding, and never changed. */
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,62}$", message = "must be lowercase letters, digits and underscores") String schemaName,

        @NotNull Board board,
        @Size(max = 100) String city,
        @Size(max = 100) String state) {}
