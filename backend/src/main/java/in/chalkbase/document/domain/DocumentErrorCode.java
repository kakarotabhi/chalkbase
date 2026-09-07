package in.chalkbase.document.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the document module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum DocumentErrorCode implements ErrorCode {
    /** Neither PDF, JPEG nor PNG by its first bytes — see {@code DocumentService.SniffedType}. */
    UNSUPPORTED_FILE_TYPE(
            "DOC_001", "That file is not a PDF, JPEG or PNG. Upload one of those.", HttpStatus.BAD_REQUEST),

    FILE_EMPTY("DOC_002", "That file is empty", HttpStatus.BAD_REQUEST),

    /** The upload could not be read to the end — a connection that dropped mid-request. */
    FILE_UNREADABLE("DOC_003", "That file could not be read", HttpStatus.BAD_REQUEST),

    /**
     * {@code fk_document_student}. 422 rather than 404, for the same reason
     * {@code StudentErrorCode.UNKNOWN_ACADEMIC_SESSION} is: this endpoint's own path or body names
     * no other resource that might not exist, so there is nothing to tell apart from "the student
     * named in the request is not this school's" — but 404 on a create is still the wrong signal to
     * a client that just listed this school's own students moments before.
     */
    UNKNOWN_STUDENT("DOC_004", "That student does not belong to this school", HttpStatus.UNPROCESSABLE_ENTITY),

    /** {@code ck_document_dates}, and checked before it too — see {@code DocumentService}. */
    EXPIRY_BEFORE_ISSUE("DOC_005", "The expiry date must be after the issue date", HttpStatus.BAD_REQUEST),

    /**
     * A delete removed the bytes but something kept the row alive — a concurrent modification, a
     * crash between the two steps ADR-0025 describes. Surfaced rather than served as an empty body,
     * so it reads as the inconsistency it is rather than as a document with nothing in it.
     */
    CONTENT_MISSING(
            "DOC_006",
            "This document's file could not be found. Quote the trace id when reporting it.",
            HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * {@code ck_document_type}. Unreachable through the API — the request DTOs bind to the enum, so
     * an unknown value fails as a parameter type mismatch before this module sees it — and claimed
     * anyway, for what the violation is called when something writes without going through the API.
     */
    INVALID_DOCUMENT_TYPE("DOC_007", "That is not a document type this build knows", HttpStatus.BAD_REQUEST),

    /** {@code ck_document_verification_status}, unreachable the same way and claimed for the same reason. */
    INVALID_VERIFICATION_STATUS(
            "DOC_008", "That is not a verification status this record can hold", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    DocumentErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
