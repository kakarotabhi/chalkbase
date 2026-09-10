package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * Enough of an account to list it. No identifier, no credential, no login history — everything here
 * is already on screen wherever this list is shown.
 *
 * <p>{@code locked} is a fact, not a timestamp: it is {@code UserAccount.isLocked(Instant.now())},
 * computed once per response rather than handing back the raw {@code lockedUntil} it is derived
 * from. A lockout expires on its own (see that method's own Javadoc), so a value in the past means
 * the account is not locked any more — exposing the instant instead would make every caller redo
 * that same "is it still in the future" comparison against its own clock, on every render, and a
 * client running behind or in a different time zone would get it wrong in the direction that matters
 * (calling a lifted lockout still in force). Comparing once here, against the server's clock — the
 * same one that decided the lockout in the first place — is what keeps the answer right regardless
 * of where the viewer is reading from. {@code lastLoginAt} stays off this record: nothing this list
 * shows needs it.
 */
public record UserSummary(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String displayName,
        @Classification(Tier.INTERNAL) String status,
        @Classification(Tier.INTERNAL) boolean locked) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
