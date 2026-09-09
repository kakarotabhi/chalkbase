package in.chalkbase.attendance.domain;

/**
 * What this module calls the things it writes to the audit log (ADR-0018).
 *
 * <p>{@link #ATTENDANCE_MARK} covers both the ordinary create/update a mark inside its edit window
 * gets, and {@link #CORRECTION_APPLIED} — the verb this module owns rather than a plain
 * {@code ENTITY_UPDATED} for a correction. Phase 0 decision 8 requires that "the original mark and
 * the correction both stay in the audit log": recording the original creation and the correction
 * as two entries against the same {@code entity_id}, rather than one entry that a naive
 * implementation might overwrite, is what that sentence means once ADR-0018 rules out recording
 * the actual before-and-after values. {@link AttendanceCorrectionRequest#getPreviousStatus()} is
 * where the value itself survives.
 */
public final class AttendanceAudit {

    public static final String ATTENDANCE_MARK = "ATTENDANCE_MARK";
    public static final String CORRECTION_REQUEST = "ATTENDANCE_CORRECTION_REQUEST";

    /**
     * A leave request (FR-047). Unlike {@link #CORRECTION_REQUEST}, deciding one never produces a
     * second audit entry against {@link #ATTENDANCE_MARK}: approving a leave request does not write
     * a mark (see the ADR-0030 amendment), so there is no second row for a decision to disturb. The
     * decision itself — {@code decision}, {@code decisionNote} — is still its own
     * {@code ENTITY_UPDATED} entry against this entity, field names only, the same as any other
     * change.
     */
    public static final String LEAVE_REQUEST = "ATTENDANCE_LEAVE_REQUEST";

    /**
     * A section's attendance was marked or edited for one date, inside the ordinary edit window.
     *
     * <p>One bulk event per save (see {@code AuditService#recordBulkChange}), not one per student —
     * a class teacher's morning roll call is exactly the "six hundred rows would bury everything
     * else that happened that day" case {@code AuditService} itself warns against, and marking
     * thirty children present is one act, not thirty. {@code recordCount} carries how many entries
     * the save touched; {@code entityId} is {@code "<sectionId>@<date>"}, since the row being
     * described is a section-day, not any single mark.
     */
    public static final String MARKS_RECORDED = "ATTENDANCE_MARKS_RECORDED";

    /** A correction was approved and applied to the mark it targets. Recorded against the mark. */
    public static final String CORRECTION_APPLIED = "ATTENDANCE_CORRECTION_APPLIED";

    private AttendanceAudit() {}
}
