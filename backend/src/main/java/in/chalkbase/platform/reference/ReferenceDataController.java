package in.chalkbase.platform.reference;

import in.chalkbase.platform.api.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global, Tier-1 reference data (ADR-0006, ADR-0029): the same list for every school, read through
 * an endpoint instead of duplicated as a constant in every screen that needs it.
 *
 * <p>The board list lives in {@code SchoolController#boards} instead, not here — see {@link
 * ReferenceDataService} for why.
 *
 * <p><strong>A session, and no permission beyond it</strong> — mirrors {@code identity.api.MeController}
 * and {@code platform.dashboard.DashboardController}. Every signed-in user fills in forms that need
 * this list; gating it behind a module permission would mean a teacher editing nothing about the
 * school profile still needs one just to see the state picker populate.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final ReferenceDataService referenceData;

    public ReferenceDataController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/states")
    public ApiResponse<List<ReferenceItemResponse>> states() {
        return ApiResponse.success(referenceData.states());
    }
}
