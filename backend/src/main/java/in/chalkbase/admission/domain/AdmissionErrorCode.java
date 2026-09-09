package in.chalkbase.admission.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the admission module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum AdmissionErrorCode implements ErrorCode {

    /**
     * A follow-up was logged against an enquiry already {@link EnquiryStatus#CONVERTED} or
     * {@link EnquiryStatus#LOST}. Both are terminal on purpose (see {@link Enquiry#applyFollowUp}):
     * once a family has gone ahead or dropped out there is nothing left to follow up, and a screen
     * offering to log one anyway would be free to reopen a closed enquiry by accident.
     */
    ENQUIRY_CLOSED(
            "ADM_001",
            "This enquiry has already converted or been marked lost; no further follow-up can be logged",
            HttpStatus.CONFLICT),

    /**
     * An enquiry was assigned, at creation or by reassignment, to an account that does not exist in
     * this school or is not {@code AccountStatus.ACTIVE}. Reachable when a counsellor's account was
     * disabled between the assignment picker loading and the save.
     */
    COUNSELLOR_NOT_ACTIVE(
            "ADM_002",
            "That account is not an active user of this school and cannot be assigned an enquiry",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * A follow-up tried to set {@link EnquiryStatus#NEW} as its resulting status. {@code NEW} is
     * only ever the status a freshly captured enquiry starts at; a follow-up can move an enquiry
     * forward or close it, never back to "nobody has looked at this yet".
     */
    STATUS_CANNOT_REOPEN_TO_NEW(
            "ADM_003", "An enquiry cannot be set back to New by logging a follow-up", HttpStatus.BAD_REQUEST),

    /**
     * A follow-up left an enquiry open ({@link EnquiryStatus#NEW} or {@link EnquiryStatus#IN_PROGRESS})
     * with no {@code nextFollowUpDate}. This is the rule that keeps the due-date queue honest: an
     * open enquiry with no next follow-up date scheduled would still exist, but would never surface
     * on anyone's queue again — a mailbox by another name. Closing an enquiry ({@code CONVERTED} or
     * {@code LOST}) is the only way to log a follow-up with no next date.
     */
    NEXT_FOLLOW_UP_DATE_REQUIRED(
            "ADM_004", "An enquiry that stays open needs its next follow-up date set", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    AdmissionErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
