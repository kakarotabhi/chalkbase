package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A student in a section, for one academic year.
 *
 * <p>This is where the year lives (ADR-0019, ADR-0020 §4). Classes and sections are structural —
 * one "Class 5" row, not one per year — so the session has to appear on whatever references them,
 * and it appears here. That makes <strong>promotion a new row rather than an edit</strong>: next
 * year's enrolment does not overwrite this year's, so a student's history is readable without
 * consulting the audit log.
 *
 * <p><strong>{@link #getAcademicSessionId()} and {@link #getSectionId()} are plain UUIDs, not
 * associations, and that is deliberate.</strong> Those rows belong to {@code academics}, and mapping
 * them as {@code @ManyToOne} would put an {@code academics.domain} type inside this module's
 * entities — a boundary crossing {@code ModularityTests} refuses, and a join the module map forbids.
 * The names behind these ids are resolved through {@code academics.api.AcademicsLookup}. The
 * foreign keys are still in the database, where they belong; what is absent is the Java coupling.
 *
 * <p>At most one <em>active</em> enrolment per student per session, enforced by
 * {@code uq_student_enrolment_one_active} — a partial unique index, because a student may
 * legitimately have an ended enrolment in the same year (moved section mid-term) and only the live
 * one is exclusive.
 *
 * <p>{@link #getRollNumber()} is nullable on purpose: it is assigned after admission and often after
 * the class list settles. It is unique per {@code (session, section, roll_number)}, which is the
 * requirement's "per class-section-session" said with one fewer column, since a section belongs to
 * exactly one class.
 */
@Entity
@Table(name = "student_enrolment")
public class StudentEnrolment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    /**
     * The academic year, fixed at creation. Changing it is not an edit to an enrolment — a student
     * in Class 5 last year and Class 6 this year is two rows, and rewriting the year on one of them
     * would erase the history the shape exists to keep.
     */
    @Column(name = "academic_session_id", nullable = false, updatable = false)
    private UUID academicSessionId;

    /** Editable: a student genuinely moves from 5A to 5B mid-year, and that is the same enrolment. */
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Set here rather than left to the column default. The column has
     * {@code default current_date}, but Hibernate names every mapped column in its INSERT, so a
     * field left null would be written as null against a {@code not null} column and the default
     * would never apply.
     */
    @Column(name = "enrolled_on", nullable = false)
    private LocalDate enrolledOn = LocalDate.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected StudentEnrolment() {
        // for JPA
    }

    public StudentEnrolment(Student student, UUID academicSessionId, UUID sectionId, String rollNumber) {
        this.student = student;
        this.academicSessionId = academicSessionId;
        this.sectionId = sectionId;
        this.rollNumber = rollNumber;
    }

    public void moveTo(UUID sectionId) {
        this.sectionId = sectionId;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    /**
     * Ends this enrolment, or brings it back.
     *
     * <p>Only ever set to true when the student has no other active enrolment in the same session:
     * {@code uq_student_enrolment_one_active} refuses anything else, and the service checks first so
     * the school is told what happened rather than shown a bare conflict.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public UUID getAcademicSessionId() {
        return academicSessionId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getEnrolledOn() {
        return enrolledOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
