package in.chalkbase.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One school's own bundle of permissions (ADR-0005).
 *
 * <p>Roles are data. This row was created by copying a {@link RoleTemplate} at onboarding and is
 * the school's from that moment: renaming it, adding a permission or deleting it are all ordinary
 * edits that need no release. {@link #getTemplateCode()} records only which template it came from,
 * which is why it is not a foreign key — provenance, never a live link.
 *
 * <p>Lives in the school's own schema, so there is no {@code school_id} column (ADR-0011).
 *
 * <p>The permission codes are an {@code @ElementCollection} rather than an entity: a row in
 * {@code role_permission} has no identity of its own and nothing ever refers to one.
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "template_code", length = 40)
    private String templateCode;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false, length = 80)
    private Set<String> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Role() {
        // for JPA
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

    public String getDescription() {
        return description;
    }

    /** The shipped template this was copied from, or null for a role the school invented. */
    public String getTemplateCode() {
        return templateCode;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
