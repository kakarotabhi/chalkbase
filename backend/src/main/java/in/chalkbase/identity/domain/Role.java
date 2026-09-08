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

    /**
     * Set the moment role management replaces this role's permission set, and never cleared
     * (ADR-0031). {@code RoleTemplateInstaller} reconciles a template-derived role only while this
     * is false — the instant a school edits what a role grants, that role is the school's own and
     * a later release adding a permission to the template it was copied from must not silently
     * rewrite it.
     */
    @Column(name = "customised", nullable = false)
    private boolean customised;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false, length = 80)
    private Set<String> permissions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Role() {
        // for JPA
    }

    /**
     * A school-created role — {@code templateCode} is null, because nothing shipped this; it was
     * typed into a form. {@code RoleTemplateInstaller} is the only other place a {@link Role} row is
     * created, and it goes straight to SQL rather than through this constructor because it runs
     * before the entity manager exists.
     *
     * <p>{@code customised} starts true: nothing shipped this role, so there is no template
     * reconciliation could ever safely apply to it. It happens to be moot for
     * {@code RoleTemplateInstaller} today — a school-created code cannot collide with a template's,
     * see {@code RoleCode} — but the row should read as the school's own from the moment it exists
     * rather than depend on that being true forever.
     */
    public Role(String code, String name, String description, Set<String> permissions) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.permissions = new LinkedHashSet<>(permissions);
        this.customised = true;
    }

    /**
     * Wholesale replacement, not a delta — the caller has already computed what should remain.
     *
     * <p>Also marks this role {@link #isCustomised() customised}, permanently. This is the one
     * mutator role management uses to change what a role grants (ADR-0031), so it is the one place
     * that needs to say so: from this call onward, {@code RoleTemplateInstaller} must never again
     * add a template permission this role does not have, because the school may have removed it on
     * purpose.
     */
    public void replacePermissions(Set<String> permissions) {
        this.permissions = new LinkedHashSet<>(permissions);
        this.customised = true;
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

    /** Whether role management has ever replaced this role's permission set. See {@link #replacePermissions}. */
    public boolean isCustomised() {
        return customised;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
