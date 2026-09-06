package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One child on this school's rolls.
 *
 * <p><strong>Every field on this entity is Confidential under ADR-0014.</strong> The name, the date
 * of birth and the admission number each identify a child; none of them may be logged at any level,
 * appear in an error message, or be passed to any audit method. {@code toString} is deliberately not
 * overridden — the JDK default prints the class and a hash, which is the only safe thing an
 * accidental string interpolation can produce.
 *
 * <p>{@link #getFullName()} is <strong>one field, not three</strong> (ADR-0020 §1). A great many
 * Indian students have no surname at all; many have a single name; in much of South India an
 * initial expands to a village or a father's name and is not a family name in any sense the
 * three-field shape means. A required "Last name" box makes the office clerk invent one, and what
 * they invent goes on the certificate. It also matches the boards: CBSE records a single "Candidate
 * Name" and takes the parents' names separately — which here come from the guardian records.
 *
 * <p>The cost is honest: sorting by surname is not available, and a class list sorts by the whole
 * name. A school that wants a different order gets an additive {@code sort_name} column later.
 *
 * <p>Deliberately unqualified and carrying no {@code school_id}: the schema is the tenant boundary
 * (ADR-0011). {@code uq_student_admission_number} is therefore unique <em>within one school</em>,
 * which is the only scope a schema can enforce and a decision rather than a limitation (ADR-0020
 * §3) — a group spanning campuses spans schemas, so group-wide uniqueness could never be a database
 * constraint at all.
 *
 * <p>Never deleted (ADR-0020 §6). {@link StudentStatus} is what a child leaving looks like.
 */
@Entity
@Table(name = "student")
public class Student {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Confidential: it identifies one child. Never logged, never in an error message, never audited as a value. */
    @Column(name = "admission_number", nullable = false, length = 40)
    private String admissionNumber;

    /** Confidential. One field, exactly as the boards will hold the school to it (ADR-0020 §1). */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** Confidential. */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudentStatus status = StudentStatus.ACTIVE;

    /** Nullable: a record migrated from a paper register often has no reliable admission date. */
    @Column(name = "admitted_on")
    private LocalDate admittedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Student() {
        // for JPA
    }

    public Student(
            String admissionNumber,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            StudentStatus status,
            LocalDate admittedOn) {
        apply(admissionNumber, fullName, dateOfBirth, gender, status, admittedOn);
    }

    /**
     * Overwrites every editable field.
     *
     * <p>One method rather than six setters, as {@code SchoolProfile} does: the record is edited as
     * a whole form, and a partial update would let a screen that forgot a field silently blank it —
     * here, a child's date of birth.
     */
    public final void apply(
            String admissionNumber,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            StudentStatus status,
            LocalDate admittedOn) {
        this.admissionNumber = admissionNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.status = status;
        this.admittedOn = admittedOn;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public LocalDate getAdmittedOn() {
        return admittedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
