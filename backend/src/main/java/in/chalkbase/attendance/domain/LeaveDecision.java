package in.chalkbase.attendance.domain;

/**
 * Where a leave request stands.
 *
 * <p>Deliberately its own enum rather than a reuse of {@link CorrectionDecision}, even though the
 * three values are identical and the state machine — pending, then decided once, never back — is
 * the same shape. A leave request and a correction request are different acts (one is filed in
 * advance of a day, the other after it) that only coincidentally share a decision vocabulary, and
 * {@code CorrectionDecision} is already a published type on {@code CorrectionRequestResponse}'s
 * wire shape: reusing it here would mean a rename of that type — and therefore of the generated
 * contract's schema name — to read sensibly for both, for a saving of one three-value enum.
 */
public enum LeaveDecision {
    PENDING,
    APPROVED,
    REJECTED
}
