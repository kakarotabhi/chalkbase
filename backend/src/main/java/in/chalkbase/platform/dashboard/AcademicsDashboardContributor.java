package in.chalkbase.platform.dashboard;

import java.util.Optional;
import java.util.Set;

/**
 * Academics' contribution to the landing dashboard: the current-session tile.
 *
 * <p>Mirrors {@code platform.navigation.NavigationProvider} and
 * {@code platform.security.PermissionProvider}: the platform owns the aggregation and the shape of
 * {@link DashboardResponse}, and academics owns what belongs in its own tile and whether the
 * caller may see it. That is what keeps {@code platform.dashboard} from importing
 * {@code academics.api} directly — the same reason {@code platform.audit.AuditActorResolver} exists
 * rather than the audit log reaching into identity's principal type. The shared kernel must not
 * import a feature module.
 *
 * <p>Register one implementation as a {@code @Bean} inside {@code academics}.
 */
@FunctionalInterface
public interface AcademicsDashboardContributor {

    /**
     * @param heldPermissions the caller's granted authorities — the same strings
     *     {@code hasAuthority(...)} checks in a {@code @PreAuthorize}. The implementation decides
     *     using its own {@code AcademicsPermissions.SESSION_READ}, so the permission string this
     *     tile depends on stays owned by the module that defined it.
     * @return empty when the caller does not hold the session-read permission. A tile with
     *     {@link SessionTile#set()} false — not empty — when the caller may see it but no session is
     *     current: "not permitted to know" and "there is nothing to know yet" are different facts.
     */
    Optional<SessionTile> sessionTile(Set<String> heldPermissions);
}
