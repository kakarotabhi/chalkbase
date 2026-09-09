package in.chalkbase.fee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A named thing this school charges for — "Tuition Fee", "Annual Day Fee" — one of the seven
 * {@link FeeHeadCategory} kinds Phase 0 confirmed.
 *
 * <p>A catalogue, like {@code academics.domain.Subject}: not per session, reused by every
 * {@link FeeStructureItem} that names it. Deactivated, never deleted, once a structure has used it
 * — a class ladder's own reasoning (ADR-0019) applies here for the same reason: by the time a
 * structure names a head, deleting it would leave that structure pointing at nothing.
 */
@Entity
@Table(name = "fee_head")
public class FeeHead {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeeHeadCategory category;

    /** Only ever set when {@link #category} is {@link FeeHeadCategory#ANNUAL_DEVELOPMENT} — see {@link #capPercentOfTuition}. */
    @Column(name = "cap_percent_of_tuition", precision = 5, scale = 2)
    private BigDecimal capPercentOfTuition;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FeeHead() {
        // for JPA
    }

    public FeeHead(String name, FeeHeadCategory category, BigDecimal capPercentOfTuition) {
        this.name = name;
        apply(name, category, capPercentOfTuition);
    }

    /**
     * Overwrites the editable fields.
     *
     * <p>{@code category} may change — a head misclassified at creation is fixed here rather than
     * by deleting and recreating it, which would orphan any structure item that already names it.
     * The service layer, not the entity, is what refuses a cap on a non-development category
     * ({@code FeeErrorCode#CAP_PERCENT_NOT_APPLICABLE}) — the same split {@code AcademicSession}
     * draws between "what this object may hold" and "what a caller may ask for".
     */
    public final void apply(String name, FeeHeadCategory category, BigDecimal capPercentOfTuition) {
        this.name = name;
        this.category = category;
        this.capPercentOfTuition = capPercentOfTuition;
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

    public FeeHeadCategory getCategory() {
        return category;
    }

    /**
     * Delhi's DoE caps Development Fee as a percentage of tuition (07-phase-0-decisions.md §2).
     * Null for a head with no cap configured — most heads, and most schools until their state
     * requires one; the percentage itself is set by the school, never hardcoded to 15%.
     */
    public BigDecimal getCapPercentOfTuition() {
        return capPercentOfTuition;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
