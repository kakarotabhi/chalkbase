package in.chalkbase.identity.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Ends every session belonging to one account, right now (ADR-0023).
 *
 * <p>Re-validation ({@link AccountStanding}) is what stops a session from continuing to work once
 * its account is disabled or locked, but it is passive: the session still exists, and the next
 * request is what discovers it is no longer good. This service is the active counterpart — it is
 * what an admin action reaches for when leaving the old cookie merely non-functional is not enough,
 * chiefly a password reset (the old credential the session was issued under is gone; leaving the
 * session itself alive would let its holder keep reading {@code /api/me} and retrying
 * {@code POST /api/auth/password} against a current password they can never supply) and a
 * deactivation (no reason to wait for the next request when the answer is already known).
 *
 * <p><strong>Why this needs its own index, and cannot use Spring Session's default one.</strong> By
 * default {@code JdbcIndexedSessionRepository} indexes {@code principal_name} from the
 * authenticated {@code Authentication}'s name, which for this application is the
 * <em>username</em> — deliberately readable in logs (see {@code AuthenticatedUser#getName()}).
 * Usernames are unique only within one school's schema (ADR-0017): two schools may both issue a
 * parent the username {@code 2026-0412}. Looking sessions up by that value across the single
 * cross-tenant {@code public.spring_session} table would revoke a stranger's session at another
 * school that happens to share a username. {@link #principalIndexValue} is schema-qualified by the
 * account's UUIDv7 id instead, which cannot collide across schools, and
 * {@code AuthenticationService} sets it as an explicit session attribute at login — the officially
 * supported way to give Spring Session an index value other than the security principal's name.
 */
@Service
public class SessionInvalidationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionInvalidationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** The value stored under {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}. */
    public static String principalIndexValue(String schema, UUID accountId) {
        return schema + ":" + accountId;
    }

    /**
     * Deletes every live session for this account and reports how many there were.
     *
     * <p>Deleting rather than merely marking is deliberate: a session with no row is a cookie with
     * nothing behind it at all, which fails at the very first filter rather than reaching as far as
     * {@code SessionStandingFilter}'s re-validation.
     */
    public int invalidateSessionsFor(String schema, UUID accountId) {
        Map<String, ? extends Session> live = sessions.findByPrincipalName(principalIndexValue(schema, accountId));
        live.keySet().forEach(sessions::deleteById);
        return live.size();
    }
}
