package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A person responsible for one or more students — a father, a mother, a local guardian.
 *
 * <p><strong>A person record, not an account</strong> (ADR-0017 §4). Most parents never sign in, and
 * creating a dormant account for every guardian would put thousands of rows that can never
 * authenticate into the identity tables. An account is created only when a guardian actually needs
 * one; the parent who is also a teacher is then two records for one human, deliberately, because
 * those are two different relationships with the school with two different lifecycles.
 *
 * <p><strong>Shared between siblings, never copied per child</strong> (ADR-0020 §5). Four children
 * of one father link to this one row, so correcting his phone number once corrects it for all four.
 * The trap that avoids is worth naming: with a guardian copied per student, a school that fixes one
 * child's record leaves the other three holding a number that no longer answers, and nothing in the
 * system knows they disagree. {@link StudentGuardianLink} is what carries the relationship, because
 * one person is "father" to one child and something else entirely to another.
 *
 * <p>Every field here is Confidential under ADR-0014 — a name, a phone number, an email address.
 * None may be logged, appear in an error message, or be passed to an audit method as a value.
 * {@code toString} is not overridden, so an accidental interpolation prints a hash.
 *
 * <p>Never deleted. Unlinking a guardian from a child removes the link and leaves the person, which
 * is what {@link StudentGuardianLink} exists to make possible.
 */
@Entity
@Table(name = "guardian")
public class Guardian {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Confidential. One field, for the reason {@link Student#getFullName()} is one field. */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /**
     * Confidential, and the single most useful column in this table: it is what the school rings.
     * Nullable, because a guardian record entered from a paper form sometimes has only a name.
     */
    @Column(length = 20)
    private String phone;

    @Column(length = 320)
    private String email;

    /**
     * Confidential but not Restricted. Occupation is asked for on every admission form and is not
     * one of ADR-0014's Restricted categories — income is, and is deliberately not modelled here.
     */
    @Column(length = 120)
    private String occupation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Guardian() {
        // for JPA
    }

    public Guardian(String fullName, String phone, String email, String occupation) {
        apply(fullName, phone, email, occupation);
    }

    /** Overwrites every editable field, as a whole form, for the reason {@link Student#apply} does. */
    public final void apply(String fullName, String phone, String email, String occupation) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.occupation = occupation;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getOccupation() {
        return occupation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
