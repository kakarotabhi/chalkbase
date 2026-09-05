package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.SessionAttributes;
import in.chalkbase.platform.tenancy.SchemaName;
import in.chalkbase.platform.tenancy.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Binds the tenant for the rest of the request from the session, and unbinds it afterwards.
 *
 * <p><strong>Why this lives in {@code identity} and not in {@code platform.tenancy}.</strong> The
 * schema comes from the session, and ADR-0017 makes the session identity's to own. Putting the
 * filter next to {@link TenantContext} would mean the shared kernel reaching into a feature module
 * for the attribute name — the wrong direction, and something {@code ModularityTests} would be
 * right to reject. The mechanism (a thread-bound schema applied as {@code search_path}) stays in
 * {@code platform}; only the decision of which schema belongs here.
 *
 * <p><strong>Why it runs after Spring Security.</strong> Ordered later than the security filter
 * chain ({@code -100}) and than Spring Session's filter, so by the time it runs the session has
 * been resolved from the cookie and the security context restored. Running it earlier would mean
 * reading a session that does not exist yet. An unauthenticated request is rejected inside the
 * security chain and never reaches here, which is correct: it has no tenant.
 *
 * <p>The {@code finally} is not optional. Request threads are pooled — and with virtual threads,
 * carrier state is still inherited — so a schema left bound is the next request's tenant.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class SessionTenantFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionTenantFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean bound = false;
        if (request instanceof HttpServletRequest httpRequest) {
            bound = bindTenant(httpRequest);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (bound) {
                TenantContext.clear();
            }
        }
    }

    private boolean bindTenant(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object schema = session.getAttribute(SessionAttributes.SCHEMA);
        if (!(schema instanceof String name) || !SchemaName.isValid(name)) {
            // A session with no usable schema is a session that was never completed by a login.
            // Leave the tenant unbound rather than guessing; the request will fail on its own.
            return false;
        }
        TenantContext.set(name);
        log.debug("Bound tenant schema {} from session", name);
        return true;
    }
}
