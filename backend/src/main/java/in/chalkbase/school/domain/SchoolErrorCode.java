package in.chalkbase.school.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the school module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum SchoolErrorCode implements ErrorCode {
    DUPLICATE_CODE("SCHOOL_001", "A school with this code already exists", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    SchoolErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
