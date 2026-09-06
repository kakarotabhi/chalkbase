package in.chalkbase.student.domain;

/**
 * What this module calls the things it writes to the audit log (ADR-0018).
 *
 * <p><strong>The {@code entityId} of every row this module writes is a UUID. Never an admission
 * number, never a name.</strong> Both are Confidential under ADR-0014 and both identify a child,
 * and the audit log is read by more people than the student record is — an inspection can be given
 * {@code platform:audit:read} without being given {@code student:student:read}. A log that carried
 * admission numbers would hand that reader a roster of the school's children as a side effect of
 * oversight.
 *
 * <p><strong>Which UUID is a decision, and it is not always the row that changed.</strong>
 *
 * <ul>
 *   <li>{@link #STUDENT}, {@link #STUDENT_ENROLMENT} and {@link #STUDENT_GUARDIAN} rows all carry
 *       the <em>student's</em> id. The audit log is indexed on {@code (entity_type, entity_id)}, and
 *       "what happened to this child" is the question it will actually be asked — by a parent
 *       disputing a roll number, by a principal asked why a transfer certificate says what it says.
 *       Recording an enrolment change against the enrolment's own id would make that question
 *       unanswerable without first knowing every enrolment id the child has ever had, which is a
 *       lookup nobody performing an audit has. The {@code entity_type} is what says which of the
 *       three kinds of change it was, and {@code changed_fields} says what moved.
 *   <li>{@link #GUARDIAN} rows carry the <em>guardian's</em> id, because a guardian is shared
 *       between siblings (ADR-0020 §5). Correcting a father's phone number is one change to one
 *       person; attributing it to one of his four children would be false, and attributing it to all
 *       four would claim four changes that did not happen.
 * </ul>
 *
 * <p>There are no verbs of this module's own. {@code AuditAction.ENTITY_CREATED},
 * {@code ENTITY_UPDATED} and {@code ENTITY_DELETED} say everything, because the entity type beside
 * them is already specific — {@code ENTITY_DELETED} on {@code STUDENT_GUARDIAN} reads as "a guardian
 * was detached from this child" and could not be mistaken for a child being deleted, which is a
 * thing this module cannot do. {@code academics} added {@code SESSION_MADE_CURRENT} because moving a
 * school between academic years is one switch with consequences everywhere; nothing here is that.
 */
public final class StudentAudit {

    /** A child's own record. {@code entityId} is the student's id. */
    public static final String STUDENT = "STUDENT";

    /** A guardian person record. {@code entityId} is the guardian's id — see the class javadoc. */
    public static final String GUARDIAN = "GUARDIAN";

    /** A guardian attached to, edited on, or detached from a child. {@code entityId} is the student's id. */
    public static final String STUDENT_GUARDIAN = "STUDENT_GUARDIAN";

    /** A child placed in a section for a year, moved, renumbered or ended. {@code entityId} is the student's id. */
    public static final String STUDENT_ENROLMENT = "STUDENT_ENROLMENT";

    private StudentAudit() {}
}
