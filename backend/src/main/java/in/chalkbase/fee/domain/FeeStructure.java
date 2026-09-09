package in.chalkbase.fee.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One version of what a class is charged in one academic session (ADR-0012 rule 6, ADR-0033).
 *
 * <p><strong>Never updated, from its very first migration.</strong> "Editing" a school's fee
 * structure is {@link in.chalkbase.fee.application.FeeStructureService#save} writing a brand new
 * row — the next {@link #version} for the same {@code (academicSessionId, schoolClassId)} — and
 * marking this one {@link #supersede() superseded} in the same transaction. There is no setter for
 * {@link #academicSessionId}, {@link #schoolClassId}, {@link #version} or any item's amount,
 * because none of them may change once written; only which row is the live one for its class and
 * session can move, and that is exactly what {@link #superseded} tracks.
 *
 * <p>This is stronger than ADR-0012's own letter, which names {@code fee_charge} and
 * {@code fee_ledger_entry} as the append-only rows and says only that structures are
 * <em>session-scoped</em>. Making the structure itself version-immutable, rather than merely
 * "replaced wholesale at the start of a new session", is this module's own decision — see
 * ADR-0033 for the argument: an editable "current version" row is exactly the option-1 shape
 * ADR-0012 rejected for the ledger, reappearing one layer up where nothing was watching for it,
 * and it is also what lets a future {@code fee_charge} pin to "structure version 3" and mean it,
 * per ADR-0012's own diagram.
 *
 * <p>{@code academicSessionId} and {@code schoolClassId} are plain {@link UUID}s with a database
 * foreign key, not JPA associations to {@code academics.domain} — this module reaches
 * {@code academics} only through {@code academics.api.AcademicsLookup}, never a join.
 */
@Entity
@Table(name = "fee_structure")
public class FeeStructure {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "academic_session_id", nullable = false)
    private UUID academicSessionId;

    @Column(name = "school_class_id", nullable = false)
    private UUID schoolClassId;

    @Column(nullable = false)
    private int version;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * Owned entirely by this structure version: an item is never moved between versions, and
     * cascading persist is what lets {@link in.chalkbase.fee.application.FeeStructureService#save}
     * build a whole new version — items and their installments — in one {@code saveAndFlush}.
     */
    @OneToMany(mappedBy = "structure", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<FeeStructureItem> items = new ArrayList<>();

    protected FeeStructure() {
        // for JPA
    }

    public FeeStructure(UUID academicSessionId, UUID schoolClassId, int version, UUID createdBy) {
        this.academicSessionId = academicSessionId;
        this.schoolClassId = schoolClassId;
        this.version = version;
        this.createdBy = createdBy;
    }

    /** Attaches an item to this version. Called only while the version is being built, before it is saved. */
    public void addItem(FeeStructureItem item) {
        items.add(item);
    }

    /** Marks this version no longer the live one for its class and session. Never called a second time on the same row. */
    public void supersede() {
        this.supersededAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAcademicSessionId() {
        return academicSessionId;
    }

    public UUID getSchoolClassId() {
        return schoolClassId;
    }

    public int getVersion() {
        return version;
    }

    public boolean isCurrent() {
        return supersededAt == null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<FeeStructureItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
