package in.chalkbase.communication.domain;

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
 * A circular: a title, a body, and whether reading it is expected to be acknowledged.
 *
 * <p>Mutable only while {@link CircularStatus#DRAFT}. {@link #publish} is the one-way transition
 * to {@link CircularStatus#PUBLISHED} — see that enum's Javadoc for why there is no way back.
 * Nothing on this entity names its audience directly: {@link CircularTarget} rows say what was
 * targeted, and {@link CircularRecipient} rows say who it actually reached, because "what we meant
 * to send this to" and "who it reached" are different facts that must both survive a later change
 * to the roster.
 */
@Entity
@Table(name = "circular")
public class Circular {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "requires_acknowledgement", nullable = false)
    private boolean requiresAcknowledgement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CircularStatus status = CircularStatus.DRAFT;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Circular() {
        // for JPA
    }

    public Circular(String title, String body, boolean requiresAcknowledgement, UUID createdBy) {
        this.title = title;
        this.body = body;
        this.requiresAcknowledgement = requiresAcknowledgement;
        this.createdBy = createdBy;
    }

    /** Refuses a second publish ({@link CommunicationErrorCode#CIRCULAR_ALREADY_PUBLISHED}). */
    public void publish(UUID publishedBy) {
        if (status != CircularStatus.DRAFT) {
            throw new ChalkbaseException(CommunicationErrorCode.CIRCULAR_ALREADY_PUBLISHED);
        }
        this.status = CircularStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDraft() {
        return status == CircularStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isRequiresAcknowledgement() {
        return requiresAcknowledgement;
    }

    public CircularStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
