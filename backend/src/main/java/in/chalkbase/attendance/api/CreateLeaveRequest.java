package in.chalkbase.attendance.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filing a leave request for one student on a section's roster, in advance of the date(s) it names.
 *
 * <p>{@code sectionId} is not here: it comes from the URL path, the same relationship
 * {@link MarkAttendanceRequest} has to the section it marks — a student is validated against that
 * section's live roster, the way {@code AttendanceMarkingService.mark} already validates one.
 *
 * <p>{@link FutureOrPresent} is this DTO's own refusal of a date already past, the same relationship
 * {@link MarkAttendanceRequest#attendanceDate}'s {@code PastOrPresent} has to
 * {@code AttendanceErrorCode.FUTURE_DATE_NOT_ALLOWED}: {@code AttendanceErrorCode.LEAVE_DATE_IN_PAST}
 * is what the same rule is called when a write reaches the service without going through this
 * validation. There is no annotation-level check that {@code endDate} is not before {@code
 * startDate} — cross-field validation needs either a custom validator or a service-level check, and
 * the service already has to check {@code startDate} against today, so both live there together
 * ({@code AttendanceLeaveService#create}).
 *
 * @param reason may name a medical condition — see {@code LeaveRequestResponse}'s Javadoc for why
 *     this is Confidential rather than Restricted.
 */
public record CreateLeaveRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID studentId,

        @Classification(Tier.INTERNAL) @NotNull @FutureOrPresent LocalDate startDate,

        @Classification(Tier.INTERNAL) @NotNull LocalDate endDate,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 500) String reason) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
