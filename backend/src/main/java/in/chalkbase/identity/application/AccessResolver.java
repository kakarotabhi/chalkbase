package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.UserRoleGrant;
import in.chalkbase.identity.infrastructure.UserRoleGrantRepository;
import in.chalkbase.platform.security.AccessScope;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a user's grants into the set of things they may do (ADR-0005).
 *
 * <p>grants &rarr; roles &rarr; permissions, unioned. Called once per login, from inside the
 * tenant: like everything in this module it reads tables that are only reachable through the
 * connection's {@code search_path}, so it must run inside {@code TenantContext.callWith}. The
 * transaction boundary is here rather than on {@code AuthenticationService} for the same reason
 * {@code UserAccountService}'s is — Hibernate picks the tenant when it opens a session at the start
 * of a transaction.
 */
@Service
@Transactional(readOnly = true)
public class AccessResolver {

    private static final Logger log = LoggerFactory.getLogger(AccessResolver.class);

    private final UserRoleGrantRepository grants;

    public AccessResolver(UserRoleGrantRepository grants) {
        this.grants = grants;
    }

    /**
     * Everything {@code userAccountId} may do on {@code on}.
     *
     * <p>The date is a parameter rather than {@code LocalDate.now()} so that "acting principal for
     * March" is testable without a clock abstraction, and so that a single login resolves against
     * one consistent date.
     *
     * <p>A grant outside its validity window is not weakened, it is absent — the repository does
     * not return it at all.
     */
    public EffectiveAccess resolveFor(UUID userAccountId, LocalDate on) {
        List<UserRoleGrant> inForce = grants.findInForce(userAccountId, on);
        if (inForce.isEmpty()) {
            log.debug("Account {} holds no grant in force on {}", userAccountId, on);
            return EffectiveAccess.none();
        }

        Set<String> permissions = new LinkedHashSet<>();
        Set<AccessScope> scopes = new LinkedHashSet<>();
        for (UserRoleGrant grant : inForce) {
            permissions.addAll(grant.getRole().getPermissions());
            scopes.add(grant.scope());
        }

        log.debug(
                "Account {} holds {} grant(s) giving {} permission(s)",
                userAccountId,
                inForce.size(),
                permissions.size());
        return new EffectiveAccess(permissions, scopes);
    }
}
