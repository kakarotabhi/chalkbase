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
 * @param permissions permission codes, each of which is also a Spring Security authority
 * @param scopes how far each grant reaches; empty means the user holds no grants at all
 */
public record EffectiveAccess(Set<String> permissions, Set<AccessScope> scopes) implements Serializable {

    private static final EffectiveAccess NONE = new EffectiveAccess(Set.of(), Set.of());

    public EffectiveAccess {
        permissions = Set.copyOf(permissions);
        scopes = Set.copyOf(scopes);
    }

    /** A user with no grants. Authenticated, and able to do nothing but change their own password. */
    public static EffectiveAccess none() {
        return NONE;
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}
