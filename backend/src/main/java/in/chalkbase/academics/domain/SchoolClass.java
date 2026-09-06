package in.chalkbase.academics.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One rung of this school's ladder — Nursery, LKG, Class 5.
 *
 * <p>{@code SchoolClass}, not {@code Class}, and the table is {@code school_class} to match:
 * {@code class} is a Java keyword, so the entity had to be called something else whatever the table
 * was named, and a table whose name does not match its entity is a small confusion repeated forever
 * (ADR-0019). The same reasoning made {@code user} into {@code user_account}.
 *
 * <p>Structural, not per session. A school has one Class 5 row, not one per academic year; the
 * session appears on the enrolment that puts a student in it.
 *
 * <p>Never deleted. {@code active} is on the table from its first migration rather than retrofitted
 * once something references it, because by the time a student's enrolment names a class, deciding
 * that deleting it was a mistake is too late.
 */
@Entity
@Table(name = "school_class")
public class SchoolClass {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String name;

    /**
     * Where this rung sits in the ladder. Unique, so "which comes first" always has an answer, and
     * {@code uq_school_class_sequence} is {@code deferrable initially deferred} so that reordering
     * the whole ladder is one transaction rather than a dance through temporary values.
     */
    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Read-only from here: sections are created and edited through {@code SectionRepository}, and
     * this side exists so one query can fetch a class with its sections rather than one query per
     * class. {@code @OrderBy} makes the database do the sorting the contract promises.
     */
    @OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("name asc")
    private List<Section> sections = new ArrayList<>();

    protected SchoolClass() {
        // for JPA
    }

    public SchoolClass(String name, int sequence) {
        this.name = name;
        this.sequence = sequence;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** Deactivated, never deleted (ADR-0019). A mistake is fixable by renaming. */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Moves this rung to a position in the ladder.
     *
     * <p>Package-visible from the outside only through the reorder operation, which assigns every
     * class a position in one transaction. Moving one class on its own would collide with whichever
     * class already holds that position, which is why creating a class appends rather than inserts.
     */
    public void moveTo(int sequence) {
        this.sequence = sequence;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSequence() {
        return sequence;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }
}
