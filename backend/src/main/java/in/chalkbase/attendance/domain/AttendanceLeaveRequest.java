package in.chalkbase.attendance.domain;

import in.chalkbase.platform.error.ChalkbaseException;
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
 * A guardian-or-teacher's advance notice that a student will be away on a date range, and an
 * authorised person's decision on it (FR-047, this module's Phase 2 lane).
 *
 * <p><strong>This table has no foreign key to {@link AttendanceMark}, in either direction, and
 * approving one never writes one.</strong> See the ADR-0030 amendment ("Leave requests: how an
 * approval reaches the register") for the full reasoning; in short, {@code MarkAttendanceRequest}
 * refuses a future date and {@code AttendanceMarkingService.mark} validates a roster that is only
 * known as of the day marked — a leave request filed in advance names neither reliably yet.
 * Instead, {@code AttendanceMarkingService.view} reads {@code attendance_leave_request} at the
 * moment a section's register is opened, for the date opened, and offers {@link
 * AttendanceStatus#EXCUSED_LEAVE} as an unmarked student's default. Approving connects to the
 * register at the point marking actually happens, not by mutating a mark this table has no
 * business writing.
 *
 * <p>{@code sectionId} is the section the student was on as of filing, the same reasoning {@link
 * AttendanceMark#sectionId} gives: fixed at creation so a mid-year section move does not rewrite
 * which class a past request named.
 *
 * <p><strong>Overlap between two requests for the same student is not prevented.</strong> A second
 * request covering dates already covered by an earlier pending or approved one is accepted; nothing
 * here or in {@code AttendanceLeaveService} rejects it. This lane's read path (one boolean, "is
 * there an approved request covering this date") is unaffected by a duplicate, and building the
 * exclusion this would need — a date-range overlap check, most naturally a PostgreSQL {@code
 * EXCLUDE} constraint over {@code btree_gist} — is a real feature this lane does not add a
 * dependency for. Noted so it reads as a scoped decision, not an oversight.
 */
@Entity
@Table(name = "attendance_leave_request")
public class AttendanceLeaveRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "academic_session_id", nullable = false, updatable = false)
    private UUID academicSessionId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveDecision decision = LeaveDecision.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AttendanceLeaveRequest() {
        // for JPA
    }

    public AttendanceLeaveRequest(
            UUID studentId,
            UUID sectionId,
            UUID academicSessionId,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            UUID requestedBy) {
        this.studentId = studentId;
        this.sectionId = sectionId;
        this.academicSessionId = academicSessionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    /**
     * Decides this request. Refuses a second decision on the same row —
     * {@link AttendanceErrorCode#LEAVE_REQUEST_NOT_PENDING} — the same discipline
     * {@link AttendanceCorrectionRequest#decide} holds its own row to.
     */
    public void decide(LeaveDecision decision, UUID decidedBy, String decisionNote) {
        if (this.decision != LeaveDecision.PENDING) {
            throw new ChalkbaseException(AttendanceErrorCode.LEAVE_REQUEST_NOT_PENDING);
        }
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public UUID getAcademicSessionId() {
        return academicSessionId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public LeaveDecision getDecision() {
        return decision;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
