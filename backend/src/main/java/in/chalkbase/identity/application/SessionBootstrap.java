package in.chalkbase.identity.application;

import in.chalkbase.identity.api.MeResponse;
import in.chalkbase.identity.api.MeUser;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.PlatformErrorCode;
import in.chalkbase.platform.navigation.NavigationCatalog;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The session's own view of itself: who is signed in, at which school, what they may do, and the
 * menu that follows from it (ADR-0008).
 *
 * <p>Assembled from three sources with three different lifetimes, which is the only interesting
 * thing here. The <strong>permissions and the school</strong> come off the session — resolved once
 * at login and carried on the principal (ADR-0005), never recomputed, because ADR-0008 makes this
 * the first call of every page load and the first thing to break the app if it is slow. The
 * <strong>navigation</strong> comes from the catalogue, which is code, filtered by those
 * permissions. Only the <strong>account row</strong> is read from the database, and only because
 * {@code mustChangePassword} and the display name can change during a session and the client would
 * otherwise be told something that was true at login and is not now.
 *
 * <p>Tenant-scoped, so it must be called with a schema bound — which {@code SessionTenantFilter}
 * has done from the session before any controller is reached. The transaction lives here rather
 * than on the controller for the reason it always does in this module: Hibernate picks the tenant
 * when it opens a session at the start of a transaction.
 */
@Service
@Transactional(readOnly = true)
public class SessionBootstrap {

    private final AuthenticationService authentication;
    private final UserAccountService users;
    private final NavigationCatalog navigation;

    public SessionBootstrap(
            AuthenticationService authentication, UserAccountService users, NavigationCatalog navigation) {
        this.authentication = authentication;
        this.users = users;
        this.navigation = navigation;
    }

    public MeResponse describeCurrentSession() {
        AuthenticatedUser current = authentication.currentUser();

        // A session whose account has since been deleted is a session, not an account. Treating it
        // as unauthenticated sends the client to the login screen, which is where it can recover;
        // a 404 would leave it holding a cookie it cannot use and no idea why.
        UserAccount account = users.findById(current.userId())
                .orElseThrow(() -> new ChalkbaseException(PlatformErrorCode.AUTHENTICATION_REQUIRED));

        List<String> permissions = current.permissions().stream().sorted().toList();
        return new MeResponse(
                new MeUser(account.getId(), account.getDisplayName(), account.isMustChangePassword()),
                current.school(),
                PermissionsVersion.of(permissions),
                permissions,
                navigation.navigationFor(current.permissions()));
    }
}
