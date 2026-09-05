package in.chalkbase.platform.error;

import org.springframework.http.HttpStatus;

/** Cross-cutting error codes. Module-specific failures belong in that module's own enum. */
public enum PlatformErrorCode implements ErrorCode {
    VALIDATION_FAILED("VAL_001", "Some of the information provided is not valid", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("VAL_002", "The request could not be read", HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE("VAL_003", "The uploaded file is too large", HttpStatus.PAYLOAD_TOO_LARGE),
    METHOD_NOT_ALLOWED("VAL_004", "That action is not supported on this address", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("VAL_005", "That content type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    UNAUTHENTICATED("AUTH_001", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    AUTHENTICATION_REQUIRED("AUTH_002", "Please sign in to continue", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("PERM_001", "You do not have permission to do that", HttpStatus.FORBIDDEN),

    NOT_FOUND("NF_001", "Not found", HttpStatus.NOT_FOUND),
    NO_SUCH_ENDPOINT("NF_002", "No such endpoint", HttpStatus.NOT_FOUND),

    CONFLICT("CONF_001", "That conflicts with information already saved", HttpStatus.CONFLICT),
    CONCURRENT_UPDATE(
            "CONF_002", "Someone else changed this while you were editing. Reload and try again.", HttpStatus.CONFLICT),

    INTERNAL(
            "GEN_001",
            "Something went wrong at our end. Quote the trace id when reporting it.",
            HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    PlatformErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
