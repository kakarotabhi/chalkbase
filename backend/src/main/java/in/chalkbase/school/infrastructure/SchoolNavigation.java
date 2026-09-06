package in.chalkbase.school.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>Registered the same way {@link SchoolPermissions} registers this module's permissions: a
 * {@code @Bean} inside the module, collected by the platform. Nothing is declared here for a screen
 * that has not shipped: an id the frontend cannot resolve is dropped and logged, which is a menu
 * item that does nothing.
 *
 * <p>{@code school:school:read} gates it, which is the permission {@code SchoolController} will
 * enforce once platform-operator accounts exist. Until then the endpoint is deliberately open and
 * the menu is the stricter of the two — which is the right way round: it is fine for navigation to
 * be conservative, never for an endpoint to be generous.
 */
@Configuration
public class SchoolNavigation {

    /** The school register. Matches the frontend's {@code /schools} route by convention, not by URL. */
    public static final String SCHOOLS = "schools";

    /**
     * This school's own profile, which lives under the settings section the identity module owns.
     *
     * <p>Declared here, at the top level, under its dotted id: the catalogue reads {@code
     * settings.profile} and places it beneath {@code settings}. The alternative — declaring it
     * inside {@code IdentityNavigation} — would put a school screen behind another module's
     * boundary and make one file the place every module goes to add a settings entry.
     *
     * <p>Gated on {@code school:school:update} rather than {@code read}, although {@code GET
     * /api/school/profile} only needs {@code read}. The menu is allowed to be the stricter of the
     * two and this is a case for it: the screen exists to change the school's details, and a
     * librarian who can technically view the address has no reason to be sent to an editing form.
     * Navigation may be conservative; an endpoint may never be generous.
     */
    public static final String SETTINGS_PROFILE = "settings.profile";

    @Bean
    NavigationProvider schoolNavigationProvider() {
        return () -> List.of(
                new NavigationItem(SCHOOLS, "nav.schools", "school", 20, SchoolPermissions.SCHOOL_READ),
                new NavigationItem(
                        SETTINGS_PROFILE, "nav.settings.profile", "school", 20, SchoolPermissions.SCHOOL_UPDATE));
    }
}
