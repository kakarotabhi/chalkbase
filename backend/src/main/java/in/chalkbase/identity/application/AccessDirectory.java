package in.chalkbase.identity.application;

import in.chalkbase.identity.api.GrantResponse;
import in.chalkbase.identity.api.RoleResponse;
import in.chalkbase.identity.api.UserSummary;
import in.chalkbase.identity.domain.Role;
import in.chalkbase.identity.domain.UserRoleGrant;
import in.chalkbase.identity.infrastructure.RoleRepository;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserRoleGrantRepository;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.security.PermissionDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for the access screens: what may be granted, what this school has bundled, and who
 * could hold it.
 *
 * <p>The three answers come from three different places, and the difference is the whole of
 * ADR-0005. The permission list comes from <strong>code</strong> and is identical at every school.
 * The roles come from <strong>this school's own tables</strong> and are identical nowhere. The
 * users come from this school's schema.
 *
 * <p>Everything here is tenant-scoped except the catalogue, so it must be called with a tenant
 * bound — which it is, from the session, before a controller is reached.
 */
@Service
@Transactional(readOnly = true)
public class AccessDirectory {

    private final PermissionCatalog catalog;
    private final RoleRepository roles;
    private final UserAccountRepository accounts;
    private final UserRoleGrantRepository grants;

    public AccessDirectory(
            PermissionCatalog catalog,
            RoleRepository roles,
            UserAccountRepository accounts,
            UserRoleGrantRepository grants) {
        this.catalog = catalog;
        this.roles = roles;
        this.accounts = accounts;
        this.grants = grants;
    }

    /** Every permission this build declares. Read from the catalogue, not from the seeded table. */
    public List<PermissionDefinition> permissions() {
        return catalog.all();
    }

    public List<RoleResponse> roles() {
        return roles.findAllByOrderByNameAsc().stream()
                .map(AccessDirectory::toResponse)
                .toList();
    }

    public List<UserSummary> users() {
        return accounts.findAll().stream()
                .map(account -> new UserSummary(
                        account.getId(),
                        account.getDisplayName(),
                        account.getStatus().name()))
                .sorted(Comparator.comparing(UserSummary::displayName))
                .toList();
    }

    /**
     * Every account currently holding {@code roleId}, regardless of scope or validity window.
     *
     * <p>The raw material a future "who is affected by this change" screen needs (the acceptance
     * note behind FR-004 — see {@code AccessController}'s own Javadoc for why the interactive
     * preview itself is not built in this wave). Unfiltered by date deliberately: an admin editing a
     * role wants to know about a grant starting next month too, not just one in force today.
     */
    public List<UserSummary> holders(UUID roleId) {
        requireRole(roleId);
        Set<UUID> accountIds = grants.findByRole_Id(roleId).stream()
                .map(UserRoleGrant::getUserAccountId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return accounts.findAllById(accountIds).stream()
                .map(account -> new UserSummary(
                        account.getId(),
                        account.getDisplayName(),
                        account.getStatus().name()))
                .sorted(Comparator.comparing(UserSummary::displayName))
                .toList();
    }

    /** Every grant one account holds, regardless of validity window — so an expired or future one is still visible to edit. */
    public List<GrantResponse> grantsFor(UUID accountId) {
        return grants.findByUserAccountId(accountId).stream()
                .map(AccessDirectory::toResponse)
                .sorted(Comparator.comparing(GrantResponse::roleName))
                .toList();
    }

    private void requireRole(UUID roleId) {
        if (!roles.existsById(roleId)) {
            throw new NotFoundException("Role", roleId);
        }
    }

    static RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getTemplateCode(),
                role.getPermissions().stream().sorted().toList());
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
