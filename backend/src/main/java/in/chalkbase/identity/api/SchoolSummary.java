package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.io.Serializable;

/**
 * The school the caller has signed in to, so the client can show it without a second call.
 *
 * <p>{@link Serializable} because it rides on {@code AuthenticatedUser}, which is part of the
 * security context and therefore written into {@code public.spring_session_attributes}. Keeping it
 * on the session is what lets {@code /api/me} answer without reading {@code public.school} on every
 * page load — and the school's code and name do not change under a session.
 */
public record SchoolSummary(
        @Classification(Tier.PUBLIC) String code,
        @Classification(Tier.PUBLIC) String name) implements Serializable {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
