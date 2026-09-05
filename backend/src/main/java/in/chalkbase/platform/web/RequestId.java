package in.chalkbase.platform.web;

import org.slf4j.MDC;

/** Access to the current request's trace id. Populated by {@link RequestIdFilter}. */
public final class RequestId {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Request-Id";

    private RequestId() {}

    /** The current request's trace id, or {@code null} outside a request (a scheduled job, a test). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
