package in.chalkbase.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One student's attendance for one day (daily grain) or one period (period-wise grain).
 *
 * <p>{@code studentId}, {@code academicSessionId}, {@code sectionId} and {@code subjectId} are
 * plain UUIDs, not JPA associations — the same shape {@code StudentEnrolment} uses for
 * {@code academicSessionId} and {@code sectionId}, and {@code Document} uses for
 * {@code studentId}: this module has no import of {@code student} or {@code academics} at all. The
 * foreign keys live in the database, where {@code ModularityTests} cannot see them and does not
 * need to.
 *
 * <p><strong>{@code sectionId} is fixed at creation, deliberately.</strong> It is the section a
 * student sat in on the day marked, not whatever their enrolment says today — a mid-year move to
 * another section must not rewrite what a past date says about where a child was.
 *
 * <p><strong>Mutable until it locks, then append-only.</strong> {@link #isEditableOn} answers
 * whether direct editing is still allowed — end of the attendance day plus 24 hours, computed from
 * the current date rather than stored, so there is no scheduled job to lock a row and nothing to
 * drift out of step with it. Past that point, {@code AttendanceCorrectionService} is the only path
 * to a changed {@link #status}, and it goes through {@link #applyCorrection}, never
 * {@link #mark}.
 */
@Entity
@Table(name = "attendance_mark")
public class AttendanceMark {

    /** How long after the attendance day itself a mark stays directly editable (Phase 0 decision 8). */
    private static final int LOCK_GRACE_DAYS = 1;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_session_id", nullable = false, updatable = false)
    private UUID academicSessionId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    /** Null for the daily grain, which is the only one this build writes. */
    @Column(name = "period_number")
    private Short periodNumber;

    /** Null for the daily grain. */
    @Column(name = "subject_id")
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(length = 500)
    private String remarks;

    @Column(name = "marked_by", nullable = false, updatable = false)
    private UUID markedBy;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AttendanceMark() {
        // for JPA
    }

    /** The daily grain. Period-wise construction is not exposed — see the class Javadoc. */
    public AttendanceMark(
            UUID studentId,
            UUID academicSessionId,
            UUID sectionId,
            LocalDate attendanceDate,
            AttendanceStatus status,
            String remarks,
            UUID markedBy) {
        this.studentId = studentId;
        this.academicSessionId = academicSessionId;
        this.sectionId = sectionId;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.remarks = remarks;
        this.markedBy = markedBy;
    }

    /** Whether {@code attendanceDate} may still be edited directly, as of {@code today}. */
    public boolean isEditableOn(LocalDate today) {
        return !today.isAfter(attendanceDate.plusDays(LOCK_GRACE_DAYS));
    }

    /** A same-window edit: the teacher changing their own mind before the lock, no workflow needed. */
    public void mark(AttendanceStatus status, String remarks, UUID markedBy) {
        this.status = status;
        this.remarks = remarks;
        this.markedBy = markedBy;
        this.markedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Applies an approved correction. Only {@code AttendanceCorrectionService} calls this. */
    public void applyCorrection(AttendanceStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getAcademicSessionId() {
        return academicSessionId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public Short getPeriodNumber() {
        return periodNumber;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getMarkedBy() {
        return markedBy;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
