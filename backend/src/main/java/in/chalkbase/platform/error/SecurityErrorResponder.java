package in.chalkbase.platform.error;

import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Produces the {@link ApiResponse} envelope for failures raised inside the security filter chain.
 *
 * <p>This closes a gap that is easy to miss. {@code @RestControllerAdvice} only sees exceptions
 * thrown once a request has reached a controller. An unauthenticated request is rejected earlier,
 * by the filter chain, so without this the API returns Spring's default error body for exactly the
 * two responses a client most needs to parse — 401 and 403 — while every other error uses the
 * envelope.
 *
 * <p><strong>It is also one of the two places a permission denial is audited</strong> (ADR-0018).
 * A 403 has exactly two producers in this application: this handler, for a denial raised inside the
 * security filter chain before a controller is reached — a URL rule, a missing CSRF token — and
 * {@link GlobalExceptionHandler}, for one raised by a method-level {@code @PreAuthorize} after it
 * has been. Only one of the two runs for any given request, which is what makes
 * {@code AuditService#recordPermissionDenied} record one row per denial. Auditing inside a filter
 * or an authorization manager instead would count wrong, because the chain re-evaluates
 * authorization more than once per request, and an audit log that counts wrong is one nobody
 * trusts.
 *
 * <p>A 401 is not audited. An unauthenticated request is a request with no session, no school and
 * no actor — there is no school's audit log for it to belong to (ADR-0018 §5), and recording every
 * expired cookie and every unauthenticated probe of the API would drown the events that matter. A
 * failed <em>sign-in</em>, which does name a school, is audited by {@code AuthenticationService}.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    private final JsonMapper jsonMapper;
    private final AuditService audit;

    public SecurityErrorResponder(JsonMapper jsonMapper, AuditService audit) {
        this.jsonMapper = jsonMapper;
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.warn("Unauthenticated request to {}", request.getRequestURI());
        write(response, PlatformErrorCode.AUTHENTICATION_REQUIRED);
    }

    /**
     * The 403, and the audit row that goes with it.
     *
     * <p>The tenant is deliberately not read from {@code TenantContext} here, because by this point
     * there is none: {@code SessionTenantFilter} runs downstream of the security chain and has
     * already unbound the schema in its {@code finally} as the {@code AccessDeniedException}
     * travelled back up. The school comes off the actor instead, which is why
     * {@code platform.audit.AuditActor} carries it. A denial with no principal at all — a missing
     * CSRF token on an anonymous request — names no school and is not recorded.
     *
     * <p>{@link AuditService#recordSecurityEvent} never throws, so a failure to audit cannot turn
     * this 403 into a broken response or a 500.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        log.warn("Access denied for {}", request.getRequestURI());
        audit.recordPermissionDenied();
        write(response, PlatformErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletResponse response, PlatformErrorCode code) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(jsonMapper.writeValueAsString(
                        ApiResponse.error(ApiError.of(code.code(), code.defaultMessage()))));
    }
}
