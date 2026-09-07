package in.chalkbase.platform.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The JPA view of {@code public.state}.
 *
 * <p>Lives in {@code public}, not any school's schema, so it is qualified explicitly rather than
 * resolved through {@code search_path} — the same reason {@code school.domain.School} does this
 * (ADR-0011). A query against this entity reaches {@code public.state} regardless of which tenant
 * schema the connection is currently bound to.
 *
 * <p>No setters and no other constructor: nothing in this application ever creates, edits or
 * deletes a row through JPA. Rows exist only as {@link ReferenceDataSeeder} puts them there with
 * plain SQL, and this entity exists only so {@link ReferenceDataService} can read them back.
 */
@Entity
@Table(name = "state", schema = "public")
public class State {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected State() {}

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
