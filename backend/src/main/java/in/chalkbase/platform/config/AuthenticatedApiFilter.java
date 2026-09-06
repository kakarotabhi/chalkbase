package in.chalkbase.platform.config;

import jakarta.servlet.Filter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * A filter a feature module contributes to the API security chain, downstream of authorization.
 *
 * <p><strong>Why an interface in the shared kernel rather than a filter added directly.</strong>
 * {@link SecurityConfig} builds the one chain, and it lives in {@code platform}. A rule that needs
 * domain knowledge — what a session's account is allowed to be used for, say — belongs to the
 * module that owns that knowledge, and {@code platform} may not reach into a feature module to
 * name its filter class. So the platform owns the position in the chain and each module owns its
 * own rule, the same shape as {@code platform.security.PermissionProvider} and
 * {@code platform.error.ConstraintMappingProvider}.
 *
 * <p><strong>Why the chain and not the servlet container.</strong> A servlet-level filter sees the
 * raw request: it runs before {@code StrictHttpFirewall} and before the path is parsed, so its own
 * idea of which requests are "under {@code /api}" differs from Spring Security's over trailing
 * slashes, path parameters and encoding — and every such disagreement is a way past the filter into
 * an endpoint Security still believes is guarded. {@code SetupKeyFilter} carries the long-form
 * version of this argument. An implementation must therefore also register a disabled
 * {@code FilterRegistrationBean} for itself, or Boot will auto-register a second copy in front of
 * the chain, and {@code OncePerRequestFilter} would then let that copy be the only one that runs.
 *
 * <p><strong>Position.</strong> Added immediately after {@link AuthorizationFilter}, which is the
 * last filter in the chain. By that point the security context has been restored, CSRF has been
 * checked, and an anonymous or URL-denied request has already been answered — so an implementation
 * sees only requests that would otherwise have reached a controller, and never pays for a probe.
 * What it does not see is the method-level {@code @PreAuthorize}, which runs later, at the
 * controller: a filter here refuses <em>before</em> the permission check, so its answer is the one
 * the caller gets.
 *
 * <p>Implementations are added in {@code ObjectProvider#orderedStream} order, so {@code @Order} on
 * the bean decides the sequence when there is more than one.
 */
public interface AuthenticatedApiFilter extends Filter {}
