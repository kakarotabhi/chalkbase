package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Where a student came from, and the paperwork that admitted them (FR-033).
 *
 * <p>Confidential under ADR-0014, like the rest of the student record: it identifies where a child
 * came from, not what they are, so none of ADR-0022's masking or encryption applies here.
 *
 * <p>Keyed by the student's own id — one row per student, present only once the office has entered
 * a previous school or a transfer certificate. A fresh admission with no prior school has no row at
 * all, which is a truer answer than five empty columns on {@code student}.
 */
@Entity
@Table(name = "student_transfer")
public class StudentTransfer {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "previous_school_name", length = 200)
    private String previousSchoolName;

    @Column(name = "previous_school_board", length = 60)
    private String previousSchoolBoard;

    @Column(name = "transfer_certificate_number", length = 60)
    private String transferCertificateNumber;

    @Column(name = "transfer_certificate_issued_on")
    private LocalDate transferCertificateIssuedOn;

    /** Confidential free text. FR-033's "migration details" covers more than a fixed list would hold. */
    @Column(name = "reason_for_leaving", columnDefinition = "text")
    private String reasonForLeaving;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentTransfer() {
        // for JPA
    }

    public StudentTransfer(
            UUID studentId,
            String previousSchoolName,
            String previousSchoolBoard,
            String transferCertificateNumber,
            LocalDate transferCertificateIssuedOn,
            String reasonForLeaving) {
        this.studentId = studentId;
        apply(
                previousSchoolName,
                previousSchoolBoard,
                transferCertificateNumber,
                transferCertificateIssuedOn,
                reasonForLeaving);
    }

    /** Overwrites every editable field, as a whole form — see {@link Student#apply}. */
    public final void apply(
            String previousSchoolName,
            String previousSchoolBoard,
            String transferCertificateNumber,
            LocalDate transferCertificateIssuedOn,
            String reasonForLeaving) {
        this.previousSchoolName = previousSchoolName;
        this.previousSchoolBoard = previousSchoolBoard;
        this.transferCertificateNumber = transferCertificateNumber;
        this.transferCertificateIssuedOn = transferCertificateIssuedOn;
        this.reasonForLeaving = reasonForLeaving;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getPreviousSchoolName() {
        return previousSchoolName;
    }

    public String getPreviousSchoolBoard() {
        return previousSchoolBoard;
    }

    public String getTransferCertificateNumber() {
        return transferCertificateNumber;
    }

    public LocalDate getTransferCertificateIssuedOn() {
        return transferCertificateIssuedOn;
    }

    public String getReasonForLeaving() {
        return reasonForLeaving;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
