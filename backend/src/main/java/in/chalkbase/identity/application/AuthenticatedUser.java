package in.chalkbase.identity.application;

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
 *
 * <p>It also carries {@link EffectiveAccess} — the permissions and scopes resolved once at login
 * (ADR-0005). <strong>This is the session cache</strong>: the principal is part of the security
 * context, and the security context is what Spring Session writes to the session store, so storing
 * the same set under a second {@code SessionAttributes} key would be a copy that can drift from the
 * authorities the filter chain actually checks. Invalidating it is invalidating the session, which
 * is what a role change or a forced logout already does.
 */
public record AuthenticatedUser(UUID userId, String username, String schema, EffectiveAccess access)
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

    /** A user id is not personal data; a username may be. Keep the username out of log lines. */
    @Override
    public String toString() {
        return "AuthenticatedUser[" + userId + "]";
    }
}
