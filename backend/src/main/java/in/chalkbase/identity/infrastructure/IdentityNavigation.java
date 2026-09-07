package in.chalkbase.identity.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>Signing in, signing out and changing one's own password have no entry, for the same reason
 * they have no permission in {@link IdentityPermissions}: they are not places you navigate to.
 *
 * <p>{@code settings} carries no {@code requiredPermission} of its own. It is a container, and the
 * catalogue drops a container whose children have all been filtered away — so a librarian never
 * sees a Settings entry that opens onto nothing, without this file having to know which roles a
 * school has invented.
 */
@Configuration
public class IdentityNavigation {

    /** The settings section. A container: it holds no screen of its own. */
    public static final String SETTINGS = "settings";

    /** Roles, the permission catalogue and who holds what — the screens behind {@code /api/access}. */
    public static final String SETTINGS_ACCESS = "settings.access";

    /**
     * The account roster at {@code /settings/users} — create, deactivate, reactivate, unlock and
     * reset.
     *
     * <p>Gated on {@link IdentityPermissions#USER_READ}, not {@code USER_MANAGE}. The screen itself
     * only ever needs the stricter of "may see this" and "may act here" to justify sending someone
     * to it, and {@code GET /api/access/users} — the read the screen loads on entry — already
     * enforces {@code USER_READ} on its own. Gating the menu on {@code USER_MANAGE} instead would
     * hide the roster from a role that can legitimately read it but not act on it, and there is a
     * shipped one: {@code AUDITOR} holds {@code USER_READ} for exactly this — seeing who has an
     * account is oversight — without {@code USER_MANAGE}, which would let it change anything.
     * {@code VICE_PRINCIPAL} is the same shape for a different reason: wide operational reach,
     * without the ability to create or disable an account. The screen itself already knows this
     * split — {@code UserRoster.canManageUsers} gates the five write actions on {@code USER_MANAGE}
     * independently, so a {@code USER_READ}-only visitor sees the roster with every button that
     * would fail to them held back, never a screen that 403s on load.
     */
    public static final String SETTINGS_USERS = "settings.users";

    @Bean
    NavigationProvider identityNavigationProvider() {
        return () -> List.of(new NavigationItem(
                SETTINGS,
                "nav.settings",
                "settings",
                90,
                null,
                List.of(
                        new NavigationItem(
                                SETTINGS_ACCESS, "nav.settings.access", "shield", 10, IdentityPermissions.ROLE_MANAGE),
                        // Ordered right after Access rather than after the school module's Profile
                        // (order 20, `SchoolNavigation`): both Access and Users are identity's own
                        // account-and-permission screens, and putting Profile between them would
                        // split a pair that belongs together for no reason but which module owns
                        // which order number.
                        new NavigationItem(
                                SETTINGS_USERS, "nav.settings.users", "user", 15, IdentityPermissions.USER_READ))));
    }
}
