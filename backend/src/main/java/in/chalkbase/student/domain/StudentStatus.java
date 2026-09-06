package in.chalkbase.student.domain;

/**
 * Where a student stands with the school. What {@code ck_student_status} allows.
 *
 * <p><strong>This is what exists instead of deleting a student</strong> (ADR-0020 §6). Fees,
 * attendance and marks all reference a student, and a school that removed one would leave those
 * pointing at nothing — while still being legally required to produce the record years later. So a
 * child who leaves is {@link #WITHDRAWN} or {@link #TRANSFERRED}, and the row stays.
 *
 * <p>{@link #INACTIVE} is the vaguer one on purpose: a long absence, an admission that has not been
 * confirmed, a record the office has parked. A school that wants to say why has the audit log; a
 * status per reason would be a list nobody could keep agreeing on.
 */
public enum StudentStatus {

    /** On the rolls. */
    ACTIVE,

    /** On the rolls but not attending — parked, absent long-term, or not yet confirmed. */
    INACTIVE,

    /** Left for another school, and a transfer certificate was issued or is owed. */
    TRANSFERRED,

    /** Completed the school's highest class. */
    GRADUATED,

    /** Left without transferring. */
    WITHDRAWN
}
