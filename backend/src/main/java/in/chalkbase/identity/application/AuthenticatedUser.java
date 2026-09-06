package in.chalkbase.identity.application;

import in.chalkbase.identity.api.SchoolSummary;
import in.chalkbase.platform.security.AccessScope;
import java.io.Serializable;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * The principal placed in the {@code SecurityContext} after a successful login.
 *
 * <p>{@link Principal} rather than a bare string so that Spring Session's
 * {@code principal_name} column and every log line that prints an authentication get the username
 * and not a record dump. {@link Serializable} because the security context is stored in the
 * session, which Spring Session serialises into {@code public.spring_session_attributes}.
 *
 * <p>Carries the schema as well as the id: a user id is only meaningful inside one school's schema.
 * It carries the school's code and name too, which are not the same thing — the schema says where
 * this user's rows live, the {@link SchoolSummary} says what to put on screen. Keeping the second
 * here means {@code /api/me} can name the school without reading {@code public.school} on a call
 * ADR-0008 makes the hottest path in the application, and a school cannot be renamed out from under
 * a running session.
 *
 * <p>It carries the display name as well as the username, and the two are not interchangeable. The
 * username is what was typed to sign in — usually an admission number, and not something to put in
 * a log line. The display name is what a human reads, and it is what the audit log snapshots as
 * {@code actor_name} (ADR-0018): an audit row must still read correctly after the account is
 * renamed, so the name is captured at the time rather than looked up again afterwards.
 *
 * <p>It also carries {@link EffectiveAccess} — the permissions and scopes resolved once at login
 * (ADR-0005). <strong>This is the session cache</strong>: the principal is part of the security
 * context, and the security context is what Spring Session writes to the session store, so storing
 * the same set under a second {@code SessionAttributes} key would be a copy that can drift from the
 * authorities the filter chain actually checks. Invalidating it is invalidating the session, which
 * is what a role change or a forced logout already does.
 */
public record AuthenticatedUser(
        UUID userId, String username, String displayName, String schema, SchoolSummary school, EffectiveAccess access)
        implements Principal, Serializable {

    public AuthenticatedUser {
        access = access == null ? EffectiveAccess.none() : access;
    }

    @Override
    public String getName() {
        return username;
    }

    /** The permission codes this session holds. Also the authorities {@code hasAuthority} checks. */
    public Set<String> permissions() {
        return access.permissions();
    }

    /** How far this session's grants reach. Consumed when narrowing a query, never as a post-filter. */
    public Set<AccessScope> scopes() {
        return access.scopes();
    }

    /**
     * The role codes held at sign-in, sorted and comma-separated — the shape {@code audit_event}
     * stores. For the audit trail only; nothing in this application authorizes on a role name.
     */
    public String rolesSnapshot() {
        return access.rolesAsSnapshot();
    }

    /** A user id is not personal data; a username may be. Keep the username out of log lines. */
    @Override
    public String toString() {
        return "AuthenticatedUser[" + userId + "]";
    }
}
