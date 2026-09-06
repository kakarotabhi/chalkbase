package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.CredentialStatus;
import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.IdentifierType;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.domain.UserCredential;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserCredentialRepository;
import in.chalkbase.identity.infrastructure.UserIdentifierRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the identity tables of whichever school is currently bound.
 *
 * <p><strong>Every method here must be called with a tenant already bound</strong> — the tables are
 * reached through {@code search_path} and there is no schema argument to pass. That is why the
 * transaction boundary is here and not on {@code AuthenticationService}: Hibernate resolves the
 * tenant when it opens the session at the start of a transaction, so a transaction started before
 * the schema was chosen would run every statement against {@code public}.
 *
 * <p>The steps of a login are separate transactions on purpose. A failed attempt has to be
 * recorded, and recording it inside the transaction that then throws would roll the counter back —
 * the lockout would never trigger.
 */
@Service
@Transactional(readOnly = true)
public class UserAccountService {

    /** Failures before the account locks. FR-006 requires lockout; five is the usual bar. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** Long enough to make guessing pointless, short enough that a parent is not stranded. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserAccountRepository accounts;
    private final UserIdentifierRepository identifiers;
    private final UserCredentialRepository credentials;

    public UserAccountService(
            UserAccountRepository accounts,
            UserIdentifierRepository identifiers,
            UserCredentialRepository credentials) {
        this.accounts = accounts;
        this.identifiers = identifiers;
        this.credentials = credentials;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return identifiers
                .findWithAccount(IdentifierType.USERNAME, username)
                .map(identifier -> identifier.getAccount());
    }

    public Optional<UserAccount> findById(UUID accountId) {
        return accounts.findById(accountId);
    }

    /**
     * Whether this account is still on the password its school issued it.
     *
     * <p>Read from the account row rather than from the session, deliberately, and this is the one
     * decision in the forced-change enforcement worth arguing about. The flag changes <em>during</em>
     * a session — that is the whole point of it, and it is already why {@code SessionBootstrap}
     * reads the row for {@code /api/me} instead of trusting the principal. A copy on the principal
     * would be a second answer to the same question, and the two disagreeing means either
     * {@code /api/me} tells a client to go and change a password the filter has already stopped
     * requiring, or the filter keeps refusing an account that has changed it. Neither is a bug
     * anyone would find quickly.
     *
     * <p>The cost is one primary-key select per API call for a signed-in session, which is why
     * {@link UserAccountRepository#findMustChangePassword} projects a single column instead of
     * loading the entity.
     *
     * <p><strong>An account that no longer exists answers true.</strong> Fail closed: a session
     * pointing at a deleted account is not a session that should keep reading a school's data while
     * we decide what to call it. The client's next {@code /api/me} answers 401 and sends it to the
     * login screen, which is where it can actually recover.
     */
    public boolean mustChangePassword(UUID accountId) {
        return accounts.findMustChangePassword(accountId).orElse(true);
    }

    public Optional<UserCredential> activeCredential(UUID accountId, CredentialType type) {
        return credentials.findByAccount_IdAndTypeAndStatus(accountId, type, CredentialStatus.ACTIVE);
    }

    @Transactional
    public void recordSuccessfulLogin(UUID accountId, UUID credentialId) {
        Instant now = Instant.now();
        accounts.findById(accountId).ifPresent(account -> account.recordSuccessfulLogin(now));
        credentials.findById(credentialId).ifPresent(credential -> credential.markUsed(now));
    }

    /**
     * Counts a failure and locks the account once {@link #MAX_FAILED_ATTEMPTS} have failed.
     *
     * @return true when this failure is the one that locked the account, so the caller can audit
     *     the lockout once instead of on every attempt afterwards
     */
    @Transactional
    public boolean recordFailedAttempt(UUID accountId) {
        return accounts.findById(accountId)
                .map(account -> account.recordFailedAttempt(Instant.now(), MAX_FAILED_ATTEMPTS, LOCK_DURATION))
                .orElse(false);
    }

    /**
     * Replaces the stored secret and clears the forced-change flag.
     *
     * <p>The credential row is updated in place rather than revoked and replaced: the partial unique
     * index allows only one ACTIVE credential per type, so inserting the new one before revoking the
     * old would violate it, and the previous hash is of no audit value once it is gone.
     */
    @Transactional
    public void changePassword(UUID accountId, UUID credentialId, String encodedSecret) {
        credentials.findById(credentialId).ifPresent(credential -> credential.replaceSecret(encodedSecret));
        accounts.findById(accountId).ifPresent(UserAccount::passwordChanged);
    }
}
