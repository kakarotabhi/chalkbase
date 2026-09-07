package in.chalkbase.attendance.infrastructure;

import in.chalkbase.attendance.domain.AttendanceMark;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes for {@link AttendanceMark}.
 *
 * <p>The two finder methods below are what {@code idx_attendance_mark_section_date} and
 * {@code idx_attendance_mark_student_date} exist to serve — see the migration comment for why
 * those two shapes, and only those two, are indexed.
 */
public interface AttendanceMarkRepository extends JpaRepository<AttendanceMark, UUID> {

    /** The daily mark, if any, for one section on one date — the marking screen's own read. */
    List<AttendanceMark> findBySectionIdAndAttendanceDateAndPeriodNumberIsNull(
            UUID sectionId, LocalDate attendanceDate);

    /** The same rows, keyed by student, for an upsert that needs to know which already exist. */
    List<AttendanceMark> findByAttendanceDateAndPeriodNumberIsNullAndStudentIdIn(
            LocalDate attendanceDate, Collection<UUID> studentIds);

    /** One student's daily marks over a date range — a session's worth, or a month for a report. */
    List<AttendanceMark> findByStudentIdAndPeriodNumberIsNullAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            UUID studentId, LocalDate from, LocalDate to);
}
