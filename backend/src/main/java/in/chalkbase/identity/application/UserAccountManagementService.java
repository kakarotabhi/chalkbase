package in.chalkbase.identity.application;

import in.chalkbase.identity.api.CreateUserAccountRequest;
import in.chalkbase.identity.api.NewUserAccountResponse;
import in.chalkbase.identity.api.TemporaryPasswordResponse;
import in.chalkbase.identity.api.UserAccountResponse;
import in.chalkbase.identity.domain.CredentialStatus;
import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.IdentifierType;
import in.chalkbase.identity.domain.TemporaryPasswordGenerator;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.domain.UserCredential;
import in.chalkbase.identity.domain.UserIdentifier;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserCredentialRepository;
import in.chalkbase.identity.infrastructure.UserIdentifierRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating an account, and the four things an admin can do to one afterwards (item 2 of the
 * identity write-endpoint milestone).
 *
 * <p>Unlike {@link AuthenticationService} and {@link UserAccountService}, these methods run as
 * ordinary {@code @Transactional} service calls: they are reached through
 * {@code /api/access/users/**}, an authenticated endpoint {@code SessionTenantFilter} has already
 * bound a tenant for by the time a controller method runs, so there is no circularity to work
 * around here the way login has.
 *
 * <p>Deactivating, reactivating and unlocking are all idempotent (repeating one is not an error and
 * writes nothing the second time) — a double-click on the button is not an event, matching
 * {@code AcademicSessionService#makeCurrent}.
 */
@Service
@Transactional(readOnly = true)
public class UserAccountManagementService {

    private final UserAccountRepository accounts;
    private final UserIdentifierRepository identifiers;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final SessionInvalidationService sessionInvalidation;
    private final AccessGuardrails guardrails;
    private final AuthenticationService authentication;

    public UserAccountManagementService(
            UserAccountRepository accounts,
            UserIdentifierRepository identifiers,
            UserCredentialRepository credentials,
            PasswordEncoder passwordEncoder,
            AuditService audit,
            SessionInvalidationService sessionInvalidation,
            AccessGuardrails guardrails,
            AuthenticationService authentication) {
        this.accounts = accounts;
        this.identifiers = identifiers;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.sessionInvalidation = sessionInvalidation;
        this.guardrails = guardrails;
        this.authentication = authentication;
    }

    /**
     * A new account, with a generated temporary password (ADR-0023) and
     * {@code mustChangePassword = true} — the existing forced-change machinery
     * ({@code SessionStandingFilter}) is what makes that password unusable for anything but
     * replacing itself.
     */
    @Transactional
    public NewUserAccountResponse create(CreateUserAccountRequest request) {
        UserAccount account =
                accounts.saveAndFlush(new UserAccount(request.displayName().trim()));
        identifiers.saveAndFlush(new UserIdentifier(
                account, IdentifierType.USERNAME, request.username().trim()));

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        credentials.saveAndFlush(
                new UserCredential(account, CredentialType.PASSWORD, passwordEncoder.encode(temporaryPassword)));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                "USER_ACCOUNT",
                account.getId().toString(),
                List.of("displayName", "username"));

        return new NewUserAccountResponse(
                account.getId(), request.username().trim(), account.getDisplayName(), temporaryPassword);
    }

    /**
     * Refuses to leave the school with nobody able to manage access ({@code IdentityErrorCode
     * #LAST_ACCESS_MANAGER}, via {@link AccessGuardrails}).
     */
    @Transactional
    public UserAccountResponse deactivate(UUID accountId) {
        UserAccount account = require(accountId);
        guardrails.requireAnotherAccessManagerIfThisIsOne(accountId);

        if (account.disable()) {
            accounts.saveAndFlush(account);
            audit.recordChange(AuditAction.ENTITY_UPDATED, "USER_ACCOUNT", accountId.toString(), List.of("status"));
            // Belt and braces alongside SessionStandingFilter's passive re-validation: a school
            // expects "deactivate" to take effect now, not on the account's next request.
            int endedSessions = sessionInvalidation.invalidateSessionsFor(currentSchema(), accountId);
            if (endedSessions > 0) {
                audit.recordSecurityEvent(
                        AuditAction.SESSION_REVOKED, AuditOutcome.SUCCESS, "USER_ACCOUNT", accountId.toString());
            }
        }
        return toResponse(account);
    }

    @Transactional
    public UserAccountResponse reactivate(UUID accountId) {
        UserAccount account = require(accountId);
        if (account.reactivate()) {
            accounts.saveAndFlush(account);
            audit.recordChange(AuditAction.ENTITY_UPDATED, "USER_ACCOUNT", accountId.toString(), List.of("status"));
        }
        return toResponse(account);
    }

    /** Clears a lockout early. Never needed for a disabled account — that is {@link #reactivate}'s job. */
    @Transactional
    public UserAccountResponse unlock(UUID accountId) {
        UserAccount account = require(accountId);
        if (account.clearLockout()) {
            accounts.saveAndFlush(account);
            audit.recordChange(
                    AuditAction.ENTITY_UPDATED,
                    "USER_ACCOUNT",
                    accountId.toString(),
                    List.of("lockedUntil", "failedAttempts"));
        }
        return toResponse(account);
    }

    /**
     * Issues a new temporary password and ends every session the target currently holds.
     *
     * <p>The order is the whole method: the new credential is written and committed to this
     * transaction first — {@code recordChange} joins it, so if the write fails nothing is claimed
     * to have happened — and only once that has succeeded are the old sessions torn down. Ending
     * them first and then failing to write the new credential would strand the account signed out
     * of everywhere with no way back in.
     */
    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID accountId) {
        UserAccount account = require(accountId);
        UUID actingAdminId = authentication.currentUser().userId();

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        UserCredential credential = credentials
                .findByAccount_IdAndTypeAndStatus(accountId, CredentialType.PASSWORD, CredentialStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Credential for account", accountId));
        credential.replaceSecret(passwordEncoder.encode(temporaryPassword));
        credentials.saveAndFlush(credential);

        Instant now = Instant.now();
        account.recordAdminPasswordReset(now, actingAdminId);
        accounts.saveAndFlush(account);

        // The action, never the secret. Neither the new password nor its hash goes anywhere near an
        // audit method — there is no parameter here that would take one (AGENTS.md rule 11).
        audit.recordSecurityEvent(
                AuditAction.PASSWORD_RESET_BY_ADMIN, AuditOutcome.SUCCESS, "USER_ACCOUNT", accountId.toString());

        int endedSessions = sessionInvalidation.invalidateSessionsFor(currentSchema(), accountId);
        if (endedSessions > 0) {
            audit.recordSecurityEvent(
                    AuditAction.SESSION_REVOKED, AuditOutcome.SUCCESS, "USER_ACCOUNT", accountId.toString());
        }

        return new TemporaryPasswordResponse(accountId, temporaryPassword);
    }

    private UserAccountResponse toResponse(UserAccount account) {
        return new UserAccountResponse(
                account.getId(),
                account.getDisplayName(),
                account.getStatus().name(),
                account.isMustChangePassword(),
                account.getLockedUntil(),
                account.getLastLoginAt());
    }

    private UserAccount require(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account", accountId));
    }

    private String currentSchema() {
        return TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException("No tenant bound for a user-account management call"));
    }
}
