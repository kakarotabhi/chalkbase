package in.chalkbase.communication.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>One screen so far, so the container has a single child — kept as a container rather than a
 * bare leaf because a second screen (a per-recipient inbox, once the parent portal exists) is the
 * next thing this module grows, and giving the id a dotted child now costs nothing.
 *
 * <p>Ordered at 50, just after attendance (40) and well ahead of settings (90) — a class teacher's
 * or front office's daily work, not configuration.
 */
@Configuration
public class CommunicationNavigation {

    public static final String COMMUNICATION = "communication";
    public static final String COMMUNICATION_CIRCULARS = "communication.circulars";

    @Bean
    NavigationProvider communicationNavigationProvider() {
        return () -> List.of(new NavigationItem(
                COMMUNICATION,
                "nav.communication",
                "communication",
                50,
                null,
                List.of(new NavigationItem(
                        COMMUNICATION_CIRCULARS,
                        "nav.communication.circulars",
                        "communication",
                        10,
                        CommunicationPermissions.CIRCULAR_READ))));
    }
}
