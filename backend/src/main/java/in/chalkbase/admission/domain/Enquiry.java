package in.chalkbase.admission.domain;

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
 * A prospective student's enquiry: a walk-in, a call, a website lead, a referral, a campaign, or an
 * imported row (FR-016) — captured before anyone applies, so it holds its own contact details
 * rather than referencing a {@code student} row that does not exist yet.
 *
 * <p><strong>{@code assignedCounsellorId} is required, not optional.</strong> The whole point of
 * this module (see the PR that introduced it) is that an enquiry is never a row nobody owns; making
 * assignment an afterthought would let the office capture enquiries that sit unassigned
 * indefinitely, which is the mailbox this feature exists to prevent.
 *
 * <p><strong>{@code nextFollowUpDate} defaults to the day the enquiry is captured</strong> (see the
 * constructor), for the same reason: a freshly captured enquiry with no follow-up date recorded
 * would not appear on anyone's due-date queue until somebody thought to log one, which is exactly
 * the silent gap the queue exists to close. It is cleared the moment the enquiry closes
 * ({@link EnquiryStatus#isClosed()}) — see {@link #applyFollowUp} — so a converted or lost enquiry
 * stops asking anyone to follow up on it.
 *
 * <p>{@code interestedClassId} and {@code assignedCounsellorId} are plain UUIDs, not JPA
 * associations — the same shape {@code document.student_id} and {@code attendance_mark.section_id}
 * use for the tables they point at (ADR-0011, module map): this module has no import of
 * {@code academics} or {@code identity} at all, only their named interfaces
 * ({@code academics.api.AcademicsLookup}, {@code identity.api.IdentityLookup}).
 */
@Entity
@Table(name = "enquiry")
public class Enquiry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "child_full_name", nullable = false, length = 200)
    private String childFullName;

    @Column(name = "child_date_of_birth")
    private LocalDate childDateOfBirth;

    @Column(name = "interested_class_id")
    private UUID interestedClassId;

    @Column(name = "parent_name", nullable = false, length = 200)
    private String parentName;

    @Column(name = "parent_phone", nullable = false, length = 20)
    private String parentPhone;

    @Column(name = "parent_email", length = 200)
    private String parentEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquirySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryStatus status;

    @Column(name = "assigned_counsellor_id", nullable = false)
    private UUID assignedCounsellorId;

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "captured_by", nullable = false, updatable = false)
    private UUID capturedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Enquiry() {
        // for JPA
    }

    public Enquiry(
            String childFullName,
            LocalDate childDateOfBirth,
            UUID interestedClassId,
            String parentName,
            String parentPhone,
            String parentEmail,
            EnquirySource source,
            String remarks,
            UUID assignedCounsellorId,
            UUID capturedBy) {
        this.childFullName = childFullName;
        this.childDateOfBirth = childDateOfBirth;
        this.interestedClassId = interestedClassId;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        this.parentEmail = parentEmail;
        this.source = source;
        this.remarks = remarks;
        this.assignedCounsellorId = assignedCounsellorId;
        this.capturedBy = capturedBy;
        this.status = EnquiryStatus.NEW;
        // Due immediately — see the class Javadoc for why a freshly captured row cannot start with
        // no follow-up date at all.
        this.nextFollowUpDate = LocalDate.now();
    }

    /** Assigns or reassigns the counsellor responsible for this enquiry's follow-up. */
    public void reassign(UUID counsellorId) {
        this.assignedCounsellorId = counsellorId;
        this.updatedAt = Instant.now();
    }

    /**
     * Applies one follow-up's effect on this row: an optional status move, and the next date to
     * follow up on (or none, once closed).
     *
     * @param resultingStatus the status this follow-up moves the enquiry to, or null to log a note
     *     without changing status — in which case a still-{@link EnquiryStatus#NEW} enquiry moves to
     *     {@link EnquiryStatus#IN_PROGRESS} on its own, since logging any follow-up is itself
     *     evidence that someone has looked at it
     * @param nextFollowUpDate when to follow up next, required unless the resulting status closes
     *     the enquiry
     * @throws ChalkbaseException {@link AdmissionErrorCode#ENQUIRY_CLOSED} if this enquiry is
     *     already closed, {@link AdmissionErrorCode#STATUS_CANNOT_REOPEN_TO_NEW} if
     *     {@code resultingStatus} is {@link EnquiryStatus#NEW}, or
     *     {@link AdmissionErrorCode#NEXT_FOLLOW_UP_DATE_REQUIRED} if the enquiry stays open with no
     *     date given
     */
    public void applyFollowUp(EnquiryStatus resultingStatus, LocalDate nextFollowUpDate) {
        if (this.status.isClosed()) {
            throw new ChalkbaseException(AdmissionErrorCode.ENQUIRY_CLOSED);
        }
        if (resultingStatus == EnquiryStatus.NEW) {
            throw new ChalkbaseException(AdmissionErrorCode.STATUS_CANNOT_REOPEN_TO_NEW);
        }
        if (resultingStatus != null) {
            this.status = resultingStatus;
        } else if (this.status == EnquiryStatus.NEW) {
            this.status = EnquiryStatus.IN_PROGRESS;
        }

        if (this.status.isClosed()) {
            this.nextFollowUpDate = null;
        } else {
            if (nextFollowUpDate == null) {
                throw new ChalkbaseException(AdmissionErrorCode.NEXT_FOLLOW_UP_DATE_REQUIRED);
            }
            this.nextFollowUpDate = nextFollowUpDate;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getChildFullName() {
        return childFullName;
    }

    public LocalDate getChildDateOfBirth() {
        return childDateOfBirth;
    }

    public UUID getInterestedClassId() {
        return interestedClassId;
    }

    public String getParentName() {
        return parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public String getParentEmail() {
        return parentEmail;
    }

    public EnquirySource getSource() {
        return source;
    }

    public EnquiryStatus getStatus() {
        return status;
    }

    public UUID getAssignedCounsellorId() {
        return assignedCounsellorId;
    }

    public LocalDate getNextFollowUpDate() {
        return nextFollowUpDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getCapturedBy() {
        return capturedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
