package in.chalkbase.attendance.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.util.List;

/**
 * Marking or editing a section's daily attendance for one date, in one call.
 *
 * <p>Whole and idempotent by design: the client sends every student it wants marked, present
 * included, in a single request, and the service upserts each one in the same transaction — a
 * class teacher's "mark all present, then change the three who are not" is one save, not thirty.
 *
 * <p>No {@code academicSessionId} here: the service resolves the school's current academic session
 * itself, through {@code academics.api.AcademicsLookup}, the same way it resolves who is marking
 * from the security context. Attendance is always marked for the session the school says it is in
 * today, and a client that could name a different one could file today's roll call against last
 * year's roster.
 *
 * <p>{@link PastOrPresent} is the DTO's own refusal of a future date, the same relationship
 * {@code EndsAfterStart} has to {@code AcademicsErrorCode.INVALID_SESSION_DATES}:
 * {@code AttendanceErrorCode.FUTURE_DATE_NOT_ALLOWED} is what the same rule is called when a write
 * reaches the service without going through this validation.
 */
public record MarkAttendanceRequest(
        @Classification(Tier.INTERNAL) @NotNull @PastOrPresent LocalDate attendanceDate,

        @Classification(Tier.CONFIDENTIAL) @NotEmpty List<@Valid AttendanceEntryRequest> entries) {

    public MarkAttendanceRequest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
