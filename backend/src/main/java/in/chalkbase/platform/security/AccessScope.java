package in.chalkbase.platform.security;

import java.io.Serializable;
import java.util.UUID;

/**
 * One narrowing of what a user may see, resolved once at login and carried on the session.
 *
 * <p>The second of the two enforcement layers in ADR-0005. The first — "may they do this at all?" —
 * is a permission check on the method. This one answers "which rows?", and it belongs
 * <strong>inside the query</strong>, exactly as the tenant schema does. A post-filter over results
 * turns every list endpoint into "load the whole school and discard most of it": the one place this
 * model can genuinely fail to scale, and it fails quietly, only at the biggest school.
 *
 * <p>{@link Serializable} because it travels in the security context, which Spring Session writes
 * to {@code public.spring_session_attributes}.
 *
 * @param type what kind of thing the grant is scoped to
 * @param targetId the thing itself, or {@code null} for {@link ScopeType#SCHOOL} and
 *     {@link ScopeType#SELF}, which need no target
 */
public record AccessScope(ScopeType type, UUID targetId) implements Serializable {

    public static AccessScope school() {
        return new AccessScope(ScopeType.SCHOOL, null);
    }

    /** True when this scope covers everything in the school, so no narrowing is needed at all. */
    public boolean isWholeSchool() {
        return type == ScopeType.SCHOOL;
    }
}
