package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing screen for most users: what a principal opening Chalkbase in the morning wants to
 * know, cut down to what they may see.
 *
 * <p><strong>No permission gate beyond a session, deliberately — mirrors
 * {@code identity.api.MeController}.</strong> Every signed-in user may ask, including one who holds
 * none of the four tile permissions; they get an envelope with every field absent rather than a
 * 403 on their own landing page, the same reasoning that keeps {@code /api/me} open to anyone
 * authenticated. {@link DashboardService} is where the real authorization decision is made, once
 * per tile — see it for how.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.success(dashboard.forCurrentCaller());
    }
}
