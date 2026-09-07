package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.AccountStanding;
import in.chalkbase.identity.application.AuthenticatedUser;
import in.chalkbase.identity.application.UserAccountService;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditActor;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.config.AuthenticatedApiFilter;
import in.chalkbase.platform.error.ErrorCode;
import in.chalkbase.platform.error.PlatformErrorCode;
import in.chalkbase.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Re-validates a session against its account on every API call (ADR-0023).
 *
 * <p><strong>What is re-checked, and why exactly this and no more.</strong> {@link AccountStanding}
 * — {@code status}, {@code lockedUntil} and {@code mustChangePassword} — three columns behind the
 * same indexed primary-key lookup this filter (previously {@code PasswordChangeRequiredFilter})
 * already made on every request for {@code mustChangePassword} alone. Widening that one query to
 * three columns is not a new round trip; it is the entire cost ADR-0023 was written to account for.
 * The effective permission set is deliberately <strong>not</strong> re-read here: it is a join
 * across {@code user_role_grant}, {@code role} and {@code role_permission}, not an indexed column,
 * and ADR-0005 already rejected paying that on every request. A role or grant change takes effect on
 * the next login, or immediately when the write that made it calls
 * {@code SessionInvalidationService} — the active counterpart to this filter's passive check.
 *
 * <p><strong>Two different problems, two different responses.</strong>
 *
 * <ul>
 *   <li>The account is disabled, or a lockout is in force: the session is no longer good for
 *       anything, so it is invalidated right here — the cookie the caller holds is now worthless —
 *       and the answer is the ordinary {@code AUTH_002} a client already knows to react to by
 *       forgetting its session and returning to sign-in. Recorded once, as
 *       {@link AuditAction#SESSION_REVOKED}: once, because the session is gone by the time this
 *       method returns, so there is no next request to record it again on.
 *   <li>{@code mustChangePassword}: the session stays valid but is narrowed to the handful of
 *       endpoints that let the restriction be lifted. This is the original, unwidened behaviour of
 *       this class from before ADR-0023, preserved exactly — see the per-endpoint notes below.
 * </ul>
 *
 * <h2>What is left open for a session that owes a password change</h2>
 *
 * Exactly three things, and each is here because refusing it would strand the user:
 *
 * <ul>
 *   <li>{@code POST /api/auth/password} — the change itself, or the restriction can never be lifted;
 *   <li>{@code POST /api/auth/logout} — walking away must always work;
 *   <li>{@code GET /api/me} — the client bootstraps from this call and learns from it that it has to
 *       redirect. A session that cannot bootstrap is a blank screen with no explanation.
 * </ul>
 *
 * <p>{@code POST /api/auth/login} is open too, which the list above does not obviously imply. A
 * browser posting a login still carries the old session cookie, so the security context is restored
 * before this filter runs; refusing it would leave someone who owes a change unable to sign in
 * again after a reload, which is precisely the state a reload puts them in (the temporary password
 * lives in the client's memory and is lost). The same reasoning does not extend to a disabled or
 * locked account: a fresh login attempt authenticates from scratch and invalidates the old session
 * itself before this filter would ever see the new one, so there is nothing here for it to strand.
 *
 * <p>Everything else under {@code /api/**} is refused, including {@code /api/schools/**}, which is
 * {@code permitAll} for onboarding. An <em>anonymous</em> call there still works — there is no
 * principal, so this filter passes it through — but a signed-in session has no business creating
 * schemas regardless of which of the two problems above it has.
 *
 * <h2>Why it is a chain filter and not a servlet filter</h2>
 *
 * The same reason {@code SetupKeyFilter} is: by the time {@code FilterChainProxy} reaches here the
 * request has been through {@code StrictHttpFirewall} and its path has been parsed, so this
 * filter's matchers and Spring Security's rules cannot disagree about what a path is. A
 * hand-rolled {@code startsWith} on {@code getRequestURI()} differs from Security's parsed path
 * over trailing slashes, path parameters and encoding, and every one of those differences would be
 * a way to reach {@code /api/students} with a session this filter believes it is refusing.
 *
 * <h2>403, with a reason</h2>
 *
 * {@link IdentityErrorCode#PASSWORD_CHANGE_REQUIRED} rather than the generic {@code PERM_001}: the
 * caller has proved they hold this account's credential and the account is their own, so telling
 * them what to do about it gives away nothing and is the entire purpose of the flag. It is a
 * distinct code so a client can tell "change your password" apart from "ask your school for this
 * permission", which need completely different screens.
 *
 * <p>Neither refusal is audited as a permission denial. ADR-0018 gets one {@code PERMISSION_DENIED}
 * row per denial from exactly two producers, and a third that fires on every request a stuck client
 * retries would make that count wrong — an audit log that counts wrong is one nobody trusts. Both
 * are logged at {@code WARN} with the account id, which is not personal data; the username is, and
 * never appears here (ADR-0014).
 */
public class SessionStandingFilter extends OncePerRequestFilter implements AuthenticatedApiFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionStandingFilter.class);

    /** Everything this filter has an opinion about. Nothing outside {@code /api} is its business. */
    private static final RequestMatcher API =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    /**
     * The three endpoints a session that owes a password change may still reach, plus login.
     *
     * <p>Matched on method as well as path, so {@code GET /api/auth/password} is not a way in. The
     * same matcher type Spring Security uses for its own rules, for the reason in the class
     * javadoc. Not consulted at all for a disabled or locked account — that session is invalidated
     * outright rather than narrowed.
     */
    private static final RequestMatcher ALLOWED_WHILE_PASSWORD_OWED = new OrRequestMatcher(
            matcher(HttpMethod.POST, "/api/auth/password"),
            matcher(HttpMethod.POST, "/api/auth/logout"),
            matcher(HttpMethod.POST, "/api/auth/login"),
            matcher(HttpMethod.GET, "/api/me"));

    private final UserAccountService users;
    private final AuditService audit;
    private final JsonMapper jsonMapper;

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    SessionStandingFilter(UserAccountService users, AuditService audit, JsonMapper jsonMapper) {
        this.users = users;
        this.audit = audit;
        this.jsonMapper = jsonMapper;
    }

    private static RequestMatcher matcher(HttpMethod method, String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !API.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            // Anonymous, or a principal this application did not put there. Not this filter's
            // refusal to make: the chain has its own answer for that, and it has already given it.
            chain.doFilter(request, response);
            return;
        }

        AccountStanding standing = readStanding(user);

        if (!standing.isUsable(Instant.now())) {
            revokeSession(request, user);
            log.warn(
                    "Ended the session for account {}: the account is {}{}",
                    user.userId(),
                    standing.status(),
                    standing.lockedUntil() != null ? " and locked until " + standing.lockedUntil() : "");
            writeError(response, PlatformErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }

        if (standing.mustChangePassword() && !ALLOWED_WHILE_PASSWORD_OWED.matches(request)) {
            // The path, not the query string: `?q=Aarav%20Sharma` would put a child's name in a log
            // line, and a name is Confidential under ADR-0014.
            log.warn(
                    "Refused {} {} for account {}: the school-issued password has not been changed",
                    request.getMethod(),
                    request.getRequestURI(),
                    user.userId());
            writeError(response, IdentityErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * The account's own answer, read inside its tenant.
     *
     * <p>The schema comes off the principal rather than off the session attribute. It is the same
     * value — login writes both from the same {@code SchoolRef} — but taking it from the
     * authentication this filter has just read means there is only one thing here deciding which
     * school a request belongs to. {@code SessionTenantFilter} binds the tenant for the rest of the
     * request, but it is ordered <em>after</em> the whole security chain, so nothing is bound yet at
     * this point and the lookup has to bind its own.
     */
    private AccountStanding readStanding(AuthenticatedUser user) {
        try {
            return TenantContext.callWith(user.schema(), () -> users.standing(user.userId()));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Tenant-scoped work failed for schema " + user.schema(), ex);
        }
    }

    /**
     * Invalidates the session that just failed re-validation, and records exactly one
     * {@link AuditAction#SESSION_REVOKED} for it.
     *
     * <p>Invalidating here — not merely refusing — is what stops the same cookie from arriving on
     * the next request and being refused all over again: this fires once, on discovery, rather than
     * once per retry.
     */
    private void revokeSession(HttpServletRequest request, AuthenticatedUser user) {
        // Read before the context is cleared, or there would be nobody left to attribute the
        // revocation to — the same reason AuthenticationService#logout captures its actor first.
        AuditActor actor = new AuditActor(user.userId(), user.displayName(), user.rolesSnapshot(), user.schema());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();
        try {
            TenantContext.callWith(user.schema(), () -> {
                audit.recordSecurityEvent(
                        AuditAction.SESSION_REVOKED,
                        AuditOutcome.DENIED,
                        "USER_ACCOUNT",
                        user.userId().toString(),
                        actor);
                return null;
            });
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Tenant-scoped work failed for schema " + user.schema(), ex);
        }
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication =
                securityContextHolderStrategy.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }

    /** The application's ordinary error envelope, written here because no controller is reached. */
    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(jsonMapper.writeValueAsString(
                        ApiResponse.error(ApiError.of(code.code(), code.defaultMessage()))));
    }
}
