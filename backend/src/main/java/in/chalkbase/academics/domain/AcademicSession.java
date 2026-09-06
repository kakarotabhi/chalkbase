package in.chalkbase.academics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * An academic year for one school — April to March in most Indian schools.
 *
 * <p>The time axis the rest of the academic model hangs off: an enrolment names a session, and roll
 * numbers are unique per class, section and session. The class and the section themselves are not
 * per-session (ADR-0019), which is why this is one small table rather than a dimension every
 * academic row has to carry.
 *
 * <p>Deliberately unqualified: this lives in the school's own schema and is reached through
 * {@code search_path}. It carries no {@code school_id}, because the schema is the tenant boundary
 * (ADR-0011).
 */
@Entity
@Table(name = "academic_session")
public class AcademicSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    /**
     * At most one row per school has this set, and {@code uq_academic_session_one_current} — a
     * partial unique index — is what says so. That index is <em>not</em> deferrable (an index
     * cannot be), so anything that moves "current" from one session to another has to clear the old
     * one and flush before setting the new one.
     */
    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AcademicSession() {
        // for JPA
    }

    public AcademicSession(String name, LocalDate startsOn, LocalDate endsOn) {
        apply(name, startsOn, endsOn);
    }

    /**
     * Overwrites the editable fields.
     *
     * <p>{@code current} is not among them on purpose: which session a school is <em>in</em> is a
     * different decision from what a session is called and when it runs, it is mutually exclusive
     * across rows, and giving an edit form the power to set it would let two forms saved a second
     * apart disagree about which year the school is in.
     */
    public final void apply(String name, LocalDate startsOn, LocalDate endsOn) {
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /** Only ever called after every other session has been cleared and that clear has been flushed. */
    public void becomeCurrent() {
        this.current = true;
    }

    public void stopBeingCurrent() {
        this.current = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public boolean isCurrent() {
        return current;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
