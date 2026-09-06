package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.AuthenticatedUser;
import in.chalkbase.platform.audit.AuditActor;
import in.chalkbase.platform.audit.AuditActorResolver;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tells the audit log who is signed in (ADR-0018).
 *
 * <p>Registered the same way {@link IdentityPermissions} registers this module's permissions and
 * {@link IdentityNavigation} its menu entries: a {@code @Bean} inside the module, collected by the
 * platform. The dependency has to run this way round — the audit log is platform, the principal is
 * identity's (ADR-0017), and the shared kernel must not import a feature module.
 *
 * <p>Everything it returns is a <strong>snapshot</strong> read off the principal, which was itself
 * resolved once at login. Nothing here queries the database: an audit row records who the actor was
 * at the time, and a lookup would record who they are now — letting a later rename or role change
 * rewrite the past.
 *
 * <p>An unauthenticated request, a scheduled job and a startup task all return empty. That is the
 * normal case, not an error: a failed sign-in has no actor, and recording the attempted username
 * against the event is what identifies it instead.
 */
@Configuration
public class IdentityAuditActorResolver {

    @Bean
    AuditActorResolver sessionAuditActorResolver() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                return Optional.empty();
            }
            return Optional.of(new AuditActor(user.userId(), user.displayName(), user.rolesSnapshot(), user.schema()));
        };
    }
}
