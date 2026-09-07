package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A role's complete replacement permission set, not a delta.
 *
 * <p>A full replacement rather than add/remove lists because the client already has the current
 * set — it came from {@code GET /api/access/roles} — and a delta invites the two forms drifting out
 * of sync with what is actually on screen. Only the permissions being <em>added</em> (present here,
 * absent from the role today) need to already be held by the acting account
 * ({@code AccessGuardrails#requireHeldByActor}); removing one is never guarded, because taking
 * access away cannot escalate anything.
 */
public record UpdateRolePermissionsRequest(
        @Classification(Tier.INTERNAL) @NotNull List<@NotBlank String> permissions) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
