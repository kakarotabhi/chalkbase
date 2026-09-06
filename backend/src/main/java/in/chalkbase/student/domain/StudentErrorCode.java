package in.chalkbase.student.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Failures specific to the student module. Cross-cutting ones live in {@code PlatformErrorCode}.
 *
 * <p><strong>Not one of these messages contains a name, an admission number, a date of birth or a
 * phone number</strong>, and none ever may. ADR-0014 puts every field this module handles in the
 * Confidential tier, and an error message is a response body, a log line and usually a screenshot in
 * a support ticket. "A student with this admission number already exists" says what the school needs
 * to know; naming the number would put a child's identifier in all three places.
 */
public enum StudentErrorCode implements ErrorCode {

    /**
     * Unique within this school, which is the only scope a schema can enforce (ADR-0020 §3). Two
     * campuses of one trust may each hold 2026/0148 and that is not a clash — under ADR-0011 they
     * are different databases as far as a constraint is concerned.
     */
    DUPLICATE_ADMISSION_NUMBER(
            "STU_001", "A student with this admission number already exists at this school", HttpStatus.CONFLICT),

    /**
     * The refusal {@code uq_student_enrolment_one_active} exists for.
     *
     * <p>A student is in one section at a time in any given year. Reaching this usually means the
     * previous placement was never ended — the fix is to end it, which the enrolment update does,
     * and not to allow a child to be in two classes at once.
     */
    ALREADY_ENROLLED_THIS_SESSION(
            "STU_002", "This student already has an active enrolment for that academic year", HttpStatus.CONFLICT),

    /** {@code uq_student_enrolment_roll}. Nullable roll numbers do not collide; two 14s in one section do. */
    ROLL_NUMBER_TAKEN("STU_003", "Another student in that section already has this roll number", HttpStatus.CONFLICT),

    /**
     * {@code uq_student_guardian_pair}. The same person twice on one child, which is almost always
     * an operator adding "father" a second time rather than editing the first.
     */
    GUARDIAN_ALREADY_LINKED("STU_004", "This guardian is already linked to this student", HttpStatus.CONFLICT),

    /**
     * {@code uq_student_guardian_one_primary} — a partial unique index, which cannot be deferred.
     *
     * <p>Reaching this through the API should be impossible: making a link primary clears the
     * previous one first and flushes that clear, in the same transaction. This is what the violation
     * is called when something writes without going through the service.
     */
    PRIMARY_GUARDIAN_ALREADY_SET("STU_005", "This student already has a primary guardian", HttpStatus.CONFLICT),

    /**
     * Deliberately weaker than the request DTO, which rejects the same thing as a named field error.
     * {@code ck_student_gender} and {@code ck_student_status} say it at the table, which is what the
     * violation is called when something writes without going through the API.
     */
    INVALID_STUDENT_GENDER("STU_006", "That is not a gender this record can hold", HttpStatus.BAD_REQUEST),

    INVALID_STUDENT_STATUS("STU_007", "That is not a status this record can hold", HttpStatus.BAD_REQUEST),

    /** {@code ck_student_guardian_relation}, the same way round as the two above. */
    INVALID_GUARDIAN_RELATION("STU_008", "That is not a relationship this link can hold", HttpStatus.BAD_REQUEST),

    /**
     * An academic session id in a request body that is not this school's.
     *
     * <p>422 rather than 404, and the distinction is not pedantry: the student named in the path
     * <em>does</em> exist, so answering 404 would tell a client its screen was showing a child who
     * had been removed — and a screen that reacts by clearing itself would look, to the office, like
     * a student had just vanished. The body is what was wrong, and 422 says so.
     */
    UNKNOWN_ACADEMIC_SESSION(
            "STU_009", "That academic year does not belong to this school", HttpStatus.UNPROCESSABLE_ENTITY),

    /** A section id in a request body that is not this school's. 422 for the reason above. */
    UNKNOWN_SECTION("STU_010", "That section does not belong to this school", HttpStatus.UNPROCESSABLE_ENTITY),

