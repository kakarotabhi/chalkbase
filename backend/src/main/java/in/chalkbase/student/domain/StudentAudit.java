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
 *       four would claim four changes that did not happen. {@link #GUARDIANS_IMPORTED} is the one
 *       exception on this same entity type, for the reason {@link #STUDENT_IMPORT} is an exception
 *       to the row above: several new guardians created in one bulk act are one fact about the
 *       import that created them, not several facts about several people.
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

    /**
     * A whole file of students admitted at once. {@code entityId} is the <em>academic session's</em>
     * id — the third exception to the rule above, and the reason is the same one that produced it.
     *
     * <p>An import is one act with one decision behind it, and the thing it is a fact about is the
     * year it loaded. Six hundred {@code ENTITY_CREATED} rows would bury every other thing that
     * happened that day in the one log a principal reads to find out what happened that day
     * (ADR-0021 §7), and there is no single child this row is about.
     *
     * <p>The individual children are not lost by this: they exist, with their {@code created_at},
     * and this row says when the load happened and who ran it.
     */
    public static final String STUDENT_IMPORT = "STUDENT_IMPORT";

    /**
     * A verb of this module's own, for the reason {@code AuditAction} allows one.
     *
     * <p>{@code ENTITY_CREATED} on {@link #STUDENT_IMPORT} would read as "an import was created",
     * which is not what happened — a school's whole roll arrived in one act, and the log a principal
     * reads should say so in the row rather than in a field name.
     */
    public static final String STUDENTS_IMPORTED = "STUDENTS_IMPORTED";

    /**
     * The guardian half of a student import (ADR-0021 §4): a phone number in the file that matched
     * nobody in this school's directory, so a new {@link #GUARDIAN} row was written for it.
     *
     * <p>A separate bulk row from {@link #STUDENTS_IMPORTED}, on entity type {@link #GUARDIAN}
     * rather than folded into the student one — the two are writes to different tables, and a reader
     * asking "how many new guardians did this import create" should not have to know that the answer
     * is hiding inside a student-shaped event. Not written at all when every guardian in the file
     * matched an existing directory entry, because then nothing was created and there is nothing to
     * say — a school should never see an audit row for zero.
     *
     * <p>{@code entityId} is the academic session's id, for the same reason {@link #STUDENT_IMPORT}
     * is: this is one fact about the import that created these guardians, not about any one of them.
     */
    public static final String GUARDIANS_IMPORTED = "GUARDIANS_IMPORTED";

    private StudentAudit() {}
}
