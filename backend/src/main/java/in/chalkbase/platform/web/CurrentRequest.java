package in.chalkbase.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's address and client, when there is a caller.
 *
 * <p>Sits beside {@link RequestId} and works the same way: ambient, static, and safe to ask from
 * anywhere. That matters because the alternative — injecting {@code HttpServletRequest} into
 * {@code platform.audit.AuditService} — would make every audit call require a servlet request to
 * exist, and a scheduled retention job or a startup task has none. Both methods return null outside
 * a request rather than throwing, so "no request" is data, not an error path.
 *
 * <p>The values are recorded because FR-008's acceptance note asks for "IP/device where available",
 * and because an IP is what makes an intrusion investigable. An IP is itself personal data under
 * the DPDP Act; it inherits the audit log's retention rather than being kept forever.
 */
public final class CurrentRequest {

    /** Matches {@code audit_event.ip_address} — long enough for a full IPv6 address. */
    private static final int MAX_IP_LENGTH = 45;

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private CurrentRequest() {}

    /**
     * The caller's address, or null outside a request.
     *
     * <p>Reads {@code X-Forwarded-For} first because the deployment baseline (ADR-0015) puts a
     * reverse proxy in front of this application, and without it every audit row would record the
     * proxy's own address and the field would be worthless. The header is only as trustworthy as
     * the proxy that sets it: it is attacker-supplied on a directly exposed deployment, so it is
     * treated as a hint for an investigator rather than as an authorization input — nothing in this
     * application makes a decision from it.
     */
    public static String ipAddress() {
        HttpServletRequest request = current();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // The left-most entry is the original client; the rest are the proxies it passed.
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, MAX_IP_LENGTH);
            }
        }
        return truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
    }

    /**
     * The method and path being served — {@code "GET /api/audit"} — or null outside a request.
     *
     * <p>The query string is deliberately left out. A path segment is an identifier and belongs in
     * an audit row; a query string carries values, and ADR-0014 keeps values out of the audit log.
     */
    public static String endpoint() {
        HttpServletRequest request = current();
        return request == null ? null : request.getMethod() + " " + request.getRequestURI();
    }

    /** The caller's {@code User-Agent}, or null outside a request. */
    public static String userAgent() {
        HttpServletRequest request = current();
        return request == null ? null : request.getHeader("User-Agent");
    }

    /**
     * The request bound to this thread, or null.
     *
     * <p>{@code RequestContextHolder} rather than a request-scoped bean: this is called from a
     * transaction boundary, and a scoped proxy would throw there instead of answering "no request".
     */
    private static HttpServletRequest current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
