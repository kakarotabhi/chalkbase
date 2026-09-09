package in.chalkbase.fee.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the fee module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum FeeErrorCode implements ErrorCode {
    DUPLICATE_FEE_HEAD_NAME("FEE_001", "A fee head with this name already exists", HttpStatus.CONFLICT),

    DUPLICATE_CONCESSION_TYPE_NAME("FEE_002", "A concession type with this name already exists", HttpStatus.CONFLICT),

    /** A cap percentage was set on a head whose category is not {@link FeeHeadCategory#ANNUAL_DEVELOPMENT}. */
    CAP_PERCENT_NOT_APPLICABLE(
            "FEE_003", "A cap on tuition only applies to an annual/development fee head", HttpStatus.BAD_REQUEST),

    /**
     * A structure's Development Fee items exceed the cap the school itself set on that head
     * (07-phase-0-decisions.md §2). Reachable only when the head actually carries a cap — a head
     * with none is never checked against one.
     */
    DEVELOPMENT_FEE_EXCEEDS_CAP(
            "FEE_004",
            "The development fee in this structure exceeds the cap set on that fee head",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /** The same fee head named twice in one structure save. One row per head per version (FEE_005). */
    DUPLICATE_HEAD_IN_STRUCTURE(
            "FEE_005", "The same fee head cannot appear twice in one fee structure", HttpStatus.UNPROCESSABLE_ENTITY),

    /** An item's installments do not add up to that item's own amount. */
    INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT(
            "FEE_006", "An item's installments must add up to that item's own amount", HttpStatus.UNPROCESSABLE_ENTITY),

    /** Two installments of the same item sharing one due date. */
    DUPLICATE_INSTALLMENT_DATE(
            "FEE_007", "An item cannot have two installments due on the same date", HttpStatus.UNPROCESSABLE_ENTITY),

    /** An installment due before the academic session it belongs to has even started. */
    INSTALLMENT_BEFORE_SESSION_START(
            "FEE_008",
            "An installment cannot be due before its academic session starts",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * A new version was refused for a class's fee structure because its academic session has
     * already run its course — see {@link in.chalkbase.fee.application.FeeStructureService} for
     * exactly what "already run its course" means and why.
     */
    STRUCTURE_SESSION_CLOSED(
            "FEE_009",
            "This academic session has already run; its fee structure can no longer be changed",
            HttpStatus.CONFLICT),

    /** A structure item named a fee head that has been deactivated. */
    INACTIVE_FEE_HEAD(
            "FEE_010", "A deactivated fee head cannot be added to a fee structure", HttpStatus.UNPROCESSABLE_ENTITY),

    /** {@code copy-from-previous} was asked to copy a session into itself. */
    CANNOT_COPY_SESSION_INTO_ITSELF(
            "FEE_011", "Choose a different session to copy the fee structure from", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    FeeErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
