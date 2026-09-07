package in.chalkbase.identity.api;

import in.chalkbase.identity.application.AccessDirectory;
import in.chalkbase.identity.application.UserAccountManagementService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This school's accounts, and the four things an admin can do to one after it exists.
 *
 * <p>Guarded by {@code identity:user:manage} for every write, kept separate from
 * {@code identity:role:manage} ({@link AccessController}): a school may want someone who runs the
 * office roster — creating accounts as staff join, deactivating them as they leave — without also
 * handing them the ability to decide what any role may do.
 *
 * <p><strong>Every write here is idempotent except {@link #create} and {@link #resetPassword}.</strong>
 * Deactivating an already-disabled account, reactivating an already-active one, or unlocking one
 * that was never locked all succeed and change nothing — a double-click on the button is not an
 * event. Creating an account and resetting a password are not idempotent by nature: each issues a
 * fresh, one-time-visible temporary password, and doing either twice is two different secrets, not
 * the same request repeated.
 *
 * <p>The permission strings in the annotations are literals rather than references to
 * {@code IdentityPermissions}, matching {@link AccessController} — see its Javadoc for why.
 */
@RestController
@RequestMapping("/api/access/users")
public class UserAccountController {

    private final AccessDirectory directory;
    private final UserAccountManagementService management;

    public UserAccountController(AccessDirectory directory, UserAccountManagementService management) {
        this.directory = directory;
        this.management = management;
    }

    @PreAuthorize("hasAuthority('identity:user:read')")
    @GetMapping
    public ApiResponse<List<UserSummary>> users() {
        return ApiResponse.success(directory.users());
    }

    /**
     * A generated temporary password is returned exactly once, in this response — see
     * {@link NewUserAccountResponse}.
     */
    @PreAuthorize("hasAuthority('identity:user:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<NewUserAccountResponse>> create(
            @Valid @RequestBody CreateUserAccountRequest request) {
        NewUserAccountResponse created = management.create(request);
        return ResponseEntity.created(URI.create("/api/access/users/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('identity:user:manage')")
    @PostMapping("/{accountId}/deactivate")
    public ApiResponse<UserAccountResponse> deactivate(@PathVariable UUID accountId) {
        return ApiResponse.success(management.deactivate(accountId));
    }

    @PreAuthorize("hasAuthority('identity:user:manage')")
    @PostMapping("/{accountId}/reactivate")
    public ApiResponse<UserAccountResponse> reactivate(@PathVariable UUID accountId) {
        return ApiResponse.success(management.reactivate(accountId));
    }

    /** Clears a lockout from repeated failed sign-ins. Does not touch {@code status}. */
    @PreAuthorize("hasAuthority('identity:user:manage')")
    @PostMapping("/{accountId}/unlock")
    public ApiResponse<UserAccountResponse> unlock(@PathVariable UUID accountId) {
        return ApiResponse.success(management.unlock(accountId));
    }

    /**
     * Issues a new temporary password and ends every session the account currently holds — see
     * {@link TemporaryPasswordResponse} and {@code UserAccountManagementService#resetPassword}.
     */
    @PreAuthorize("hasAuthority('identity:user:manage')")
    @PostMapping("/{accountId}/reset-password")
    public ApiResponse<TemporaryPasswordResponse> resetPassword(@PathVariable UUID accountId) {
        return ApiResponse.success(management.resetPassword(accountId));
    }
}
