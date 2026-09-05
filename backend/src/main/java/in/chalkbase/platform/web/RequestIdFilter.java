package in.chalkbase.platform.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Gives every request a trace id, exposed on the response and on every log line for that request.
 *
 * <p>Runs first so anything that fails later — including inside the security filter chain — is
 * still traceable. An inbound {@code X-Request-Id} is honoured so a value set by a proxy or by the
 * frontend survives; otherwise one is generated.
 *
 * <p>Implements {@link Filter} directly rather than extending {@code OncePerRequestFilter}: this
 * needs no request-attribute bookkeeping, and staying off {@code GenericFilterBean} keeps it out of
 * that class's init lifecycle. Setting the same MDC value twice would be harmless anyway.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements Filter {

    private static final int MAX_LENGTH = 64;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = resolveTraceId(request);
        MDC.put(RequestId.MDC_KEY, traceId);
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(RequestId.HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }

    private String resolveTraceId(ServletRequest request) {
        if (request instanceof HttpServletRequest httpRequest) {
            String inbound = httpRequest.getHeader(RequestId.HEADER);
            if (StringUtils.hasText(inbound)) {
                // An inbound header is attacker-controlled: it lands in log lines, so strip
                // anything that could forge a line break or confuse a log parser, and cap it.
                String cleaned = inbound.replaceAll("[^A-Za-z0-9._-]", "");
                if (!cleaned.isEmpty()) {
                    return cleaned.substring(0, Math.min(cleaned.length(), MAX_LENGTH));
                }
            }
        }
        return UUID.randomUUID().toString();
    }
}
