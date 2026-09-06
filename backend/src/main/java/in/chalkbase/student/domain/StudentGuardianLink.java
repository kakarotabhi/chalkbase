package in.chalkbase.student.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One child and one guardian, and what they are to each other.
 *
 * <p>{@code StudentGuardianLink} rather than {@code StudentGuardian}, because the name
 * {@code StudentGuardian} belongs to the record this is returned as — and a DTO and an entity with
 * the same simple name in one module is a confusion repeated at every import. The table is
 * {@code student_guardian}.
 *
 * <p>The relationship lives here, on the pair, not on {@link Guardian} (ADR-0020 §5). One person is
 * "father" to one child and "local guardian" to another; putting {@code relation} on the person
 * would force a second copy of them, which is the exact shape this module exists to avoid.
 *
 * <p><strong>{@link #isPrimary()} is exclusive per student, and the database says so</strong> —
 * {@code uq_student_guardian_one_primary} is a partial unique index over {@code student_id} where
 * {@code is_primary}. An index cannot be deferred, so anything that moves "primary" from one link to
 * another must clear the old one and <em>flush that clear</em> before setting the new one. This is
 * the same trap {@code AcademicSession#becomeCurrent} carries, and it fails on the second
 * reassignment rather than the first, which is not a thing to discover in production.
 */
@Entity
@Table(name = "student_guardian")
public class StudentGuardianLink {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Fixed at creation. Moving a link from one child to another is not an edit — it is a link to
     * delete and a link to create, and treating it as an edit would silently reassign a parent.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    /**
     * Fixed at creation, for the same reason. Correcting which person a child's father actually is
     * means unlinking the wrong one, which is what the module's single {@code DELETE} is for.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false, updatable = false)
    private Guardian guardian;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuardianRelation relation;

    /** Who the school rings first. At most one per student — see the class javadoc. */
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected StudentGuardianLink() {
        // for JPA
    }

    public StudentGuardianLink(Student student, Guardian guardian, GuardianRelation relation, boolean primary) {
        this.student = student;
        this.guardian = guardian;
        this.relation = relation;
        this.primary = primary;
    }

    public void setRelation(GuardianRelation relation) {
        this.relation = relation;
    }

    /**
     * Only ever set to true after every other link of the same student has been cleared and that
     * clear has been flushed. See the class javadoc: the index is partial and cannot be deferred.
     */
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public GuardianRelation getRelation() {
        return relation;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
