package in.chalkbase.identity.api;

import in.chalkbase.identity.application.SessionBootstrap;
import in.chalkbase.platform.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bootstrap call: everything the shell needs, in one request (ADR-0008).
 *
 * <p>It lives in identity because it is the session describing itself, and the session is
 * identity's (ADR-0017). Other modules contribute to the answer through
 * {@code platform.navigation.NavigationProvider}, not by being called from here.
 *
 * <p><strong>A session, and no permission beyond it.</strong> Every signed-in user must be able to
 * bootstrap — including a parent who holds nothing at all, and a user still on an issued password
 * who is allowed nowhere else until they change it. A permission on this endpoint would be a user
 * who can sign in and then cannot be shown anything, not even the reason. {@code isAuthenticated()}
 * is the authorization decision, written out so {@code ControllerAuthorizationTests} sees a
 * deliberate choice rather than an omission.
 *
 * <p>Unauthenticated callers never reach the method: the filter chain answers {@code 401 AUTH_002}
 * first, which is exactly what a client with an expired cookie needs to be told on reload.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final SessionBootstrap bootstrap;

    public MeController(SessionBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiResponse<MeResponse> me() {
        return ApiResponse.success(bootstrap.describeCurrentSession());
    }
}
