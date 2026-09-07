package in.chalkbase.academics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A subject this school teaches — English, Mathematics, Environmental Science.
 *
 * <p>A flat catalogue, unlike {@link SchoolClass}: a subject has no natural order the way a class
 * ladder does — "English" does not come before "Mathematics" the way Class 5 comes before Class
 * 6 — so there is no {@code sequence} here, and the list is read alphabetically instead.
 *
 * <p><strong>No relation to a class or a section.</strong> Which classes teach which subjects is a
 * subject allocation, which belongs to the timetable and marks modules (FR-041, FR-053) once they
 * exist. This row is a fact about the school, not about a class, and nothing here points at
 * {@code student} either — wiring a subject to marks or to a student is a later phase's decision to
 * make, not this one's.
 *
 * <p>Never deleted. {@code active} is on the table from its first migration rather than
 * retrofitted once something references it, for the same reason {@link SchoolClass} gives
 * (ADR-0019): nothing references a subject yet in this build, but the timetable and marks modules
 * that come next will, and by the time one does it is too late to decide that deleting it was
 * wrong.
 */
@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    /**
     * The school's own short form — "MATH", "SST". Not normalised to upper case: a school that
     * types "Eng" is entitled to see "Eng" back, exactly as a class's name is never reshaped.
     */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Subject() {
        // for JPA
    }

    public Subject(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public void rename(String name, String code) {
        this.name = name;
        this.code = code;
    }

    /** Deactivated, never deleted (ADR-0019). A mistake is fixable by editing the name or code. */
    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
