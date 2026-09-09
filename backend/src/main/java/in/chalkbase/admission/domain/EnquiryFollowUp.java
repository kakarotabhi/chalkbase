package in.chalkbase.admission.domain;

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
 * One dated note against an {@link Enquiry} — who followed up, when, what was said, and what
 * happens next (FR-017's "follow-up history").
 *
 * <p>Append-only: there is no method that edits or removes one, the same discipline
 * {@code attendance_correction_request} and {@code AttendanceCorrectionRequest} apply to a decided
 * correction. A counsellor's record of what happened on a call is exactly the kind of row that
 * should never quietly change after the fact — a follow-up log that could be edited is a follow-up
 * log nobody can trust when a parent later disputes what they were told.
 *
 * <p>{@code enquiryId} is a plain UUID rather than a {@code @ManyToOne} to {@link Enquiry}, even
 * though both belong to this module: the two are read and written at different times by the same
 * service, and a JPA association would drag the parent row along on every read of the history it
 * does not need.
 */
@Entity
@Table(name = "enquiry_follow_up")
public class EnquiryFollowUp {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "enquiry_id", nullable = false, updatable = false)
    private UUID enquiryId;

    @Column(nullable = false, length = 1000, updatable = false)
    private String note;

    @Column(name = "next_follow_up_date", updatable = false)
    private LocalDate nextFollowUpDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", length = 20, updatable = false)
    private EnquiryStatus resultingStatus;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected EnquiryFollowUp() {
        // for JPA
    }

    public EnquiryFollowUp(
            UUID enquiryId, String note, LocalDate nextFollowUpDate, EnquiryStatus resultingStatus, UUID recordedBy) {
        this.enquiryId = enquiryId;
        this.note = note;
        this.nextFollowUpDate = nextFollowUpDate;
        this.resultingStatus = resultingStatus;
        this.recordedBy = recordedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEnquiryId() {
        return enquiryId;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getNextFollowUpDate() {
        return nextFollowUpDate;
    }

    public EnquiryStatus getResultingStatus() {
        return resultingStatus;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