    /** A guardian id in a request body that is not this school's. 422 for the reason above. */
    UNKNOWN_GUARDIAN("STU_011", "That guardian does not belong to this school", HttpStatus.UNPROCESSABLE_ENTITY),

    // ── Bulk import (ADR-0021) ───────────────────────────────────────────────────────────────
    //
    // These are the failures of the FILE, not of a row in it. A row's problems are ImportError
    // entries inside the report — a school fixing one error per upload would give up, so per-row
    // trouble is never an error code and never stops the parse (ADR-0021 §1).
    //
    // Not one of these messages quotes anything out of the file. The file is several hundred
    // children's names and dates of birth (ADR-0014), and an error message is a response body, a log
    // line and a screenshot in a support ticket.

    /** Nothing to import: an empty upload, or a file holding only blank lines. */
    IMPORT_FILE_EMPTY("STU_012", "That file has no rows in it", HttpStatus.BAD_REQUEST),

    /**
     * The first row does not name the columns the import needs — the commonest failure there is.
     *
     * <p>The details map names every column that is missing, and every column in the file that this
     * import does not recognise, so that a school which wrote {@code dob} sees {@code dob} in the
     * answer rather than being told only that {@code date_of_birth} is absent. Column names are the
     * import's own vocabulary plus the school's spelling of it — neither is anybody's personal data,
     * which is why these may be said out loud when a cell value may not.
     *
     * <p>A file with no header row at all arrives here too, because a first row of student data
     * names none of the columns.
     */
    IMPORT_COLUMNS_MISSING(
            "STU_013", "The first row of that file does not name the columns the import needs", HttpStatus.BAD_REQUEST),

    /**
     * One column name appears twice in the header.
     *
     * <p>Refused rather than resolved by taking the first or the last, because the two columns will
     * disagree in some row and either choice silently imports the wrong half of the file.
     */
    IMPORT_COLUMN_REPEATED(
            "STU_014", "That file names the same column more than once in its first row", HttpStatus.BAD_REQUEST),

    /**
     * More rows than one import may carry (ADR-0021 §6). 2,000 is above any single Indian school's
     * intake and low enough that a mistaken upload cannot exhaust memory.
     *
     * <p>The cap itself is in the details map, under {@code maxRows}, so the screen saying "split
     * the file" names the number this build actually enforces rather than a constant copied into the
     * frontend that will drift the first time the cap moves.
     */
    IMPORT_TOO_MANY_ROWS(
            "STU_015", "That file has more rows than one import may carry", HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * A workbook rather than a CSV — the commonest way this will be got wrong, and worth its own
     * sentence.
     *
     * <p>Reading {@code .xlsx} directly needs Apache POI, which ADR-0021 §5 leaves open pending the
     * product owner. Until then the answer a school office needs is "Save As, CSV", not "the header
     * row is wrong" — which is what the header check would otherwise tell them about a zip file.
     */
    IMPORT_NOT_CSV(
            "STU_016",
            "That looks like an Excel workbook rather than a CSV file. In Excel, choose"
                    + " File \u2192 Save As and pick CSV, then upload that file.",
            HttpStatus.BAD_REQUEST),

    /** The upload could not be read to the end — a connection that dropped mid-request. */
    IMPORT_FILE_UNREADABLE("STU_017", "That file could not be read", HttpStatus.BAD_REQUEST),

    /**
     * The school has no class ladder to import into, which is not a fault of the file.
     *
     * <p>A school that uploads its roll before setting up its classes is an ordinary first-day
     * mistake, and answering it with six hundred identical row errors saying "no class called Class
     * 5" describes the symptom rather than the cause. The fix is one screen away and this says so.
     *
     * <p>422 rather than 400: the request was well formed and the file may be perfect. It is the
     * school that is not ready yet, which is exactly what ADR-0021 means by "a school must set up
     * its academic session and class ladder before importing".
     */
    IMPORT_NO_CLASS_LADDER(
            "STU_018",
            "This school has no classes and sections set up yet, so there is nothing to import"
                    + " students into. Set up the class ladder under Academics first.",
            HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    StudentErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
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
