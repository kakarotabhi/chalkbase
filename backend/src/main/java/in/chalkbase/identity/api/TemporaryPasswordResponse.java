package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * What an admin password reset hands back: the new school-issued password, shown exactly once.
 *
 * <p>Nothing stores this value once the response is sent — the account row holds only its hash
 * (ADR-0003) — so if it is lost before it reaches whoever it is for, the only recovery is another
 * reset. The target's existing sessions are already gone by the time this response is returned:
 * see {@code UserAccountManagementService#resetPassword}.
 */
public record TemporaryPasswordResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String temporaryPassword) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
