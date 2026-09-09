package in.chalkbase.attendance.infrastructure;

import in.chalkbase.attendance.domain.AttendanceLeaveRequest;
import in.chalkbase.attendance.domain.LeaveDecision;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceLeaveRequestRepository extends JpaRepository<AttendanceLeaveRequest, UUID> {

    /** The queue: requests in one decision state, oldest first — {@code idx_attendance_leave_request_pending}. */
    Page<AttendanceLeaveRequest> findByDecisionOrderByRequestedAtAsc(LeaveDecision decision, Pageable pageable);

    /**
     * Every approved request covering {@code date} for any of {@code studentIds} — the marking
     * screen's own read, at the moment a section's register is opened for that date. Two identical
     * date parameters bind the range's ends: {@code startDate <= date <= endDate}.
     *
     * <p>Served by {@code idx_attendance_leave_request_student_dates}.
     */
    List<AttendanceLeaveRequest> findByDecisionAndStudentIdInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveDecision decision, Collection<UUID> studentIds, LocalDate onOrAfterStart, LocalDate onOrBeforeEnd);
}
