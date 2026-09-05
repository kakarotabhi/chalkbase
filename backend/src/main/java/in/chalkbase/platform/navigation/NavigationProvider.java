package in.chalkbase.platform.navigation;

import java.util.List;

/**
 * Lets a module declare the navigation entries for the screens it owns (ADR-0008).
 *
 * <p>Mirrors {@code platform.security.PermissionProvider} exactly, and for the same reason: the
 * platform owns the registry and the filtering, each module owns its own entries. The alternative
 * is one menu definition in the shared kernel that all sixteen modules edit — a permanent merge
 * conflict, and domain knowledge living where it does not belong.
 *
 * <p>Register one per module as a {@code @Bean} inside that module. Add an entry in the same change
 * that adds the screen it points at: a menu item for a screen that does not exist is a dead link
 * the frontend will drop, and a screen with no menu item is a feature a school paid for that nobody
 * can find.
 */
@FunctionalInterface
public interface NavigationProvider {

    List<NavigationItem> navigation();
}
