package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * The account just created, and the temporary password it must be handed along with.
 *
 * <p>This is the only response that ever carries a plaintext password, and it is shown exactly
 * once: nothing stores it, and there is no endpoint that can retrieve it again — only
 * {@code POST .../reset-password}, which issues a new one. Whoever creates the account is
 * responsible for getting it to the person it is for, the same way a school office already hands
 * over a slip of paper.
 */
public record NewUserAccountResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String username,
        @Classification(Tier.CONFIDENTIAL) String displayName,
        @Classification(Tier.CONFIDENTIAL) String temporaryPassword) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
