package in.chalkbase.attendance.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Failures specific to the attendance module. Cross-cutting ones live in {@code PlatformErrorCode}. */
public enum AttendanceErrorCode implements ErrorCode {

    /**
     * A student named in a mark is not actively enrolled in the section and session given.
     *
     * <p>Reachable when the roster a client is marking against has gone stale — a student was
     * moved to another section, or their enrolment ended, between the screen loading and the save.
     */
    STUDENT_NOT_ENROLLED_IN_SECTION(
            "ATT_001", "This student is not currently enrolled in that section", HttpStatus.UNPROCESSABLE_ENTITY),

    /** A mark was submitted for a date after today. Nobody may pre-mark a day that has not happened. */
    FUTURE_DATE_NOT_ALLOWED("ATT_002", "Attendance cannot be marked for a future date", HttpStatus.BAD_REQUEST),

    /**
     * A direct edit was attempted on a mark whose edit window has passed (end of the attendance day
     * plus 24 hours). The client should file a correction request instead.
     */
    MARK_LOCKED("ATT_003", "This attendance date is locked. File a correction request instead", HttpStatus.CONFLICT),

    /**
     * A correction request was filed for a mark that is still inside its own edit window — there is
     * nothing for a correction to do that a direct edit does not already do, and letting both paths
     * apply to the same mark would let a pending, unapproved request coexist with an ordinary edit.
     */
    CORRECTION_NOT_ALLOWED_YET(
            "ATT_004",
            "This mark can still be edited directly; a correction request is not needed yet",
            HttpStatus.BAD_REQUEST),

    /**
     * A second correction request was filed for a mark that already has one awaiting a decision.
     * Maps {@code uq_attendance_correction_one_pending}.
     */
    CORRECTION_ALREADY_PENDING(
            "ATT_005", "A correction request for this mark is already awaiting a decision", HttpStatus.CONFLICT),

    /**
     * An approval or rejection was attempted on a request that has already been decided.
     * {@link AttendanceCorrectionRequest#decide} is what throws this.
     */
    CORRECTION_NOT_PENDING("ATT_006", "This correction request has already been decided", HttpStatus.CONFLICT),

    /**
     * Two concurrent writes raced for the same student, date and (for the daily grain) no period —
     * or the same student, date and period. Maps {@code uq_attendance_mark_daily} and
     * {@code uq_attendance_mark_period}. The API's own upsert makes this vanishingly rare; it is
     * what a write outside the normal path collides with.
     */
    DUPLICATE_MARK("ATT_007", "This student already has a mark for that date", HttpStatus.CONFLICT),

    /**
     * Attendance was marked before this school has any academic session marked current. Reachable
     * only for a school that has not finished setting itself up — {@code academics:session:manage}
     * is what fixes it.
     */
    NO_CURRENT_SESSION("ATT_008", "This school has not set a current academic session yet", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    AttendanceErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
