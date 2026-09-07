package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.AccountStatus;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.identity.domain.UserRoleGrant;
import in.chalkbase.identity.infrastructure.IdentityPermissions;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserRoleGrantRepository;
import in.chalkbase.platform.error.ChalkbaseException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two risks a self-service access-management screen creates, that a code review would
 * otherwise be the only thing catching (item 2 and item 3 of the identity write-endpoint
 * milestone).
 *
 * <ol>
 *   <li><strong>Nobody left who can manage access.</strong> Deactivating an account, revoking a
 *       grant, or editing a role to drop {@link IdentityPermissions#ROLE_MANAGE} can each, on their
 *       own, leave a school with no active account holding that permission — and from that moment
 *       nothing about access can ever be fixed through the product again, only by someone with
 *       direct database access. The guard is on the <em>outcome</em>: it asks "would at least one
 *       active account still be able to manage access after this?", never "is this the account that
 *       happened to do it" — an admin may deactivate themselves, or have their own grant revoked, as
 *       long as somebody else still can.
 *   <li><strong>Privilege escalation through the one permission that grants every other.</strong>
 *       {@code identity:role:manage} is not scoped to particular permissions (ADR-0005 says code
 *       checks permissions, never a role name, and does not further restrict what a role-manager may
 *       assign), so nothing stops a holder creating a role with a permission they do not themselves
 *       have, or granting one that does, unless something here does. {@link #requireHeldByActor}
 *       is that something: an actor may only add to a role, or grant through a role, a permission
 *       that their own session currently holds. Removing a permission from a role, or revoking a
 *       grant, is never guarded this way — taking access away cannot escalate anything.
 * </ol>
 *
 * <p>Both checks read the <strong>current</strong> state of {@code user_role_grant} and
 * {@code user_account}, not the session cache: unlike an ordinary permission check, this one is
 * about the shape of the school's access as a whole, which a single session's resolved authorities
 * cannot answer.
 */
@Service
@Transactional(readOnly = true)
public class AccessGuardrails {

    private final UserRoleGrantRepository grants;
    private final UserAccountRepository accounts;

    public AccessGuardrails(UserRoleGrantRepository grants, UserAccountRepository accounts) {
        this.grants = grants;
        this.accounts = accounts;
    }

    /**
     * Refuses to proceed if deactivating {@code accountId} would leave no active account able to
     * manage access.
     */
    public void requireAnotherAccessManagerIfThisIsOne(UUID accountId) {
        Set<UUID> managers = activeAccessManagers(List.of());
        if (managers.size() == 1 && managers.contains(accountId)) {
            throw new ChalkbaseException(IdentityErrorCode.LAST_ACCESS_MANAGER);
        }
    }

    /** Refuses to proceed if revoking this specific grant would leave no active access manager at all. */
    public void requireAnotherAccessManagerIfThisGrantIsOne(UserRoleGrant grant) {
        if (activeAccessManagers(List.of(grant.getId())).isEmpty()) {
            throw new ChalkbaseException(IdentityErrorCode.LAST_ACCESS_MANAGER);
        }
    }

    /**
     * Refuses to proceed if replacing {@code roleId}'s permission set with {@code newPermissions}
     * would leave no active access manager at all — i.e. the role currently carries
     * {@code identity:role:manage}, {@code newPermissions} does not, and nobody else's grant covers
     * it either.
     */
    public void requireAnotherAccessManagerIfRoleLosesIt(
            UUID roleId, Set<String> currentPermissions, Set<String> newPermissions) {
        boolean losingIt = currentPermissions.contains(IdentityPermissions.ROLE_MANAGE)
                && !newPermissions.contains(IdentityPermissions.ROLE_MANAGE);
        if (!losingIt) {
            return;
        }
        List<UUID> grantIdsOnThisRole =
                grants.findByRole_Id(roleId).stream().map(UserRoleGrant::getId).toList();
        if (activeAccessManagers(grantIdsOnThisRole).isEmpty()) {
            throw new ChalkbaseException(IdentityErrorCode.LAST_ACCESS_MANAGER);
        }
    }

    /**
     * Refuses to proceed if any permission in {@code requested} is one {@code actorPermissions} does
     * not already hold. Pass the full target permission set — for a role edit, only the ones being
     * <em>added</em> need to pass this, so filter to the difference before calling.
     */
    public void requireHeldByActor(Set<String> requested, Set<String> actorPermissions) {
        Set<String> notHeld = new LinkedHashSet<>(requested);
        notHeld.removeAll(actorPermissions);
        if (!notHeld.isEmpty()) {
            throw new ChalkbaseException(IdentityErrorCode.CANNOT_GRANT_PERMISSION_YOU_DO_NOT_HOLD);
        }
    }

    /**
     * Every active account that could manage access today, ignoring the named grants — as if they
     * had already been removed. An empty collection means "as things stand right now."
     */
    private Set<UUID> activeAccessManagers(Collection<UUID> ignoringGrantIds) {
        List<UserRoleGrant> inForce = grants.findInForceGranting(IdentityPermissions.ROLE_MANAGE, LocalDate.now());
        Set<UUID> holderIds = new LinkedHashSet<>();
        for (UserRoleGrant grant : inForce) {
            if (!ignoringGrantIds.contains(grant.getId())) {
                holderIds.add(grant.getUserAccountId());
            }
        }
        if (holderIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(accounts.findIdsByIdInAndStatus(holderIds, AccountStatus.ACTIVE));
    }
}
