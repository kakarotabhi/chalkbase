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

    /** A student's contact section (FR-028) entered or corrected. {@code entityId} is the student's id. */
    public static final String STUDENT_CONTACT = "STUDENT_CONTACT";

    /**
     * A student's previous school or transfer certificate (FR-033) entered or corrected.
     * {@code entityId} is the student's id.
     */
    public static final String STUDENT_TRANSFER = "STUDENT_TRANSFER";

    /**
     * A student's health record (FR-034) entered, corrected, or revealed. {@code entityId} is the
     * student's id.
     *
     * <p>The same entity type carries both kinds of row, on purpose: {@code AuditAction.ENTITY_UPDATED}
     * for a write and {@link #RESTRICTED_DATA_REVEALED} for a read of the six Restricted fields, so a
     * reader asking "what has happened to this child's medical record" sees edits and reveals
     * together rather than having to know to look in two places.
     */
    public static final String STUDENT_MEDICAL = "STUDENT_MEDICAL";

    /**
     * A student's UDISE+/board identifiers and statutory categories (FR-029) entered, corrected, or
     * revealed. {@code entityId} is the student's id. See {@link #STUDENT_MEDICAL} for why writes and
     * reveals share one entity type.
     */
    public static final String STUDENT_COMPLIANCE = "STUDENT_COMPLIANCE";

    /**
     * A caller was shown the real value of a Restricted field — ADR-0014's "every read is audited",
     * made concrete. Recorded by {@code AuditService#recordSecurityEvent}, in its own transaction and
     * regardless of what happens afterwards, the same as {@code AuditAction#DATA_EXPORTED}: this is
     * the moment encrypted-at-rest data left the boundary that protects it, not a fact about whether
     * the surrounding request went on to succeed.
     *
     * <p><strong>What counts as "a read" here is deliberate and narrow.</strong> Opening a student's
     * record does not produce this row — {@code MedicalSummary}/{@code ComplianceSummary} carry only
     * presence flags, so nothing Restricted left the server. Only a call to
     * {@code GET …/medical/restricted} or {@code GET …/compliance/restricted} does, because that is
     * the one moment an actual caste, religion, disability status or APAAR id is decrypted and sent
     * over the wire. Auditing the record view instead would write a row on every page load a class
     * teacher makes; auditing nothing would leave ADR-0014's "every read is audited" unmet. This is
     * the line between "looked at the record" and "looked at the Restricted value".
     *
     * <p>{@code changed_fields} carries the NAMES of the Restricted fields that actually held a
     * value for this student — never the values (ADR-0014, ADR-0018 rule 11). A field the school
     * never recorded is not named: nothing was disclosed for it, so there is nothing for the row to
     * say. This differs from {@link #STUDENT_EXPORT}, whose field list is the shape of the file —
     * every column the mode allows, the same for every row it contains — rather than a fact about
     * any one student. A reveal has exactly one student behind it, so "what did this row disclose"
     * and "what could this row have disclosed" are the same question asked two different ways, and
     * this is the one the audit log answers.
     */
    public static final String RESTRICTED_DATA_REVEALED = "RESTRICTED_DATA_REVEALED";

    /**
     * A CSV export of the student roster, masked or unmasked (ADR-0014, ADR-0027). {@code entityId}
     * is null: an export has no single row it is about, and it can span every enrolment year at
     * once, unlike {@link #STUDENT_IMPORT}, which at least ties to the academic session a school
     * uploaded into. {@code changed_fields} carries the NAMES of the columns the file actually
     * contained — never a value, per the rule {@code AuditService} already enforces — and
     * {@code record_count} carries how many rows.
     *
     * <p>Written for <strong>every</strong> export, not only the unmasked one: ADR-0014 says a
     * Confidential export is audited unconditionally, and a masked export still contains a child's
     * name and admission number. The unmasked export is the same action with more field names in
     * {@code changed_fields} and its own permission gating it — see
     * {@code StudentPermissions#STUDENT_EXPORT_UNMASKED}.
     */
    public static final String STUDENT_EXPORT = "STUDENT_EXPORT";

    /**
     * As {@link #STUDENT_EXPORT}, for the guardian directory. Guardians carry no Restricted field,
     * so this one has no masked/unmasked distinction.
     */
    public static final String GUARDIAN_EXPORT = "GUARDIAN_EXPORT";

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
