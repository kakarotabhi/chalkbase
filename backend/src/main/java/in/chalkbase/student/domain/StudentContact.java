package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's own address, phone and email (FR-028).
 *
 * <p><strong>Confidential, not Restricted</strong> (ADR-0014) — the same tier as a guardian's own
 * contact details. Nothing here is encrypted or masked; it is permission-gated like the rest of the
 * student record and never logged, at any level.
 *
 * <p>Keyed by the student's own id rather than a generated one of its own: this is a one-to-one
 * section of the student record, not a list, and a row exists only once a school has entered
 * something for it (ADR-0020's "additive" pattern, one level down).
 */
@Entity
@Table(name = "student_contact")
public class StudentContact {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    /** Confidential. Free text: an Indian address rarely fits a fixed set of lines. */
    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentContact() {
        // for JPA
    }

    public StudentContact(UUID studentId, String address, String phone, String email) {
        this.studentId = studentId;
        apply(address, phone, email);
    }

    /** Overwrites every editable field, as a whole form — see {@link Student#apply}. */
    public final void apply(String address, String phone, String email) {
        this.address = address;
        this.phone = phone;
        this.email = email;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
