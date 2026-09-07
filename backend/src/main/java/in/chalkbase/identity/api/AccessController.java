package in.chalkbase.identity.api;

import in.chalkbase.identity.application.AccessDirectory;
import in.chalkbase.identity.application.RoleManagementService;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.security.PermissionDefinition;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The permission catalogue, this school's roles, and who holds them.
 *
 * <p>{@code /api/access/users} and its account-lifecycle actions live in
 * {@link UserAccountController} instead — that resource is guarded by
 * {@code identity:user:manage}, a permission a school can hold without also holding
 * {@code identity:role:manage}, so keeping the two apart keeps a controller's permission story
 * legible at a glance.
 *
 * <p><strong>No impact preview.</strong> FR-004's acceptance note asks for one — "admin users must
 * be able to preview permission impact before assigning roles" — and it is deliberately not built
 * here. It is a genuinely interactive, frontend-shaped feature ("here is who would gain what, are
 * you sure"), and this wave is backend-only. What this wave does provide is the data such a screen
 * would need: {@link #holders} answers "who holds this role right now" for a role about to be
 * edited, and {@link #roles} already returns every role's current permission set for a client to
 * diff against what it is about to save. Building the preview itself is left to the frontend lane,
 * against these two reads — building half of it here in a shape nobody has confirmed would be the
 * kind of thing that reads as done and is not, which is exactly the trap {@code docs/status.md}
 * already names elsewhere in this codebase.
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
    private final RoleManagementService roleManagement;

    public AccessController(AccessDirectory directory, RoleManagementService roleManagement) {
        this.directory = directory;
        this.roleManagement = roleManagement;
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

    @PreAuthorize("hasAuthority('identity:role:manage')")
    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse created = roleManagement.createRole(request);
        return ResponseEntity.created(URI.create("/api/access/roles/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('identity:role:manage')")
    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleResponse> updateRolePermissions(
            @PathVariable UUID roleId, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ApiResponse.success(roleManagement.updateRolePermissions(roleId, request));
    }

    /** Who holds this role right now — the building block a preview screen would summarise. */
    @PreAuthorize("hasAuthority('identity:role:manage')")
    @GetMapping("/roles/{roleId}/holders")
    public ApiResponse<List<UserSummary>> holders(@PathVariable UUID roleId) {
        return ApiResponse.success(directory.holders(roleId));
    }

    @PreAuthorize("hasAuthority('identity:role:manage')")
    @GetMapping("/users/{accountId}/grants")
    public ApiResponse<List<GrantResponse>> grants(@PathVariable UUID accountId) {
        return ApiResponse.success(directory.grantsFor(accountId));
    }

    @PreAuthorize("hasAuthority('identity:role:manage')")
    @PostMapping("/users/{accountId}/grants")
    public ResponseEntity<ApiResponse<GrantResponse>> grantRole(
            @PathVariable UUID accountId, @Valid @RequestBody GrantRoleRequest request) {
        GrantResponse created = roleManagement.grantRole(accountId, request);
        return ResponseEntity.created(URI.create("/api/access/users/" + accountId + "/grants/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('identity:role:manage')")
    @DeleteMapping("/users/{accountId}/grants/{grantId}")
    public ResponseEntity<Void> revokeGrant(@PathVariable UUID accountId, @PathVariable UUID grantId) {
        roleManagement.revokeGrant(accountId, grantId);
        return ResponseEntity.noContent().build();
    }
}
