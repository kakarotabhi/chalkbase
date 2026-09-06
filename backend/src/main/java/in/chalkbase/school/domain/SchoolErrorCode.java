package in.chalkbase.school.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the school module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum SchoolErrorCode implements ErrorCode {
    DUPLICATE_CODE("SCHOOL_001", "A school with this code already exists", HttpStatus.CONFLICT),

    /**
     * An update tried to change the school code or the schema name. Both address the tenant — the
     * code is what a user types on the sign-in form, the schema name is where every row this school
     * owns lives — so neither is editable once the schema exists (ADR-0011). Rejected rather than
     * ignored: silently dropping a field a client sent teaches the client that it worked.
     */
    IMMUTABLE_IDENTITY(
            "SCHOOL_002", "The school code and schema name cannot be changed", HttpStatus.UNPROCESSABLE_ENTITY),

    /** A second profile row for one school. The schema forbids it; this is what a user is told. */
    PROFILE_ALREADY_EXISTS("SCHOOL_003", "This school already has a profile", HttpStatus.CONFLICT),

    INVALID_PINCODE("SCHOOL_004", "A PIN code is six digits and does not start with a zero", HttpStatus.BAD_REQUEST),

    INVALID_CONTACT("SCHOOL_005", "That phone number, e-mail address or website is not usable", HttpStatus.BAD_REQUEST),

    UNKNOWN_BOARD("SCHOOL_006", "That is not a board Chalkbase knows", HttpStatus.BAD_REQUEST);

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
