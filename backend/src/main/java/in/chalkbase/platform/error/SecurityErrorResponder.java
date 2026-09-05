package in.chalkbase.platform.error;

import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
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
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponder.class);

    private final JsonMapper jsonMapper;

    public SecurityErrorResponder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.warn("Unauthenticated request to {}", request.getRequestURI());
        write(response, PlatformErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        log.warn("Access denied for {}", request.getRequestURI());
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
