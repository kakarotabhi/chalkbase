package in.chalkbase.fee.domain;

/**
 * What this module calls the things it writes to the audit log (ADR-0018).
 *
 * <p>{@link #FEE_STRUCTURE} covers a whole version being written — {@code changedFields} names the
 * item and installment shape that changed (never an amount), and {@code entityId} is
 * {@code "<sessionId>@<classId>"}, the same "describe the thing being decided about" shape
 * {@code AttendanceAudit} uses for a section-day, since the row that actually changed is a new
 * {@link FeeStructure} id each time and the auditor's question is "what happened to this class's
 * fees", not "what happened to this one row".
 */
public final class FeeAudit {

    public static final String FEE_HEAD = "FEE_HEAD";
    public static final String FEE_CONCESSION_TYPE = "FEE_CONCESSION_TYPE";
    public static final String FEE_STRUCTURE = "FEE_STRUCTURE";

    /** A new version of a class's fee structure was written for a session, superseding the previous one if any. */
    public static final String STRUCTURE_VERSION_SAVED = "FEE_STRUCTURE_VERSION_SAVED";

    /** A structure was copied wholesale from one session into another (the new-session convenience action). */
    public static final String STRUCTURE_COPIED = "FEE_STRUCTURE_COPIED";

    private FeeAudit() {}
}
