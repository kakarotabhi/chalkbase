package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.AuthenticatedUser;
import in.chalkbase.identity.application.UserAccountService;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.config.AuthenticatedApiFilter;
import in.chalkbase.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * Makes {@code must_change_password} mean something on the server.
 *
 * <p>A temporary password is <em>designed</em> to be widely transmitted: read out over the phone,
 * written on a slip handed across a counter, typed into an email. The flag is the whole reason that
 * is survivable — such a credential is meant to be <strong>replaceable but not usable</strong>.
 * Until this filter existed the flag was advice: it rode back on the login response, the Angular
 * app redirected to the change-password screen, and typing any other address escaped the redirect
 * entirely. {@code GET /api/students} with a session that owed a change returned 200 and sixty
 * children's names, and the flag stayed set, so nothing ever looked wrong to the school.
 *
 * <h2>What is left open</h2>
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
 * lives in the client's memory and is lost).
 *
 * <p>Everything else under {@code /api/**} is refused, including {@code /api/schools/**}, which is
 * {@code permitAll} for onboarding. An <em>anonymous</em> call there still works — there is no
 * principal, so this filter passes it through — but a signed-in session that owes a password change
 * has no business creating schemas.
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
 * <p>The refusal is <strong>not</strong> audited as a permission denial. ADR-0018 gets one
 * {@code PERMISSION_DENIED} row per denial from exactly two producers, and a third that fires on
 * every request a stuck client retries would make that count wrong — an audit log that counts wrong
 * is one nobody trusts. It is logged at {@code WARN} with the account id, which is not personal
 * data; the username is, and never appears here (ADR-0014).
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter implements AuthenticatedApiFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeRequiredFilter.class);

    /** Everything this filter has an opinion about. Nothing outside {@code /api} is its business. */
    private static final RequestMatcher API =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    /**
     * The three endpoints a session that owes a change may still reach, plus login.
     *
     * <p>Matched on method as well as path, so {@code GET /api/auth/password} is not a way in. The
     * same matcher type Spring Security uses for its own rules, for the reason in the class
     * javadoc.
     */
    private static final RequestMatcher ALLOWED = new OrRequestMatcher(
            matcher(HttpMethod.POST, "/api/auth/password"),
            matcher(HttpMethod.POST, "/api/auth/logout"),
            matcher(HttpMethod.POST, "/api/auth/login"),
            matcher(HttpMethod.GET, "/api/me"));

    private final UserAccountService users;
    private final JsonMapper jsonMapper;

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    PasswordChangeRequiredFilter(UserAccountService users, JsonMapper jsonMapper) {
        this.users = users;
        this.jsonMapper = jsonMapper;
    }

    private static RequestMatcher matcher(HttpMethod method, String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !API.matches(request) || ALLOWED.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticatedUser user = currentUser();
        if (user == null || !owesAPasswordChange(user)) {
            chain.doFilter(request, response);
            return;
        }
        // The path, not the query string: `?q=Aarav%20Sharma` would put a child's name in a log
        // line, and a name is Confidential under ADR-0014.
        log.warn(
                "Refused {} {} for account {}: the school-issued password has not been changed",
                request.getMethod(),
                request.getRequestURI(),
                user.userId());
        refuse(response);
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
    private boolean owesAPasswordChange(AuthenticatedUser user) {
        try {
            return TenantContext.callWith(user.schema(), () -> users.mustChangePassword(user.userId()));
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
            // Anonymous, or a principal this application did not put there. Not this filter's
            // refusal to make: the chain has its own answer for that, and it has already given it.
            return null;
        }
        return user;
    }

    /** The application's ordinary error envelope, written here because no controller is reached. */
    private void refuse(HttpServletResponse response) throws IOException {
        IdentityErrorCode code = IdentityErrorCode.PASSWORD_CHANGE_REQUIRED;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(jsonMapper.writeValueAsString(
                        ApiResponse.error(ApiError.of(code.code(), code.defaultMessage()))));
    }
}
