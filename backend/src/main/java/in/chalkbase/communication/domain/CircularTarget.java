package in.chalkbase.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One class or section a circular was addressed to.
 *
 * <p>{@code classId} is always set; {@code sectionId} is null for "every active section of this
 * class" and set for one specific section. Both are plain UUIDs with a database foreign key, not
 * JPA associations to {@code academics.domain} — this module has no import of {@code academics} at
 * all, the same shape {@code attendance_mark} uses for its own section and session ids.
 *
 * <p>Rows survive publishing unchanged: this is "what we meant to send this to", and it stays
 * correct even if the roster changes afterwards. Who it actually reached is
 * {@link CircularRecipient}, resolved once, at publish time.
 */
@Entity
@Table(name = "circular_target")
public class CircularTarget {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "circular_id", nullable = false, updatable = false)
    private UUID circularId;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "section_id", updatable = false)
    private UUID sectionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CircularTarget() {
        // for JPA
    }

    public CircularTarget(UUID circularId, UUID classId, UUID sectionId) {
        this.circularId = circularId;
        this.classId = classId;
        this.sectionId = sectionId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCircularId() {
        return circularId;
    }

    public UUID getClassId() {
        return classId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
