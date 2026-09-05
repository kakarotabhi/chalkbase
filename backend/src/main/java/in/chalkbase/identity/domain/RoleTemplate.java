package in.chalkbase.identity.domain;

import java.util.List;
import java.util.Set;

/**
 * One shipped role, versioned with the product (ADR-0005).
 *
 * <p>A template is never what a school uses. Onboarding <strong>copies</strong> it into the
 * school's own {@code role} and {@code role_permission} rows, recording {@link #code()} as
 * provenance, and the copy is then the school's to edit. If a school's role pointed back at the
 * template, adding a permission to that template in a release would silently widen access at every
 * school at once — a security incident delivered by an upgrade.
 *
 * @param code the stable template identifier, e.g. {@code PRINCIPAL}. Also the initial code of the
 *     copy, and what {@code role.template_code} records.
 * @param name what a school sees before it renames it
 * @param description one sentence for the administrator choosing between templates
 * @param permissions the permission codes this role starts with, each of which must exist in the
 *     {@code PermissionCatalog} of this build
 */
public record RoleTemplate(String code, String name, String description, Set<String> permissions) {

    public RoleTemplate {
        permissions = Set.copyOf(permissions);
    }

    public RoleTemplate(String code, String name, String description, String... permissions) {
        this(code, name, description, Set.of(permissions));
    }

    /** Sorted, so a copy is written in the same order every time and a diff of two schools is readable. */
    public List<String> sortedPermissions() {
        return permissions.stream().sorted().toList();
    }
}
