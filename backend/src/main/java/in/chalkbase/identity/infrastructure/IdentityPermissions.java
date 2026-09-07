package in.chalkbase.identity.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Signing in, signing out and changing one's own password are not here on purpose. They are not
 * permissions: everybody who has an account may do them, and a school that could remove
 * {@code identity:auth:login} from a role would have invented an account that cannot be used. They
 * are guarded by authentication alone.
 */
@Configuration
public class IdentityPermissions {

    /** Seeing who has an account at this school. */
    public static final String USER_READ = "identity:user:read";

    /**
     * Creating an account, deactivating or reactivating one, clearing a lockout, and issuing an
     * admin password reset. Deliberately separate from {@link #ROLE_MANAGE}: a school may want
     * someone who runs the office roster — creating accounts as staff join, deactivating them as
     * staff leave — without also handing them the ability to change what any role may do.
     */
    public static final String USER_MANAGE = "identity:user:manage";

    /**
     * Reading the permission catalogue and this school's roles, creating a role, editing which
     * permissions it holds, and granting or revoking it for a user. Held only by roles that
     * genuinely administer access, because it is the permission with which every other permission
     * can eventually be granted — see {@code AccessGuardrails} for what stops a holder granting one
     * they do not themselves hold.
     */
    public static final String ROLE_MANAGE = "identity:role:manage";

    @Bean
    PermissionProvider identityPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        USER_READ, "identity", "View users", "See the people who hold an account at this school."),
                new PermissionDefinition(
                        USER_MANAGE,
                        "identity",
                        "Manage user accounts",
                        "Create accounts, deactivate or reactivate them, clear a lockout, and reset a password."),
                new PermissionDefinition(
                        ROLE_MANAGE,
                        "identity",
                        "Manage roles and permissions",
                        "Read the permission catalogue and this school's roles, and decide who holds them."));
    }
}
