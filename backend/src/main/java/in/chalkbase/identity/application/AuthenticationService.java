package in.chalkbase.identity.application;

import in.chalkbase.identity.api.ChangePasswordRequest;
import in.chalkbase.identity.api.LoginRequest;
import in.chalkbase.identity.api.LoginResponse;
import in.chalkbase.identity.api.SchoolSummary;
import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.identity.domain.PasswordPolicy;
import in.chalkbase.identity.domain.SessionDuration;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.domain.UserCredential;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final Map<CredentialType, CredentialVerifier> verifiers = new EnumMap<>(CredentialType.class);

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthenticationService(
            SchoolLookup schools,
            UserAccountService users,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            List<CredentialVerifier> credentialVerifiers) {
        this.schools = schools;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
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

        UserAccount account = inSchool(school.schemaName(), () -> authenticate(request));

        establishSession(http, response, school, account, request.username(), request.isRemembered());
        // The school code is safe to log; the username is not — it is usually a child's admission
        // number. Never add it here.
        log.info("Login succeeded for school {} (account {})", school.code(), account.getId());

        return new LoginResponse(
                account.getId(),
                account.getDisplayName(),
                account.isMustChangePassword(),
                new SchoolSummary(school.code(), school.name()));
    }

    /** Ends the session. Idempotent: signing out twice is not an error. */
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            // Server-side invalidation, not just a discarded cookie — forced logout is FR-006.
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();
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

    /** Runs inside the tenant. The account it returns is detached and safe to read afterwards. */
    private UserAccount authenticate(LoginRequest request) {
        UserAccount account =
                users.findByUsername(request.username()).orElseThrow(() -> unauthenticated("no such username"));

        // Checked before the password is verified: an attacker must not be able to keep guessing
        // simply because every guess is wrong.
        if (account.isLocked(Instant.now())) {
            log.warn("Login refused: account {} is locked", account.getId());
            throw new ChalkbaseException(IdentityErrorCode.ACCOUNT_LOCKED);
        }

        UserCredential credential =
                users.activeCredential(account.getId(), CredentialType.PASSWORD).orElse(null);
        if (credential == null || !verifier(CredentialType.PASSWORD).verify(credential, request.password())) {
            users.recordFailedAttempt(account.getId());
            throw unauthenticated("wrong password");
        }

        // Checked after the password, not before: a disabled account must not be discoverable by
        // someone who does not already know its password.
        if (!account.isActive()) {
            log.warn("Login refused: account {} is {}", account.getId(), account.getStatus());
            throw new ChalkbaseException(IdentityErrorCode.ACCOUNT_LOCKED);
        }

        users.recordSuccessfulLogin(account.getId(), credential.getId());
        account.recordSuccessfulLogin(Instant.now());
        return account;
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

    private void establishSession(
            HttpServletRequest http,
            HttpServletResponse response,
            SchoolRef school,
            UserAccount account,
            String username,
            boolean remembered) {
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

        AuthenticatedUser principal = new AuthenticatedUser(account.getId(), username, school.schemaName());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
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
