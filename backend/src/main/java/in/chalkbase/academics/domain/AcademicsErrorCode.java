package in.chalkbase.academics.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the academics module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum AcademicsErrorCode implements ErrorCode {
    DUPLICATE_SESSION_NAME("ACAD_001", "A session with this name already exists for this school", HttpStatus.CONFLICT),

    /**
     * Two sessions marked current at once. The partial unique index refuses it, and the only way to
     * reach this message is a write that did not go through
     * {@code AcademicSessionService#makeCurrent} — which clears the previous one first, in the same
     * transaction, precisely so a school is never in two years at the same time.
     */
    SESSION_ALREADY_CURRENT("ACAD_002", "This school already has a current session", HttpStatus.CONFLICT),

    /**
     * Deliberately weaker than the request DTO, which rejects the same thing as a named field
     * error. This is what the violation is called when something writes without going through the
     * API.
     */
    INVALID_SESSION_DATES("ACAD_003", "A session must end after it starts", HttpStatus.BAD_REQUEST),

    DUPLICATE_CLASS_NAME("ACAD_004", "A class with this name already exists", HttpStatus.CONFLICT),

    /**
     * Two classes claiming one rung of the ladder.
     *
     * <p>Reachable from two concurrent creates racing for {@code max(sequence) + 1}. Retrying is
     * the answer, and a conflict says so honestly; serialising every class creation to avoid a
     * collision a school hits once a decade would be the wrong trade.
     */
    CLASS_SEQUENCE_TAKEN("ACAD_005", "Another class already sits at that position in the ladder", HttpStatus.CONFLICT),

    DUPLICATE_SECTION_NAME("ACAD_006", "This class already has a section with that name", HttpStatus.CONFLICT),

    /**
     * A reorder that was not a permutation of this school's classes.
     *
     * <p>Refused rather than applied to whatever was sent. A client that silently dropped one class
     * would otherwise renumber the survivors and lose a rung off the bottom of the ladder, and
     * nothing about the result would look wrong until a student could not be enrolled into it.
     */
    INCOMPLETE_CLASS_ORDER(
            "ACAD_007",
            "The new order must list every class of this school exactly once",
            HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    AcademicsErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
