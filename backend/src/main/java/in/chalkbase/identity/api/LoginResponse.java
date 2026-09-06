package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;
import java.util.UUID;

/**
 * What a client learns from signing in.
 *
 * <p>No token, and no session id: the session is carried by an {@code HttpOnly} cookie the browser
 * never exposes to script (ADR-0003). Putting the session id in the body would defeat that.
 *
 * @param mustChangePassword true when the school issued a temporary password. The client must send
 *     the user to the change-password screen and let them do nothing else first.
 * @param permissions the permission codes this session holds, sorted, so the client can hide what
 *     the user cannot do. It is a convenience for the interface, never the enforcement: the server
 *     checks the same set on every call, and a client that ignored this list would gain nothing.
 */
public record LoginResponse(
        @Classification(Tier.INTERNAL) UUID userId,
        @Classification(Tier.CONFIDENTIAL) String displayName,
        @Classification(Tier.INTERNAL) boolean mustChangePassword,
        @Classification(Tier.PUBLIC) SchoolSummary school,
        @Classification(Tier.INTERNAL) List<String> permissions) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
