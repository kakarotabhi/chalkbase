package in.chalkbase.attendance.infrastructure;

import in.chalkbase.attendance.domain.AttendanceCorrectionRequest;
import in.chalkbase.attendance.domain.CorrectionDecision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRequestRepository extends JpaRepository<AttendanceCorrectionRequest, UUID> {

    /** The admin's queue: {@code idx_attendance_correction_pending} carries both this filter and the sort. */
    Page<AttendanceCorrectionRequest> findByDecisionOrderByRequestedAtAsc(
            CorrectionDecision decision, Pageable pageable);

    /** Every request ever filed against one mark, newest first — a teacher checking their own history. */
    List<AttendanceCorrectionRequest> findByAttendanceMarkIdOrderByRequestedAtDesc(UUID attendanceMarkId);

    /** {@code uq_attendance_correction_one_pending}: is there already one awaiting a decision? */
    Optional<AttendanceCorrectionRequest> findByAttendanceMarkIdAndDecision(
            UUID attendanceMarkId, CorrectionDecision decision);
}
