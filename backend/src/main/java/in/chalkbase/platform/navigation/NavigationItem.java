package in.chalkbase.platform.navigation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * One entry in the menu, declared in code by the module that owns the screen behind it (ADR-0008).
 *
 * <p><strong>Note what is not here: no URL, no path, no route, no component name, and no label
 * sentence.</strong> That is the whole point of ADR-0008 and it is enforced by this record simply
 * not having such a field. The server decides <em>which items exist for this user</em> and their
 * order and nesting; the frontend decides what a route is and how anything is drawn. If a URL were
 * sent from here, a backend deploy could break navigation by naming a route the frontend does not
 * have, and pixel decisions would start living in Java.
 *
 * <p>The contract with the frontend is therefore the {@link #id()}: it maps ids to its own lazy
 * routes and drops (with a log line) any id it does not know. Renaming an id is a breaking change
 * made in the same pull request on both sides.
 *
 * @param id stable, dotted, lower case — {@code fees}, {@code fees.collect}. A child's id must
 *     begin with its parent's id and a dot, so the tree is legible from any single id.
 * @param labelKey a translation key, never a display string. Parent portals need Hindi and regional
 *     languages, and sending sentences from here would drag every translation into Java. A school's
 *     own renaming ("Fees &amp; Dues") is Tier-2 configuration and will arrive later as a separate,
 *     genuinely per-school override — not as a hardcoded label.
 * @param icon a name from the frontend's own icon set, or null. The server says <em>which</em>
 *     icon, never how it is drawn; an unknown name is the frontend's to ignore.
 * @param order sort position among siblings. Ties are broken by {@code id} so the tree is stable.
 * @param requiredPermission the permission that gates this item, or null for an item everyone with
 *     a session sees. <strong>This is a convenience, never an authorization control</strong>
 *     (ADR-0008): the endpoint behind the screen enforces its own permission, and that enforcement
 *     is what protects data.
 * @param children the sub-menu, empty for a leaf
 */
public record NavigationItem(
        String id,
        String labelKey,
        String icon,
        int order,
        /*
         * Not serialised to the client. Every item that survives filtering is, by definition, one
         * whose permission the caller holds, so the field would carry no information the caller
         * does not already have from `permissions` — and shipping it invites a frontend to
         * re-derive "who may see what" from the tree, which is the exact duplication of the
         * authorization model that ADR-0008 exists to prevent.
         */
        @JsonIgnore String requiredPermission,
        List<NavigationItem> children) {

    public NavigationItem {
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** A leaf: an item with no sub-menu. */
    public NavigationItem(String id, String labelKey, String icon, int order, String requiredPermission) {
        this(id, labelKey, icon, order, requiredPermission, List.of());
    }

    /** True when this item was declared as a container, so an empty sub-menu makes it pointless. */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
