package in.chalkbase.platform.error;

import java.util.Map;

/**
 * Base class for every deliberate application failure.
 *
 * <p>Throw this (or a subclass) rather than {@code IllegalArgumentException} for business failures.
 * Mapping {@code IllegalArgumentException} to 400 wholesale is tempting and wrong: the JDK and its
 * libraries throw it for genuine programming mistakes — a null passed to {@code
 * Objects.requireNonNull}, an unknown constant in {@code Enum.valueOf} — and those would then be
 * reported to the client as "bad request" and never investigated as the bugs they are.
 */
public class ChalkbaseException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient Map<String, String> details;

    public ChalkbaseException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public ChalkbaseException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public ChalkbaseException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Structured context the client can act on — which field, which limit. Never internals. */
    public Map<String, String> getDetails() {
        return details;
    }
}
