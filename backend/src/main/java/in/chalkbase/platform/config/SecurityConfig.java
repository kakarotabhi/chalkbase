package in.chalkbase.platform.config;

import in.chalkbase.platform.error.SecurityErrorResponder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * HTTP security for a cookie-session API.
 *
 * <p>Authentication itself is not done by a Spring Security filter. Identity authenticates inside
 * {@code AuthController} (it needs a school code as well as a username, and it has to bind a tenant
 * to find the user at all — ADR-0017) and writes the resulting {@code SecurityContext} into the
 * session through {@link #securityContextRepository()}. Everything after that is ordinary Spring
 * Security: the context is restored from the session on the next request, and the rules below
 * decide what it may reach.
 *
 * <p>The 401 and 403 responders are wired here so those two responses use the same envelope as
 * every other error, even though they are produced inside the filter chain rather than by a
 * controller.
 *
 * <p>{@code @EnableMethodSecurity} is what makes {@code @PreAuthorize("hasAuthority('...')")} on a
 * controller method mean anything. The rules below decide only whether a request may reach the
 * application at all; <strong>which</strong> action it may perform is decided per method, against
 * the effective permissions resolved once at login and carried as the authorities of the
 * authentication (ADR-0005). The two are not interchangeable: a URL pattern cannot express
 * "create an invoice", and a school that renames an endpoint must not silently widen access.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            SecurityErrorResponder securityErrors,
            ObjectProvider<SetupKeyFilter> setupKey,
            ObjectProvider<AuthenticatedApiFilter> moduleFilters)
            throws Exception {
        // Present only on the prod profile (SetupKeyConfiguration). Added here, inside the chain,
        // rather than as a servlet filter in front of it: FilterChainProxy has already run
        // StrictHttpFirewall and parsed the request path by this point, so the filter's
        // /api/schools/** matcher and the permitAll() rule below cannot disagree about which
        // requests they are talking about. Placed ahead of CsrfFilter so a request with no key is
        // refused before any session or CSRF work is done for it.
        setupKey.ifAvailable(filter -> http.addFilterBefore(filter, CsrfFilter.class));

        // Rules a feature module contributes about whether a session may be used at all, rather
        // than about what it may reach. They go after AuthorizationFilter — the last filter in the
        // chain — so the security context is restored, CSRF is settled and an anonymous or
        // URL-denied request has already been answered before any of them runs. See
        // AuthenticatedApiFilter for why the position, and the interface, are not negotiable.
        moduleFilters.orderedStream().forEach(filter -> http.addFilterAfter(filter, AuthorizationFilter.class));

        // The plain (non-XOR) handler is what lets a browser read the XSRF-TOKEN cookie and echo it
        // back verbatim as X-XSRF-TOKEN. Setting the request attribute name to null opts out of
        // deferred loading, so the cookie is issued on every response rather than only once
        // something has asked for the token.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.httpOnly(false));

        return http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // Login is the one state-changing endpoint that cannot require a token: a
                        // browser that has never called the API has no cookie to echo. It is also
                        // the one endpoint where forgery buys an attacker nothing but signing a
                        // victim into an account the attacker already controls, and it has its own
                        // lockout. Every other POST/PUT/DELETE is protected.
                        // Onboarding is exempt, and the setup key does not change that. CSRF
                        // exists to stop a malicious site spending a victim's ambient cookie; an
                        // endpoint that reads no cookie has no ambient authority to spend, so the
                        // token would be friction with no security behind it — and it breaks every
                        // non-browser caller, which is what an operator onboarding a school
                        // actually uses. The prod-only X-Chalkbase-Setup-Key is not ambient either:
                        // a browser never attaches a custom header on its own, and a cross-origin
                        // request that sets one is preflighted. The exemption stays correct for the
                        // same reason it always was — there is nothing here a victim's browser can
                        // be made to spend.
                        // TODO(identity): remove this exemption in the same change that makes
                        // /api/schools/** authenticated.
                        .ignoringRequestMatchers("/api/auth/login", "/api/schools/**"))
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(auth ->
                        // Signing in has no session yet, and signing out must work even when the
                        // session has already expired — a 401 from logout leaves a client unable to
                        // do the one thing it wanted. Both still require a CSRF token except login,
                        // which has no cookie to echo.
                        auth.requestMatchers("/api/auth/login", "/api/auth/logout")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                .permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll()
                                // TODO(identity): onboarding a school is a platform-operator action and has
                                // no principal to authenticate until the authorization model of ADR-0005
                                // lands. Left open so onboarding still works; close it in the same change
                                // that introduces platform-operator accounts.
                                // permitAll() is the truth on local and test. On prod it is not the whole
                                // truth: SetupKeyFilter, added above, has already turned away anything
                                // without a matching X-Chalkbase-Setup-Key by the time a request reaches
                                // this rule. That is a stopgap for the deployed environment having a
                                // public URL, not a substitute for the operator account — a single shared
                                // secret names nobody, expires never, and cannot be audited.
                                .requestMatchers("/api/schools/**")
                                .permitAll()
                                .requestMatchers("/api/**")
                                .authenticated()
                                // Everything else under /api needs a session, and then a permission: the
                                // method-level @PreAuthorize decides the rest. An endpoint that carries no
                                // annotation is caught by ControllerAuthorizationTests, not by review.
                                // Static resources, the error dispatch and the OpenAPI assets. Nothing
                                // under /api reaches this line.
                                .anyRequest()
                                .permitAll())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(securityErrors).accessDeniedHandler(securityErrors))
                .build();
    }

    /**
     * Where an authenticated {@code SecurityContext} is kept between requests.
     *
     * <p>The session half is backed by Spring Session JDBC, so the context lives in
     * {@code public.spring_session_attributes} and a restart does not sign every parent out
     * (ADR-0003). Exposed as a bean because identity has to save into the same place the filter
     * chain later reads from.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }
}
