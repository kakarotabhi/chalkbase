package in.chalkbase.identity.application;

import in.chalkbase.platform.security.AccessScope;
import java.io.Serializable;
import java.util.Set;

/**
 * What one user may do at one school, resolved once at login (ADR-0005).
 *
 * <p><strong>The union of the user's grants.</strong> There are no deny rules, deliberately: a deny
 * that overrides an allow makes "why can't I see this?" unanswerable, interacts badly with a user
 * who holds several grants, and defeats the requirement that an administrator can preview the
 * impact of a role before assigning it. To take access away, take the permission out of that
 * school's role.
 *
 * <p>Computed once and cached against the session, never per request. The catalogue is a few
 * hundred permissions, a role holds tens and a user holds a handful of grants, so this is one query
 * at login rather than one per authorization check.
 *
 * <p>Permissions and scopes are kept as two sets rather than as scopes-per-permission. That is
 * enough for the two enforcement layers as they stand — a permission check decides whether the
 * action is allowed at all, and the scopes narrow the query — and no shipped endpoint yet reads
 * rows that a scope would narrow. Pairing them would be a change here and in the query helpers, not
 * a change to the model.
 *
 * <p>The role codes are carried alongside, and they are <strong>not</strong> an authorization
 * input — nothing checks them, and code checks permissions only (ADR-0005). They exist because
 * FR-008 requires an audited action to be traceable to a user <em>and a role</em>, and because
 * ADR-0018 records the roles held at the time as a snapshot: reading them back off the account
 * afterwards would let a later role change rewrite the past.
 *
 * @param permissions permission codes, each of which is also a Spring Security authority
 * @param scopes how far each grant reaches; empty means the user holds no grants at all
 * @param roles the codes of the roles these permissions came from. For the audit trail, never for
 *     a decision.
 */
public record EffectiveAccess(Set<String> permissions, Set<AccessScope> scopes, Set<String> roles)
        implements Serializable {

    private static final EffectiveAccess NONE = new EffectiveAccess(Set.of(), Set.of(), Set.of());

    public EffectiveAccess {
        permissions = Set.copyOf(permissions);
        scopes = Set.copyOf(scopes);
        roles = Set.copyOf(roles);
    }

    /** A user with no grants. Authenticated, and able to do nothing but change their own password. */
    public static EffectiveAccess none() {
        return NONE;
    }

    /** The role codes, sorted and comma-separated — the snapshot shape {@code audit_event} stores. */
    public String rolesAsSnapshot() {
        return roles.isEmpty() ? null : roles.stream().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
