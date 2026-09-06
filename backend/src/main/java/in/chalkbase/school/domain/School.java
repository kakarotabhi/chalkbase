package in.chalkbase.school.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
// The registry lives in `public`, not in any school's schema, so it is qualified explicitly
// rather than resolved through search_path (ADR-0011).
@Table(name = "school", schema = "public")
public class School {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** The PostgreSQL schema holding this school's data. Immutable once the schema exists. */
    @Column(name = "schema_name", nullable = false, unique = true, length = 63)
    private String schemaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Board board;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected School() {
        // for JPA
    }

    public School(String code, String name, String schemaName, Board board, String city, String state) {
        this.code = code;
        this.name = name;
        this.schemaName = schemaName;
        this.board = board;
        this.city = city;
        this.state = state;
    }

    /**
     * Updates the registry's copy of the display details, which the school profile owns.
     *
     * <p>{@code code} and {@code schemaName} are deliberately not arguments: they address the
     * tenant and name its PostgreSQL schema, so changing either would orphan every row this school
     * has. They are set once, at onboarding, and there is no method here that can change them.
     *
     * <p>The duplication with {@code school_profile} is intentional. The registry is read with no
     * tenant bound — by the migration orchestrator at startup, and by the platform's school list —
     * so it keeps enough to describe a school without opening that school's schema. The profile is
     * authoritative; this keeps the copy honest.
     */
    public void updateRegistryDetails(String name, Board board, String city, String state) {
        this.name = name;
        this.board = board;
        this.city = city;
        this.state = state;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public Board getBoard() {
        return board;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
