package in.chalkbase.academics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A division of a class — A, B, Blue.
 *
 * <p>A name that means something only inside its class: every class has an "A", and they are
 * different rooms. {@code uq_section_name_in_class} is what says so.
 *
 * <p>Structural rather than per session (ADR-0019), and deactivated rather than deleted once
 * anything references it. Nothing here remembers a section that never existed, which is the honest
 * cost of the structural shape and the reason {@code active} is on the table from the start.
 */
@Entity
@Table(name = "section")
public class Section {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false, updatable = false)
    private SchoolClass schoolClass;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Section() {
        // for JPA
    }

    public Section(SchoolClass schoolClass, String name) {
        this.schoolClass = schoolClass;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    /** Deactivated, never deleted (ADR-0019). */
    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    /**
     * The class this section divides. Fixed at creation: moving "A" from Class 5 to Class 6 is not
     * an edit to a section, it is a different section, and treating it as an edit would silently
     * carry every enrolment that names it across.
     */
    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
