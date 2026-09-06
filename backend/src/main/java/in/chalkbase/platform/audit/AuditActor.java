package in.chalkbase.platform.audit;

import java.util.UUID;

/**
 * Who did the thing, as a <strong>snapshot</strong> taken at the moment it happened (ADR-0018).
 *
 * <p>Deliberately not a reference to an account row. An audit row must still read correctly after
 * the account is renamed, its roles change, or it is deleted outright — a foreign key to a mutable
 * row would let the past change, which is the one thing an audit log may never do.
 *
 * <p>{@code tenantSchema} is here, rather than being read from
 * {@code platform.tenancy.TenantContext}, because a user id is only meaningful inside one school's
 * schema — the same reason identity's principal carries it. It also covers the one case where the
 * tenant is no longer bound when the event is recorded: a 403 is produced inside the security
 * filter chain, after the filter that binds the tenant has already unbound it in its
 * {@code finally}. Without the schema on the actor, every permission denial would have nowhere to
 * be written.
 *
 * @param id the account id, or null when nobody is authenticated (a failed sign-in has no actor)
 * @param name the display name as it read at the time, never looked up again
 * @param roles the role codes held at the time, comma-separated and sorted, or null
 * @param tenantSchema the school this actor belongs to
 */
public record AuditActor(UUID id, String name, String roles, String tenantSchema) {

    /**
     * Nobody — the event is attributable to a school but to no account.
     *
     * <p>Stated rather than left to be resolved, because "there is no actor" and "I did not look"
     * must not be the same call. A failed sign-in happens while a previous session's principal may
     * still be in the security context, and resolving one there would attribute a stranger's failed
     * guess to whoever last used the browser.
     */
    public static AuditActor unauthenticated(String tenantSchema) {
        return new AuditActor(null, null, null, tenantSchema);
    }
}
