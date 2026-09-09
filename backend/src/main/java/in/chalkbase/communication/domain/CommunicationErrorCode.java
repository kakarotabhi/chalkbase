package in.chalkbase.communication.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the communication module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum CommunicationErrorCode implements ErrorCode {

    /** A second publish was attempted on a circular that is already {@code PUBLISHED}. */
    CIRCULAR_ALREADY_PUBLISHED("COMM_001", "This circular has already been published", HttpStatus.CONFLICT),

    /**
     * Every target a circular names resolved to zero actively enrolled students — a class or
     * section with nobody in it, or one that no longer exists by the time publish runs. Publishing
     * anyway would create a circular with no recipients at all, which is indistinguishable from one
     * that silently failed.
     */
    CIRCULAR_NO_RECIPIENTS(
            "COMM_002",
            "None of the targeted classes or sections currently have any enrolled students",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * A target named a class this school does not teach, or a section that does not belong to the
     * class given alongside it.
     */
    INVALID_TARGET(
            "COMM_003",
            "A target must name a class this school teaches, and a section, if given, must belong to it",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * Two targets in the same request named the same whole class, or the same section. Maps
     * {@code uq_circular_target_whole_class} and {@code uq_circular_target_section}.
     */
    DUPLICATE_TARGET("COMM_004", "This class or section is already targeted by this circular", HttpStatus.CONFLICT),

    /**
     * An acknowledgement was recorded against a circular that was not composed to require one.
     * {@code requiresAcknowledgement} is set at composition time and never toggled afterwards, so
     * this is reachable only by naming a recipient of a circular that never asked for one.
     */
    ACKNOWLEDGEMENT_NOT_REQUIRED(
            "COMM_005", "This circular does not require an acknowledgement", HttpStatus.BAD_REQUEST),

    /** A second acknowledgement was recorded against a recipient who already has one. */
    ALREADY_ACKNOWLEDGED("COMM_006", "This recipient has already acknowledged this circular", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    CommunicationErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
