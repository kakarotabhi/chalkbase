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
 * {@code @Bean} inside the module, collected by the platform. One entry, because one screen exists
 * — the school register. Nothing is declared here for a screen that has not shipped: an id the
 * frontend cannot resolve is dropped and logged, which is a menu item that does nothing.
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

    @Bean
    NavigationProvider schoolNavigationProvider() {
        return () -> List.of(new NavigationItem(SCHOOLS, "nav.schools", "school", 20, SchoolPermissions.SCHOOL_READ));
    }
}
