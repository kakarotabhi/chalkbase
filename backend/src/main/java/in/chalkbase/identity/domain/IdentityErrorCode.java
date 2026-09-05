package in.chalkbase.identity.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Failures specific to identity.
 *
 * <p>Note what is <strong>not</strong> here: a wrong password and an unknown username. Both return
 * the platform's {@code AUTH_001} with the same sentence, deliberately. Distinguishing them turns
 * the login form into a tool for discovering which parents are registered at a school.
 */
public enum IdentityErrorCode implements ErrorCode {
    ACCOUNT_LOCKED("AUTH_003", "This account is locked. Ask your school office to unlock it.", HttpStatus.UNAUTHORIZED),
    UNKNOWN_SCHOOL("AUTH_005", "No school with that code", HttpStatus.NOT_FOUND),
    CURRENT_PASSWORD_WRONG("AUTH_006", "The current password is not correct", HttpStatus.BAD_REQUEST),
    WEAK_PASSWORD(
            "AUTH_007",
            "The new password must be at least 10 characters and contain a digit and a symbol",
            HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    IdentityErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
