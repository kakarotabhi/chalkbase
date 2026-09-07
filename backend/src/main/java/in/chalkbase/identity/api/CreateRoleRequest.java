package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A new, school-owned role (ADR-0005): a name, an optional description, and the permissions it
 * starts with.
 *
 * <p>No {@code code} field — {@code RoleCode} derives one from the name. A shipped template names
 * its own code because it is versioned with the product; a role a school invents has no such
 * identity to type, and asking for one is a field nobody would fill in meaningfully.
 *
 * <p>Every permission listed must already be held by the account creating the role
 * ({@code AccessGuardrails#requireHeldByActor}) — {@code identity:role:manage} is not scoped to
 * particular permissions, so this is what stops a holder handing out one they do not have
 * themselves.
 */
public record CreateRoleRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 120) String name,

        @Schema(nullable = true) @Classification(Tier.INTERNAL) @Size(max = 400) String description,

        @Classification(Tier.INTERNAL) @NotNull List<@NotBlank String> permissions) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
