package in.chalkbase.school.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.school.domain.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSchoolRequest(
        @Classification(Tier.PUBLIC) @NotBlank @Size(max = 32) String code,

        @Classification(Tier.PUBLIC) @NotBlank @Size(max = 200) String name,
        /** The school's PostgreSQL schema. Chosen once, at onboarding, and never changed. */
        @Classification(Tier.INTERNAL)
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,62}$", message = "must be lowercase letters, digits and underscores") String schemaName,

        @Classification(Tier.PUBLIC) @NotNull Board board,
        @Classification(Tier.PUBLIC) @Size(max = 100) String city,
        @Classification(Tier.PUBLIC) @Size(max = 100) String state) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
