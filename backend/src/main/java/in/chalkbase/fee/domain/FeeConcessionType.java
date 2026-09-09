package in.chalkbase.fee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A kind of waiver this school offers — the catalogue FR-075 asks for ("the system shall define ...
 * waivers"), never a grant to any student. See {@code in.chalkbase.fee.package-info} for why
 * granting one is out of this lane's scope.
 *
 * <p>Deactivated, never deleted, for the same reason {@link FeeHead} is: a concession already
 * recorded against a charge — once fee_demand exists to record one — must not point at nothing.
 */
@Entity
@Table(name = "fee_concession_type")
public class FeeConcessionType {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeeConcessionCategory category;

    @Column(length = 300)
    private String description;

    /** ADR-0012 rule 5: a concession is money given away and requires approval, by default. */
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FeeConcessionType() {
        // for JPA
    }

    public FeeConcessionType(
            String name, FeeConcessionCategory category, String description, boolean requiresApproval) {
        apply(name, category, description, requiresApproval);
    }

    public final void apply(String name, FeeConcessionCategory category, String description, boolean requiresApproval) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.requiresApproval = requiresApproval;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public FeeConcessionCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
