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

    @Bean
    NavigationProvider identityNavigationProvider() {
        return () -> List.of(new NavigationItem(
                SETTINGS,
                "nav.settings",
                "settings",
                90,
                null,
                List.of(new NavigationItem(
                        SETTINGS_ACCESS, "nav.settings.access", "shield", 10, IdentityPermissions.ROLE_MANAGE))));
    }
}
