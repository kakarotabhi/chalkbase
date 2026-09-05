package in.chalkbase.identity.api;

import in.chalkbase.identity.application.AccessDirectory;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.security.PermissionDefinition;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the access model: the permission catalogue, this school's roles, and its accounts.
 *
 * <p>Read-only for now. Editing a role and assigning a grant are the next step of the identity
 * milestone, together with the impact preview the acceptance notes require; nothing here presumes a
 * particular shape for them.
 *
 * <p>The permission strings in the annotations are literals rather than references to
 * {@code IdentityPermissions}, because a constant in an annotation must be a compile-time constant
 * and an inlined one would not survive a rename any better. What does survive a rename is
 * {@code ControllerAuthorizationTests}, which checks every code named here against the catalogue.
 */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final AccessDirectory directory;

    public AccessController(AccessDirectory directory) {
        this.directory = directory;
    }

    /** Everything that could be put into a role. The same list at every school — permissions are code. */
    @PreAuthorize("hasAuthority('identity:role:manage')")
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionDefinition>> permissions() {
        return ApiResponse.success(directory.permissions());
    }

    /** This school's roles. A different list at every school — roles are data. */
    @PreAuthorize("hasAuthority('identity:role:manage')")
    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.success(directory.roles());
    }

    @PreAuthorize("hasAuthority('identity:user:read')")
    @GetMapping("/users")
    public ApiResponse<List<UserSummary>> users() {
        return ApiResponse.success(directory.users());
    }
}
