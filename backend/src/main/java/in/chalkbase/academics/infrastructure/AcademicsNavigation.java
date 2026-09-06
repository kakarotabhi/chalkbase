package in.chalkbase.academics.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A stable id and a label key — no URL, no route, no component name. The frontend maps ids to
 * its own routes and drops, with a log line, any id it does not know.
 *
 * <p>{@code academics} is a container: it has no screen of its own, and it declares both of its
 * children inline, which is what makes it one. The catalogue drops a container whose children have
 * all been filtered away, so a librarian is never shown an Academics entry that opens onto nothing
 * — and this file never has to know which roles a school has invented.
 *
 * <p>Ordered at 30, after the school register and well before settings: for a principal or a class
 * teacher this is daily work, not configuration.
 *
 * <p>The gates are the read permissions rather than the manage ones. That is the opposite of the
 * choice {@code SchoolNavigation} makes for the profile screen, and deliberately: these two screens
 * are worth opening to look at — a subject teacher checking which sections Class 9 has — where the
 * profile screen exists only to edit.
 */
@Configuration
public class AcademicsNavigation {

    /** The academics section. A container: it holds no screen of its own. */
    public static final String ACADEMICS = "academics";

    /** Academic years, and which one the school is in. */
    public static final String ACADEMICS_SESSIONS = "academics.sessions";

    /** The ladder of classes and the sections inside them. */
    public static final String ACADEMICS_CLASSES = "academics.classes";

    @Bean
    NavigationProvider academicsNavigationProvider() {
        return () -> List.of(new NavigationItem(
                ACADEMICS,
                "nav.academics",
                "academics",
                30,
                null,
                List.of(
                        new NavigationItem(
                                ACADEMICS_SESSIONS,
                                "nav.academics.sessions",
                                "calendar",
                                10,
                                AcademicsPermissions.SESSION_READ),
                        new NavigationItem(
                                ACADEMICS_CLASSES,
                                "nav.academics.classes",
                                "layers",
                                20,
                                AcademicsPermissions.CLASS_READ))));
    }
}
