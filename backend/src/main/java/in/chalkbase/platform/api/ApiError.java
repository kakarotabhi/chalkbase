package in.chalkbase.platform.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * The error half of an {@link ApiResponse}.
 *
 * @param code a stable, machine-readable identifier such as {@code VAL_001}. Clients branch on
 *     this, never on {@code message}, so it is part of the API contract.
 * @param message a human-readable sentence safe to show a user. Never contains a stack trace, SQL,
 *     or anything from the request body.
 * @param details field-level detail, typically field name to failure reason. Omitted when empty.
 */
public record ApiError(
        @Classification(Tier.INTERNAL) String code,
        @Classification(Tier.INTERNAL) String message,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        Map<String, String> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError withDetails(String code, String message, Map<String, String> details) {
        return new ApiError(code, message, details == null || details.isEmpty() ? null : Map.copyOf(details));
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
