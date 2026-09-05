package in.chalkbase.identity.api;

import in.chalkbase.identity.application.AuthenticationService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in, sign out, change password.
 *
 * <p>No version segment in the path — the API is not versioned (ADR-0016). Nothing here builds an
 * error response: every failure is a {@code ChalkbaseException} that
 * {@code platform.error.GlobalExceptionHandler} turns into the envelope.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authentication;

    public AuthController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    /**
     * The servlet request and response are passed through because the session cookie is the result
     * of this call — there is no token in the body to hand back instead (ADR-0003).
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return ApiResponse.success(authentication.login(request, http, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest http) {
        authentication.logout(http);
        return ApiResponse.success(null);
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(request);
        return ApiResponse.success(null);
    }
}
