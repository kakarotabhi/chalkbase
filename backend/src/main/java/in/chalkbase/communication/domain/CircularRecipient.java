package in.chalkbase.communication.domain;

import in.chalkbase.platform.error.ChalkbaseException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One student a published circular reached, and the acknowledgement recorded against them, if any.
 *
 * <p><strong>This is the answer to "what is a recipient record, today"</strong> — see the module's
 * own {@code package-info.java} for the full reasoning. In short: {@code studentId} rather than a
 * guardian account, because a guardian may have no account at all (ADR-0017) and a student id is
 * permanent in a way "whichever guardian eventually signs in" is not. {@code sectionId} is the
 * section the student sat in at publish time, the same "fixed at creation" choice
 * {@code AttendanceMark.sectionId} makes, for the same reason: a later section move must not
 * rewrite who a past circular reached.
 *
 * <p>{@code deliveredAt} is set the instant the row is created — there is no queue an in-app
 * circular waits in (see the module's package doc on why there is no channel port here).
 * {@code viewedAt} is table shape only, with no write path in this build: it is where a read
 * receipt will land once a parent-facing screen exists to set it, and its absence today costs
 * nothing to add later. {@code acknowledgedBy} names the {@code user_account} that recorded the
 * acknowledgement — today always a staff member acting on a family's behalf (by phone, in writing,
 * or in person), and unchanged in shape the day a guardian's own account can call the same
 * endpoint for themselves.
 */
@Entity
@Table(name = "circular_recipient")
public class CircularRecipient {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "circular_id", nullable = false, updatable = false)
    private UUID circularId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt = Instant.now();

    /** No write path in this build. See the class Javadoc. */
    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledgement_note", length = 500)
    private String acknowledgementNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CircularRecipient() {
        // for JPA
    }

    public CircularRecipient(UUID circularId, UUID studentId, UUID sectionId) {
        this.circularId = circularId;
        this.studentId = studentId;
        this.sectionId = sectionId;
    }

    /**
     * Records an acknowledgement. Refuses one against a circular that does not require it
     * ({@link CommunicationErrorCode#ACKNOWLEDGEMENT_NOT_REQUIRED}, checked by the caller, which
     * holds the circular) or a second one against the same recipient
     * ({@link CommunicationErrorCode#ALREADY_ACKNOWLEDGED}).
     */
    public void acknowledge(UUID acknowledgedBy, String note) {
        if (this.acknowledgedAt != null) {
            throw new ChalkbaseException(CommunicationErrorCode.ALREADY_ACKNOWLEDGED);
        }
        this.acknowledgedAt = Instant.now();
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgementNote = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCircularId() {
        return circularId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public UUID getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public String getAcknowledgementNote() {
        return acknowledgementNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
