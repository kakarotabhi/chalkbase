package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.platform.security.ScopeType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "This user holds this role, over this much of the school, for this long" (ADR-0005).
 *
 * <p>{@code scopeId} is required for {@code CAMPUS}, {@code DEPARTMENT}, {@code CLASS},
 * {@code SECTION} and {@code SUBJECT}, and must be absent for {@code SCHOOL} and {@code SELF} — the
 * same rule {@link in.chalkbase.identity.domain.UserRoleGrant} enforces, checked here first so the
 * failure is a clear {@code VAL_001} rather than a database constraint's name. {@code WARD} is
 * rejected outright: a parent's reach is derived from the guardian-of relationship, never assigned
 * (ADR-0005), and {@code ck_user_role_grant_scope} does not even list it as a value the column
 * accepts.
 *
 * <p>Granting a role hands the holder every permission it carries, so the acting account must
 * already hold all of them itself ({@code AccessGuardrails#requireHeldByActor}) — the same guard
 * {@link CreateRoleRequest} and {@link UpdateRolePermissionsRequest} apply, extended to the grant
 * that actually hands the permissions to someone.
 *
 * <p>None of the three optional fields below carry {@code @Schema(nullable = true)}: springdoc's
 * {@code OpenApiConfig#requiredUnlessNullable} only strips that marker from response-only schemas,
 * and this is a request. Absence of {@code @NotNull} already says "may be omitted" correctly here;
 * adding the annotation anyway would leak OpenAPI 3.1's {@code "type": ["string", "null"]} into the
 * exported contract, which {@code OpenApiContractTests} forbids outright.
 */
public record GrantRoleRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID roleId,
        @Classification(Tier.INTERNAL) @NotNull ScopeType scopeType,
        @Classification(Tier.INTERNAL) UUID scopeId,
        @Classification(Tier.INTERNAL) LocalDate validFrom,
        @Classification(Tier.INTERNAL) LocalDate validTo) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
