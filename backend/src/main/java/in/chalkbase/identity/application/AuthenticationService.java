package in.chalkbase.identity.application;

import in.chalkbase.identity.api.ChangePasswordRequest;
import in.chalkbase.identity.api.LoginRequest;
import in.chalkbase.identity.api.LoginResponse;
import in.chalkbase.identity.api.SchoolSummary;
import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.IdentifierType;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.identity.domain.PasswordPolicy;
import in.chalkbase.identity.domain.SessionDuration;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.domain.UserCredential;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditActor;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.PlatformErrorCode;
import in.chalkbase.platform.tenancy.TenantContext;
import in.chalkbase.school.api.SchoolLookup;
import in.chalkbase.school.api.SchoolRef;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Signing in, signing out, and changing a password.
 *
 * <p>The order of a login is fixed by ADR-0017: school code &rarr; {@code public.school} &rarr; bind
 * the tenant &rarr; {@code user_identifier} &rarr; {@code user_credential} &rarr; session in
 * {@code public}. Something has to break the circle between "the cookie says which school" and "the
 * user record lives inside a school", and the school code on the login form is that something.
 *
 * <p>The last step of a login is resolving what the user may do. That happens exactly once, here,
 * and the result rides on the principal for the life of the session (ADR-0005) — there is no
 * per-request permission query. A role or grant change takes effect on the next login, or
 * immediately if the session is invalidated.
 *
 * <p>Every sign-in, failed sign-in, lockout, sign-out and password change is audited here, each in
 * <strong>its own transaction</strong> (ADR-0018 §4) — the same reasoning that already puts the
 * failed-attempt counter in its own transaction below. A failed sign-in must be recorded precisely
 * because it failed; recording it inside the transaction that then throws would roll the record
 * back along with the attempt, and the audit log would contain only the logins that worked.
 *
 * <p><strong>This class carries no transaction annotation, deliberately.</strong> Hibernate picks
 * the tenant when it opens a session at the start of a transaction, so a transaction opened here —
 * before {@link TenantContext} has been bound — would run every statement against {@code public}.
 * The transactions live in {@link UserAccountService}, which is only ever called from inside
 * {@link TenantContext#callWith}.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final SchoolLookup schools;
    private final UserAccountService users;
    private final AccessResolver accessResolver;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final AuditService audit;
    private final Map<CredentialType, CredentialVerifier> verifiers = new EnumMap<>(CredentialType.class);

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthenticationService(
            SchoolLookup schools,
            UserAccountService users,
            AccessResolver accessResolver,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            AuditService audit,
            List<CredentialVerifier> credentialVerifiers) {
        this.schools = schools;
        this.users = users;
        this.accessResolver = accessResolver;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.audit = audit;
        for (CredentialVerifier verifier : credentialVerifiers) {
            CredentialVerifier clash = this.verifiers.put(verifier.supports(), verifier);
            if (clash != null) {
                throw new IllegalStateException("Two verifiers claim " + verifier.supports());
            }
        }
    }

    /**
     * Authenticates against one school and establishes a session.
     *
     * <p>An unknown username and a wrong password both fail as {@code AUTH_001} with the same
     * sentence. That is not laziness: telling them apart lets anyone with the login form discover
     * which children are enrolled at a school.
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        SchoolRef school = schools.byCode(request.schoolCode())
                .orElseThrow(() -> new ChalkbaseException(IdentityErrorCode.UNKNOWN_SCHOOL));

        // Authenticating and resolving access happen in one tenant-bound block: the grants live in
        // the same schema as the account, and resolving them afterwards would need the tenant bound
        // a second time.
        SignedIn signedIn = inSchool(school.schemaName(), () -> {
            UserAccount account = authenticate(request, school.schemaName());
            return new SignedIn(account, accessResolver.resolveFor(account.getId(), LocalDate.now()));
        });
        UserAccount account = signedIn.account();

        establishSession(http, response, school, signedIn, request.username(), request.isRemembered());
        // Audited after the session exists, so the actor snapshot — id, display name and the roles
        // held at this moment — comes off the principal that was just established rather than from
        // a second lookup that could disagree with it. The tenant is no longer bound here; the
        // actor carries the schema, which is what an audit row belongs to.
        audit.recordSecurityEvent(
                AuditAction.LOGIN_SUCCEEDED, AuditOutcome.SUCCESS, IdentifierType.USERNAME.name(), request.username());

        // The school code is safe to log; the username is not — it is usually a child's admission
        // number. Never add it here.
        log.info("Login succeeded for school {} (account {})", school.code(), account.getId());

        return new LoginResponse(
                account.getId(),
                account.getDisplayName(),
                account.isMustChangePassword(),
                new SchoolSummary(school.code(), school.name()),
                signedIn.access().permissions().stream().sorted().toList());
    }

    /** Ends the session. Idempotent: signing out twice is not an error. */
    public void logout(HttpServletRequest http) {
        // Read before the context is cleared, or there would be nobody to attribute the sign-out to.
        AuditActor actor = currentActorOrNull();

        HttpSession session = http.getSession(false);
        if (session != null) {
            // Server-side invalidation, not just a discarded cookie — forced logout is FR-006.
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();

        if (actor != null) {
            audit.recordSecurityEvent(
                    AuditAction.LOGOUT, AuditOutcome.SUCCESS, "USER_ACCOUNT", String.valueOf(actor.id()), actor);
        }
        // A sign-out with no session had no principal and no school, so there is nothing to record
        // against. That is the idempotent case, not a gap.
    }

    /** Changes the signed-in user's password and clears the forced-change flag. */
    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser current = currentUser();

        if (!PasswordPolicy.isAcceptable(request.newPassword())) {
            throw new ChalkbaseException(IdentityErrorCode.WEAK_PASSWORD);
        }

        inSchool(current.schema(), () -> {
            UserAccount account = users.findById(current.userId())
                    .orElseThrow(() -> new ChalkbaseException(PlatformErrorCode.AUTHENTICATION_REQUIRED));
            UserCredential credential = users.activeCredential(account.getId(), CredentialType.PASSWORD)
                    .orElseThrow(() -> new ChalkbaseException(IdentityErrorCode.CURRENT_PASSWORD_WRONG));

            if (!verifier(CredentialType.PASSWORD).verify(credential, request.currentPassword())) {
                throw new ChalkbaseException(IdentityErrorCode.CURRENT_PASSWORD_WRONG);
            }

            users.changePassword(account.getId(), credential.getId(), passwordEncoder.encode(request.newPassword()));

            // The action, never the secret. Neither password, neither hash, and no session id goes
            // anywhere near an audit row — there is no parameter here that would take one.
            audit.recordSecurityEvent(
                    AuditAction.PASSWORD_CHANGED,
                    AuditOutcome.SUCCESS,
                    "USER_ACCOUNT",
                    account.getId().toString());
            return null;
        });

        log.info("Password changed for account {}", current.userId());
    }

    /** The signed-in user, from the security context the session filter chain restored. */
    public AuthenticatedUser currentUser() {
        Authentication authentication =
                securityContextHolderStrategy.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ChalkbaseException(PlatformErrorCode.AUTHENTICATION_REQUIRED);
        }
        return user;
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /**
     * Runs inside the tenant. The account it returns is detached and safe to read afterwards.
     *
     * <p>Every exit from here that is not a success is audited as {@code LOGIN_FAILED} first. The
     * attempt is attributable to a school — the school code was resolved before authentication was
     * attempted — but to no account, so the row carries no actor id: what is known is the
     * identifier that was tried, and that goes in {@code entity_id} as an identifier, never in a
     * value field.
     */
    private UserAccount authenticate(LoginRequest request, String schema) {
        UserAccount account = users.findByUsername(request.username()).orElseThrow(() -> {
            loginFailed(request.username(), schema);
            return unauthenticated("no such username");
        });

        // Checked before the password is verified: an attacker must not be able to keep guessing
        // simply because every guess is wrong.
        if (account.isLocked(Instant.now())) {
            log.warn("Login refused: account {} is locked", account.getId());
            loginFailed(request.username(), schema);
            throw new ChalkbaseException(IdentityErrorCode.ACCOUNT_LOCKED);
        }

        UserCredential credential =
                users.activeCredential(account.getId(), CredentialType.PASSWORD).orElse(null);
        if (credential == null || !verifier(CredentialType.PASSWORD).verify(credential, request.password())) {
            boolean lockedByThisAttempt = users.recordFailedAttempt(account.getId());
            loginFailed(request.username(), schema);
            if (lockedByThisAttempt) {
                // Recorded once, when the lock is applied — not on every attempt that follows it.
                audit.recordSecurityEvent(
                        AuditAction.ACCOUNT_LOCKED,
                        AuditOutcome.FAILURE,
                        "USER_ACCOUNT",
                        account.getId().toString(),
                        AuditActor.unauthenticated(schema));
            }
            throw unauthenticated("wrong password");
        }

        // Checked after the password, not before: a disabled account must not be discoverable by
        // someone who does not already know its password.
        if (!account.isActive()) {
            log.warn("Login refused: account {} is {}", account.getId(), account.getStatus());
            loginFailed(request.username(), schema);
            throw new ChalkbaseException(IdentityErrorCode.ACCOUNT_LOCKED);
        }

        users.recordSuccessfulLogin(account.getId(), credential.getId());
        account.recordSuccessfulLogin(Instant.now());
        return account;
    }

    /**
     * One failed sign-in, in its own transaction, with no actor.
     *
     * <p>The actor is stated as {@link AuditActor#unauthenticated} rather than left to be resolved:
     * a login form can be posted from a browser that still holds someone else's session, and
     * resolving the security context there would attribute a stranger's failed guess to whoever
     * last signed in on that machine.
     */
    private void loginFailed(String attemptedUsername, String schema) {
        audit.recordSecurityEvent(
                AuditAction.LOGIN_FAILED,
                AuditOutcome.FAILURE,
                IdentifierType.USERNAME.name(),
                attemptedUsername,
                AuditActor.unauthenticated(schema));
    }

    /** The signed-in user as an audit snapshot, or null when nobody is signed in. */
    private AuditActor currentActorOrNull() {
        Authentication authentication =
                securityContextHolderStrategy.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return new AuditActor(user.userId(), user.displayName(), user.rolesSnapshot(), user.schema());
    }

    private ChalkbaseException unauthenticated(String reason) {
        // The reason is for us, in the log. The client is told the same thing either way.
        log.warn("Login failed: {}", reason);
        return new ChalkbaseException(PlatformErrorCode.UNAUTHENTICATED);
    }

    private CredentialVerifier verifier(CredentialType type) {
        CredentialVerifier verifier = verifiers.get(type);
        if (verifier == null) {
            throw new IllegalStateException("No CredentialVerifier registered for " + type);
        }
        return verifier;
    }

    /** An authenticated account together with what it may do, both read inside the same tenant. */
    private record SignedIn(UserAccount account, EffectiveAccess access) {}

    private void establishSession(
            HttpServletRequest http,
            HttpServletResponse response,
            SchoolRef school,
            SignedIn signedIn,
            String username,
            boolean remembered) {
        UserAccount account = signedIn.account();
        HttpSession existing = http.getSession(false);
        if (existing != null) {
            // A new session id for a new principal: otherwise a session id an attacker planted
            // before the login survives it.
            existing.invalidate();
        }

        HttpSession session = http.getSession(true);
        // "Keep me signed in" is the only thing that lengthens a session. A shared machine at the
        // school counter keeps the short default, which is the case that matters.
        Duration idleTimeout = remembered ? SessionDuration.REMEMBERED : SessionDuration.DEFAULT;
        session.setMaxInactiveInterval((int) idleTimeout.toSeconds());
        session.setAttribute(SessionAttributes.SCHEMA, school.schemaName());
        session.setAttribute(SessionAttributes.USER_ID, account.getId());

        // The permission codes become the authorities, so `hasAuthority('school:school:read')` in a
        // @PreAuthorize is checked against exactly the set resolved above, with no second lookup and
        // no translation step where the two could disagree. Note there is no ROLE_ prefix and no
        // role name anywhere: code checks permissions only (ADR-0005).
        AuthenticatedUser principal = new AuthenticatedUser(
                account.getId(),
                username,
                account.getDisplayName(),
                school.schemaName(),
                new SchoolSummary(school.code(), school.name()),
                signedIn.access());
        List<GrantedAuthority> authorities = signedIn.access().permissions().stream()
                .sorted()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                .toList();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, http, response);
    }

    private <T> T inSchool(String schema, Callable<T> work) {
        try {
            return TenantContext.callWith(schema, work);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Tenant-scoped work failed for schema " + schema, ex);
        }
    }
}
