package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the dashboard appears in the menu (ADR-0008).
 *
 * <p>Ordered at 10 — ahead of every other root item ({@code students} at 25, {@code academics} at
 * 30, {@code settings} at 90, {@code audit} at 95) — and gated on no permission at all, so it is the
 * first item in every signed-in user's own navigation and therefore, through {@code landingGuard},
 * everyone's landing screen (see the PR this shipped in for what that changes for the auditor, who
 * held {@code platform:audit:read} and nothing else before this and landed on {@code /audit}
 * directly).
 *
 * <p>No permission gate is the deliberate choice, not an oversight: the endpoint itself never
 * refuses a signed-in caller (see {@link DashboardController}), so gating the menu item would hide
 * a screen the caller can in fact open — the "menu may be the stricter of the two" rule other
 * navigation providers use here works the other way, because there is nothing to be stricter than.
 */
@Configuration
public class DashboardNavigation {

    /** The dashboard screen. */
    public static final String DASHBOARD = "dashboard";

    @Bean
    NavigationProvider dashboardNavigationProvider() {
        return () -> List.of(new NavigationItem(DASHBOARD, "nav.dashboard", "dashboard", 10, null));
    }
}
