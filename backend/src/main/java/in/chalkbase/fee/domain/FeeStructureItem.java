package in.chalkbase.fee.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One {@link FeeHead}'s amount and collection {@link InstallmentFrequency} within one version of a
 * {@link FeeStructure}, split across whatever {@link FeeInstallment} due dates the school gave it.
 *
 * <p>Never updated on its own. There is no {@code apply} method here and there is not meant to be
 * one: changing an item's amount means writing a whole new {@link FeeStructure} version with the
 * complete new set of items, the same way academics reorders its whole ladder in one transaction
 * rather than moving one class at a time — see that class's Javadoc for the same shape of decision.
 */
@Entity
@Table(name = "fee_structure_item")
public class FeeStructureItem {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_id", nullable = false)
    private FeeStructure structure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_head_id", nullable = false)
    private FeeHead feeHead;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentFrequency frequency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @OrderBy("dueDate asc")
    private List<FeeInstallment> installments = new ArrayList<>();

    protected FeeStructureItem() {
        // for JPA
    }

    public FeeStructureItem(
            FeeStructure structure, FeeHead feeHead, BigDecimal amount, InstallmentFrequency frequency) {
        this.structure = structure;
        this.feeHead = feeHead;
        this.amount = amount;
        this.frequency = frequency;
    }

    /** Attaches an installment to this item. Called only while the item is being built, before it is saved. */
    public void addInstallment(FeeInstallment installment) {
        installments.add(installment);
    }

    public UUID getId() {
        return id;
    }

    public FeeHead getFeeHead() {
        return feeHead;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public InstallmentFrequency getFrequency() {
        return frequency;
    }

    public List<FeeInstallment> getInstallments() {
        return Collections.unmodifiableList(installments);
    }
}
