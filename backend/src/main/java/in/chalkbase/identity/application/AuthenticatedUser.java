package in.chalkbase.identity.application;

import java.io.Serializable;
import java.security.Principal;
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
 */
public record AuthenticatedUser(UUID userId, String username, String schema) implements Principal, Serializable {

    @Override
    public String getName() {
        return username;
    }

    /** A user id is not personal data; a username may be. Keep the username out of log lines. */
    @Override
    public String toString() {
        return "AuthenticatedUser[" + userId + "]";
    }
}
