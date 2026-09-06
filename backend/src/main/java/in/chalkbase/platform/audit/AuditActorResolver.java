package in.chalkbase.platform.audit;

import java.util.Optional;

/**
 * Lets the module that owns authentication say who is currently acting.
 *
 * <p>Mirrors {@code platform.security.PermissionProvider} and
 * {@code platform.navigation.NavigationProvider}: the platform owns the mechanism, the module owns
 * the knowledge. It exists because the dependency has to run this way round. The audit log is
 * platform, the principal is {@code identity}'s, and the shared kernel must not import a feature
 * module — so identity registers a resolver instead of the platform reaching in for
 * {@code AuthenticatedUser}.
 *
 * <p>Register one per module that can establish a principal, as a {@code @Bean} inside that module.
 * There is exactly one today; {@link AuditService} asks each in turn and takes the first answer, so
 * a second authentication mechanism (an API key, a job runner) is a new bean and nothing else.
 *
 * <p>An implementation must return {@link Optional#empty()} rather than throwing when nobody is
 * authenticated. A failed sign-in and a scheduled job are the normal cases, not error conditions.
 */
@FunctionalInterface
public interface AuditActorResolver {

    Optional<AuditActor> currentActor();
}
