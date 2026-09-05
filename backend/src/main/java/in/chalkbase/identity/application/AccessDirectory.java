package in.chalkbase.identity.application;

import in.chalkbase.identity.api.RoleResponse;
import in.chalkbase.identity.api.UserSummary;
import in.chalkbase.identity.domain.Role;
import in.chalkbase.identity.infrastructure.RoleRepository;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.platform.security.PermissionCatalog;
import in.chalkbase.platform.security.PermissionDefinition;
import java.util.Comparator;
import java.util.List;
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

    public AccessDirectory(PermissionCatalog catalog, RoleRepository roles, UserAccountRepository accounts) {
        this.catalog = catalog;
        this.roles = roles;
        this.accounts = accounts;
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

    private static RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getTemplateCode(),
                role.getPermissions().stream().sorted().toList());
    }
}
