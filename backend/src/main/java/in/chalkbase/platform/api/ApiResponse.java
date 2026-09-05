package in.chalkbase.platform.api;

import in.chalkbase.platform.web.RequestId;
import java.time.Instant;

/**
 * The single response shape for every {@code /api} endpoint.
 *
 * <p>Exactly one of {@code data} and {@code error} is present; the other is omitted, because
 * {@code spring.jackson.default-property-inclusion=non_null} drops nulls.
 *
 * <p>The HTTP status line stays truthful — a failure is never returned as 200 with
 * {@code success: false}. The envelope adds a stable error code and a trace id on top of the
 * status, it does not replace it. See ADR-0007.
 *
 * @param success whether the request succeeded, mirroring the HTTP status class
 * @param data the payload on success
 * @param error the failure on error
 * @param timestamp when the response was produced, ISO-8601 UTC
 * @param traceId identifier for this request, also returned as the {@code X-Request-Id} header.
 *     This is what a school quotes to support, and what finds the request in the logs.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), RequestId.current());
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now(), RequestId.current());
    }
}
