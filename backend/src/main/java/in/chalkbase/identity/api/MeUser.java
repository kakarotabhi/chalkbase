package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * The signed-in person, as the shell needs them: enough to greet them and to know whether they are
 * allowed anywhere else yet.
 *
 * <p>No username: it is usually a child's admission number, the client already knows what was
 * typed, and it has no business in a payload that will be logged by somebody's proxy.
 *
 * @param mustChangePassword true while the school's issued password is still in place. The client
 *     must send the user to the change-password screen and let them do nothing else first. Read
 *     from the account rather than from the session, so a password changed during this session is
 *     reflected on the next bootstrap without signing out.
 */
public record MeUser(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String displayName,
        @Classification(Tier.INTERNAL) boolean mustChangePassword) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
