package in.chalkbase.school.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.school.domain.Board;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Everything a fresh deployment needs to bring one school online (ADR-0024): the school itself,
 * plus its first sign-in.
 *
 * <p>Deliberately not a nested {@link CreateSchoolRequest} — the same six school fields, repeated,
 * rather than composed — because the two requests answer different questions ("register a campus a
 * platform operator already has an account for" versus "there is no account yet, make one") and
 * {@code contracts/} should say so as two independent shapes rather than one optionally wrapping
 * the other.
 *
 * <p>No password field: {@code SchoolService#bootstrap} generates one, the same way
 * {@code CreateUserAccountRequest} does, and returns it exactly once in
 * {@link SchoolBootstrapResponse}. A field here would invite a fixed or memorable value, which
 * ADR-0024 rules out.
 */
public record BootstrapSchoolRequest(
        @Classification(Tier.PUBLIC) @NotBlank @Size(max = 32) String code,

        @Classification(Tier.PUBLIC) @NotBlank @Size(max = 200) String name,
        /** The school's PostgreSQL schema. Chosen once, at bootstrap, and never changed. */
        @Classification(Tier.INTERNAL)
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{2,62}$", message = "must be lowercase letters, digits and underscores") String schemaName,

        @Classification(Tier.PUBLIC) @NotNull Board board,
        @Classification(Tier.PUBLIC) @Size(max = 100) String city,
        @Classification(Tier.PUBLIC) @Size(max = 100) String state,
        /** Username only, matching {@code CreateUserAccountRequest} — the first release signs everyone in this way. */
        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 100) String adminUsername,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String adminDisplayName) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
