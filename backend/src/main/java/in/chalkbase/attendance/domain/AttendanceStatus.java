package in.chalkbase.attendance.domain;

/**
 * The six marks a student's attendance may carry (Phase 0 decision 8).
 *
 * <p>{@code docs/requirements/02-functional-requirements.md} (FR-046) lists eight — it adds
 * "medical leave" and "activity duty" to this set. The Phase 0 decision, which is the later and
 * more deliberate of the two documents (it was decided 2026-09-05, specifically to remove
 * ambiguity before this module was written), settles on six. This build follows the decision and
 * flags the mismatch rather than silently picking one: see the PR that introduced this file.
 * {@link #EXCUSED_LEAVE} is this build's answer to both "medical leave" and "activity duty" — the
 * {@code remarks} field on a mark is where the difference between them is recorded, if a school
 * wants it recorded at all.
 */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    HALF_DAY,
    EXCUSED_LEAVE,
    HOLIDAY
}
