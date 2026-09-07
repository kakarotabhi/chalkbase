package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.AuthenticatedUser;
import in.chalkbase.platform.security.CurrentUserResolver;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tells {@code platform.security.CurrentUser} which account is signed in, as a plain id.
 *
 * <p>Registered the same way {@link IdentityAuditActorResolver} registers the audit log's actor
 * resolver, for the same reason: the dependency has to run this way round, since the shared kernel
 * must not import a feature module.
 */
@Configuration
public class IdentityCurrentUserResolver {

    @Bean
    CurrentUserResolver sessionCurrentUserResolver() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
                return Optional.empty();
            }
            return Optional.of(user.userId());
        };
    }
}
