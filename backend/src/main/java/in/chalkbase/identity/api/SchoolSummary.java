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
 * page load — and the school's code, name and time zone do not change under a session.
 *
 * <p>{@code timezone} is an IANA zone id, e.g. {@code Asia/Kolkata}, and it is why this record
 * exists rather than the client reading {@code code} and {@code name} off two unrelated fields: a
 * screen that renders a time needs this exact value, on every page, without a second request — the
 * same reasoning ADR-0008 already applies to permissions and navigation. Stale for the length of a
 * session, like the rest of this record: a school that changes its zone mid-session is corrected at
 * the next login, not by a background refresh.
 */
public record SchoolSummary(
        @Classification(Tier.PUBLIC) String code,
        @Classification(Tier.PUBLIC) String name,
        @Classification(Tier.PUBLIC) String timezone)
        implements Serializable {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
