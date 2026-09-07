package in.chalkbase.attendance.domain;

/** Where a correction request stands. {@code ck_attendance_correction_decided_together} pairs this with who and when. */
public enum CorrectionDecision {
    PENDING,
    APPROVED,
    REJECTED
}
