/**
 * Attendance: daily and period-wise, for students (Phase 0 decision 8, ADR-0030).
 *
 * <p>Two grains in one table, {@code attendance_mark}, from its first migration — deferring the
 * period-wise columns was rejected because changing the grain of a high-volume table after real
 * attendance exists is the expensive kind of migration. Only the <strong>daily</strong> grain has
 * a write path in this build: a class teacher marks their section once a day. Period-wise gets its
 * table shape and nothing more; see {@code ADR-0030} for what that means and does not mean.
 *
 * <p>A mark is editable in place until it locks — end of the attendance day plus 24 hours,
 * computed at request time rather than by a scheduled job. After that, a change goes through
 * {@code attendance_correction_request}, which an administrator decides, and both the original
 * mark and the correction stay recorded: the correction row keeps its own snapshot of what the
 * mark said before, and the audit log carries a separate entry for the creation and for the
 * correction (field names only, per ADR-0018) — neither overwrites the other.
 *
 * <p><strong>Staff attendance is not here.</strong> The module map reserves {@code attendance} for
 * both, but staff attendance needs a {@code staff} module that does not exist yet.
 *
 * <p>Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011). Reaches {@code academics} and {@code student} only
 * through their named interfaces, {@code academics.api.AcademicsLookup} and
 * {@code student.api.StudentLookup}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Attendance")
package in.chalkbase.attendance;
