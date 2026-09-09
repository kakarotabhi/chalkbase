package in.chalkbase.fee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One due date within a {@link FeeStructureItem}, and the amount due on it (FR-075: "installments,
 * due dates").
 *
 * <p>Never updated. It is written once, when its owning {@link FeeStructure} version is written,
 * and stays exactly as it was for the same reason the structure itself does not change in place —
 * see that class's Javadoc.
 */
@Entity
@Table(name = "fee_installment")
public class FeeInstallment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_item_id", nullable = false)
    private FeeStructureItem item;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FeeInstallment() {
        // for JPA
    }

    public FeeInstallment(FeeStructureItem item, LocalDate dueDate, BigDecimal amount) {
        this.item = item;
        this.dueDate = dueDate;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
