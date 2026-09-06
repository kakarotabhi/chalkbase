package in.chalkbase.platform.audit;

/**
 * How an audited attempt ended.
 *
 * <p>Mirrors {@code ck_audit_event_outcome} in
 * {@code V2026_09_06_0900__platform_create_audit_event.sql}. Change one and you must change the
 * other — a value this enum accepts and the constraint rejects is an insert that fails in a
 * school's database rather than in a test.
 *
 * <p>A closed set, unlike {@link AuditAction}, which is why it is an enum and that is a string.
 * There are only three ways an attempt ends, and no future module invents a fourth.
 */
public enum AuditOutcome {

    /** The action was permitted and completed. */
    SUCCESS,

    /** The action was attempted and did not complete — a wrong password, a failed export. */
    FAILURE,

    /** The action was refused by authorization. The caller was not allowed to try. */
    DENIED
}
