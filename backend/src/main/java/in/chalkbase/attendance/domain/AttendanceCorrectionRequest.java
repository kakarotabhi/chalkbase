package in.chalkbase.attendance.domain;

import in.chalkbase.platform.error.ChalkbaseException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A request to change a locked {@link AttendanceMark}, and an administrator's decision on it
 * (Phase 0 decision 8).
 *
 * <p>{@link #previousStatus} is a snapshot taken when the request is filed. It lives here, in
 * ordinary domain data, rather than in the audit log — ADR-0018 never lets the audit log hold a
 * field's value, only its name, so "what did the mark used to say" has to be recoverable from
 * somewhere else if it is to be recoverable at all. This row is that somewhere else. The audit log
 * still carries its own entry for the mark's original creation and a second one for the correction
 * being applied, which is what keeps both events in the audit log as the decision requires — see
 * {@code AttendanceAudit}.
 *
 * <p>{@code attendanceMarkId} is a plain UUID rather than a {@code @ManyToOne} to
 * {@link AttendanceMark}, even though both entities belong to this module: the two are read and
 * written by different services at different times, on the two sides of the lock, and a JPA
 * association would make loading one drag in the other every time.
 */
@Entity
@Table(name = "attendance_correction_request")
public class AttendanceCorrectionRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "attendance_mark_id", nullable = false, updatable = false)
    private UUID attendanceMarkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20, updatable = false)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", nullable = false, length = 20, updatable = false)
    private AttendanceStatus requestedStatus;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionDecision decision = CorrectionDecision.PENDING;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AttendanceCorrectionRequest() {
        // for JPA
    }

    public AttendanceCorrectionRequest(
            UUID attendanceMarkId,
            AttendanceStatus previousStatus,
            AttendanceStatus requestedStatus,
            String reason,
            UUID requestedBy) {
        this.attendanceMarkId = attendanceMarkId;
        this.previousStatus = previousStatus;
        this.requestedStatus = requestedStatus;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    /**
     * Decides this request. Refuses a second decision on the same row — {@code AttendanceErrorCode}
     * calls that {@code CORRECTION_NOT_PENDING} — the same discipline
     * {@code uq_attendance_correction_one_pending} gives the write side.
     */
    public void decide(CorrectionDecision decision, UUID decidedBy, String decisionNote) {
        if (this.decision != CorrectionDecision.PENDING) {
            throw new ChalkbaseException(AttendanceErrorCode.CORRECTION_NOT_PENDING);
        }
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAttendanceMarkId() {
        return attendanceMarkId;
    }

    public AttendanceStatus getPreviousStatus() {
        return previousStatus;
    }

    public AttendanceStatus getRequestedStatus() {
        return requestedStatus;
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

    public CorrectionDecision getDecision() {
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
