package in.chalkbase.identity.application;

import in.chalkbase.identity.api.CreateRoleRequest;
import in.chalkbase.identity.api.GrantResponse;
import in.chalkbase.identity.api.GrantRoleRequest;
import in.chalkbase.identity.api.RoleResponse;
import in.chalkbase.identity.api.UpdateRolePermissionsRequest;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.identity.domain.Role;
import in.chalkbase.identity.domain.RoleCode;
import in.chalkbase.identity.domain.UserRoleGrant;
import in.chalkbase.identity.infrastructure.RoleRepository;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserRoleGrantRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.error.PlatformErrorCode;
import in.chalkbase.platform.security.AccessScope;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.security.ScopeType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating a role, editing which permissions it holds, and granting or revoking it for a user (item
 * 3 of the identity write-endpoint milestone).
 *
 * <p>Every write here goes through {@link AccessGuardrails} twice, for the two risks a
 * self-service access screen creates that ADR-0005 leaves open: privilege escalation through
 * {@code identity:role:manage} not being scoped to particular permissions
 * ({@link AccessGuardrails#requireHeldByActor}), and leaving the school with nobody who can manage
 * access at all ({@link AccessGuardrails#requireAnotherAccessManagerIfThisIsOne} and its two
 * siblings). Neither is enforced by the database or by {@code @PreAuthorize} — both are exactly the
 * kind of thing that is easy to ship without, which is why they are written down here rather than
 * trusted to review.
 *
 * <p>Reached through {@code /api/access/**}, an authenticated endpoint {@code SessionTenantFilter}
 * has already bound a tenant for, so — like {@link UserAccountManagementService} — these are
 * ordinary {@code @Transactional} calls with no manual tenant binding.
 */
@Service
@Transactional(readOnly = true)
public class RoleManagementService {

    private final RoleRepository roles;
    private final UserRoleGrantRepository grants;
    private final UserAccountRepository accounts;
    private final PermissionCatalog catalog;
    private final AccessGuardrails guardrails;
    private final AuditService audit;
    private final AuthenticationService authentication;

    public RoleManagementService(
            RoleRepository roles,
            UserRoleGrantRepository grants,
            UserAccountRepository accounts,
            PermissionCatalog catalog,
            AccessGuardrails guardrails,
            AuditService audit,
            AuthenticationService authentication) {
        this.roles = roles;
        this.grants = grants;
        this.accounts = accounts;
        this.catalog = catalog;
        this.guardrails = guardrails;
        this.audit = audit;
        this.authentication = authentication;
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        Set<String> permissions = requireKnownPermissions(request.permissions());
        guardrails.requireHeldByActor(permissions, actorPermissions());

        Role role = new Role(
                uniqueCode(RoleCode.deriveFrom(request.name())),
                request.name().trim(),
                blankToNull(request.description()),
                permissions);
        roles.saveAndFlush(role);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                "ROLE",
                role.getId().toString(),
                List.of("name", "description", "permissions"));

        return AccessDirectory.toResponse(role);
    }

    /**
     * Replaces {@code roleId}'s permission set wholesale. Only the permissions being <em>added</em>
     * need to already be held by the actor; removing one is never guarded.
     */
    @Transactional
    public RoleResponse updateRolePermissions(UUID roleId, UpdateRolePermissionsRequest request) {
        Role role = roles.findWithPermissionsById(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));
        Set<String> current = role.getPermissions();
        Set<String> requested = requireKnownPermissions(request.permissions());

        Set<String> added = new LinkedHashSet<>(requested);
        added.removeAll(current);
        guardrails.requireHeldByActor(added, actorPermissions());
        guardrails.requireAnotherAccessManagerIfRoleLosesIt(roleId, current, requested);

        if (current.equals(requested)) {
            // Resubmitted unchanged. Nothing is written and nothing is audited, matching
            // AcademicSessionService#makeCurrent: a double-click on Save is not an event.
            return AccessDirectory.toResponse(role);
        }

        role.replacePermissions(requested);
        roles.saveAndFlush(role);

        audit.recordChange(AuditAction.ENTITY_UPDATED, "ROLE", roleId.toString(), List.of("permissions"));

        return AccessDirectory.toResponse(role);
    }

    /**
     * Grants a role to a user. Hands the holder every permission the role carries, so the acting
     * account must hold all of them too.
     */
    @Transactional
    public GrantResponse grantRole(UUID accountId, GrantRoleRequest request) {
        requireAccountExists(accountId);
        Role role = roles.findWithPermissionsById(request.roleId())
                .orElseThrow(() -> new NotFoundException("Role", request.roleId()));
        AccessScope scope = validatedScope(request);
        guardrails.requireHeldByActor(role.getPermissions(), actorPermissions());

        UserRoleGrant grant = new UserRoleGrant(
                accountId, role, scope.type(), scope.targetId(), request.validFrom(), request.validTo());
        grants.saveAndFlush(grant);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                "USER_ROLE_GRANT",
                grant.getId().toString(),
                List.of("userAccountId", "role", "scopeType", "scopeId", "validFrom", "validTo"));

        return toResponse(grant);
    }

    /** Refuses to proceed if this is the grant that leaves nobody able to manage access. */
    @Transactional
    public void revokeGrant(UUID accountId, UUID grantId) {
        UserRoleGrant grant = grants.findById(grantId).orElseThrow(() -> new NotFoundException("Grant", grantId));
        if (!grant.getUserAccountId().equals(accountId)) {
            // Belongs to a different account: answered as "no such grant for this user" rather than
            // leaking that the id exists at all under someone else's account.
            throw new NotFoundException("Grant", grantId);
        }
        guardrails.requireAnotherAccessManagerIfThisGrantIsOne(grant);

        grants.delete(grant);
        audit.recordChange(AuditAction.ENTITY_DELETED, "USER_ROLE_GRANT", grantId.toString(), List.of());
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    private Set<String> actorPermissions() {
        return authentication.currentUser().permissions();
    }

    private void requireAccountExists(UUID accountId) {
        if (!accounts.existsById(accountId)) {
            throw new NotFoundException("Account", accountId);
        }
    }

    private Set<String> requireKnownPermissions(List<String> permissions) {
        Set<String> unique = new LinkedHashSet<>(permissions);
        List<String> unknown =
                unique.stream().filter(code -> !catalog.contains(code)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new ChalkbaseException(
                    PlatformErrorCode.VALIDATION_FAILED,
                    "One or more permissions do not exist",
                    Map.of("permissions", String.join(", ", unknown)));
        }
        return unique;
    }

    /**
     * {@code scopeId} is required for a scope narrower than the school and forbidden for one that
     * is not, and {@code WARD} may never be assigned directly — checked here so the failure is a
     * clear {@code VAL_001} rather than {@code ck_user_role_grant_scope}'s name.
     */
    private AccessScope validatedScope(GrantRoleRequest request) {
        ScopeType type = request.scopeType();
        if (type == ScopeType.WARD) {
            throw new ChalkbaseException(
                    PlatformErrorCode.VALIDATION_FAILED,
                    "A WARD scope cannot be assigned; it is derived from the guardian relationship",
                    Map.of("scopeType", "WARD"));
        }
        boolean needsTarget = type != ScopeType.SCHOOL && type != ScopeType.SELF;
        if (needsTarget && request.scopeId() == null) {
            throw new ChalkbaseException(
                    PlatformErrorCode.VALIDATION_FAILED,
                    "This scope needs a target",
                    Map.of("scopeId", "required for " + type));
        }
        if (!needsTarget && request.scopeId() != null) {
            throw new ChalkbaseException(
                    PlatformErrorCode.VALIDATION_FAILED,
                    "This scope must not carry a target",
                    Map.of("scopeId", "must be absent for " + type));
        }
        return new AccessScope(type, request.scopeId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String uniqueCode(String base) {
        if (!roles.existsByCode(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = RoleCode.withSuffix(base, suffix);
            if (!roles.existsByCode(candidate)) {
                return candidate;
            }
        }
        // Effectively unreachable — it needs 999 same-named roles at one school — and refusing
        // outright beats looping forever or silently colliding on uq_role_code.
        throw new ChalkbaseException(IdentityErrorCode.ROLE_NAME_TAKEN);
    }

    private static GrantResponse toResponse(UserRoleGrant grant) {
        return new GrantResponse(
                grant.getId(),
                grant.getRole().getId(),
                grant.getRole().getName(),
                grant.getScopeType().name(),
                grant.getScopeId(),
                grant.getValidFrom(),
                grant.getValidTo());
    }
}
