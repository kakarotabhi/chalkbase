package in.chalkbase.platform.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Every navigation entry the running build knows about, collected from the modules at startup, and
 * the one place a menu is cut down to what one user may see (ADR-0008).
 *
 * <p>The shape deliberately mirrors {@code platform.security.PermissionCatalog}: collect from
 * providers, validate once at startup rather than at the first request, and hold the result sorted
 * so a log line, a test and a client all read it the same way. A malformed or duplicated id stops
 * the application instead of reaching a browser — an id is the contract with the frontend's route
 * map, so a typo produces a menu item that silently does nothing.
 *
 * <p><strong>Menus are not security.</strong> Filtering here is a convenience so a user is not
 * shown a button that will 403. Every endpoint still enforces its own permission, and that
 * enforcement is what protects data.
 *
 * <h2>Contributing to another module's section</h2>
 *
 * <p>A module declares its own subtree inline, as a {@link NavigationItem} with children. To add a
 * screen to a section <em>another</em> module owns, declare it at the top level under its dotted
 * id — {@code settings.profile} — and the catalogue places it beneath {@code settings}. The id
 * already says where an item belongs, so nothing else has to.
 *
 * <p>This is why the alternative was rejected: without it, the school module's profile screen
 * would have to be declared inside {@code IdentityNavigation}, and {@code settings} would become
 * the one file every module edits to add its own menu entry — the coupling {@link
 * NavigationProvider} exists to avoid, and a boundary violation besides. A contribution whose
 * parent no module declares stops the application at startup rather than silently appearing as a
 * root item.
 *
 * <p>A <em>container</em> — a section with no screen of its own, dropped when nothing inside it
 * survives filtering — is one declared with at least one inline child. A section that declared no
 * child of its own would be a menu entry pointing at nothing on the day it was written, so the
 * owning module declares the first child and others contribute alongside it.
 */
@Component
public class NavigationCatalog {

    /**
     * Dotted, lower case, one or more segments: {@code fees}, {@code fees.collect}. Deliberately
     * incapable of expressing a URL — no slash, no scheme, no query. The structural half of "the
     * server never sends a URL"; the other half is that {@link NavigationItem} has no such field.
     */
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*$");

    private static final int MAX_ID_LENGTH = 80;
    private static final int MAX_LABEL_KEY_LENGTH = 120;
    private static final int MAX_ICON_LENGTH = 40;

    /** Order first, then id: two items sharing an order still come back in the same order always. */
    private static final Comparator<NavigationItem> SIBLING_ORDER =
            Comparator.comparingInt(NavigationItem::order).thenComparing(NavigationItem::id);

    private static final Logger log = LoggerFactory.getLogger(NavigationCatalog.class);

    private final List<NavigationItem> roots;

    /**
     * Ids declared <em>with</em> an inline sub-menu, captured before merging.
     *
     * <p>Recorded here rather than read back off the assembled tree because merging makes the two
     * indistinguishable: after contributions are placed, a section that owns no screen and a screen
     * that happens to have gained a child both simply have children. Only the first should vanish
     * when everything inside it is filtered away.
     */
    private final Set<String> containers;

    public NavigationCatalog(List<NavigationProvider> providers) {
        Map<String, String> declaredBy = new LinkedHashMap<>();
        Map<String, NavigationItem> byId = new LinkedHashMap<>();
        Set<String> declaredContainers = new LinkedHashSet<>();
        for (NavigationProvider provider : providers) {
            String source = provider.getClass().getName();
            for (NavigationItem item : provider.navigation()) {
                validate(item, null, source, declaredBy);
                flatten(item, byId, declaredContainers);
            }
        }
        this.containers = Set.copyOf(declaredContainers);
        this.roots = sorted(assemble(byId, declaredBy));
        log.info("Registered {} navigation item(s) from {} module registr(ies)", declaredBy.size(), providers.size());
    }

    /** The whole tree, unfiltered, in display order. What a user sees is {@link #navigationFor}. */
    public List<NavigationItem> all() {
        return roots;
    }

    /** Every id in the build, including children. Useful to a test that checks the frontend knows them. */
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        collectIds(roots, ids);
        return List.copyOf(ids);
    }

    /**
     * The menu for one user.
     *
     * <p>Takes the permission codes rather than identity's {@code EffectiveAccess}: the shared
     * kernel must not depend on a feature module, and a set of strings is all the decision needs.
     *
     * <p>Three rules, and the second and third matter more than they look:
     *
     * <ol>
     *   <li>An item is kept when it has no {@code requiredPermission}, or the user holds it.
     *   <li>An item whose {@code requiredPermission} the user lacks takes its whole subtree with
     *       it. Anything else would leak a child of a section the user cannot open.
     *   <li>An item declared <em>with</em> children that has none left is dropped as well, because
     *       a menu entry that opens an empty submenu is worse than no entry. This is applied
     *       whether or not the parent had a permission of its own: if it declared children it is a
     *       container, and an empty container is not an item.
     * </ol>
     */
    public List<NavigationItem> navigationFor(Set<String> heldPermissions) {
        Set<String> held = heldPermissions == null ? Set.of() : heldPermissions;
        return roots.stream()
                .map(item -> visible(item, held))
                .filter(Objects::nonNull)
                .toList();
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    private NavigationItem visible(NavigationItem item, Set<String> held) {
        if (item.requiredPermission() != null && !held.contains(item.requiredPermission())) {
            return null;
        }
        List<NavigationItem> children = item.children().stream()
                .map(child -> visible(child, held))
                .filter(Objects::nonNull)
                .toList();
        if (children.isEmpty()) {
            // A container has nothing of its own to open, so an empty one is not an item. A screen
            // keeps its entry: `schools` is still worth showing when the one thing contributed
            // under it is filtered away.
            //
            // Note the childless copy rather than `item` itself. Returning the declared item would
            // hand back the children that were just filtered out — the leak rule 2 exists to stop,
            // arriving through the door marked "nothing to filter".
            return containers.contains(item.id())
                    ? null
                    : new NavigationItem(
                            item.id(), item.labelKey(), item.icon(), item.order(), item.requiredPermission());
        }
        return new NavigationItem(
                item.id(), item.labelKey(), item.icon(), item.order(), item.requiredPermission(), children);
    }

    /**
     * Every declared item by id, stripped of its inline children, plus which ones were containers.
     *
     * <p>Children are dropped here and rebuilt by {@link #assemble} so an inline declaration and a
     * contribution end up in exactly one place. Keeping both would nest the same item twice.
     */
    private static void flatten(NavigationItem item, Map<String, NavigationItem> byId, Set<String> containers) {
        byId.put(
                item.id(),
                new NavigationItem(item.id(), item.labelKey(), item.icon(), item.order(), item.requiredPermission()));
        if (item.hasChildren()) {
            containers.add(item.id());
            for (NavigationItem child : item.children()) {
                flatten(child, byId, containers);
            }
        }
    }

    /**
     * Rebuilds the tree from the flat set, placing each item under the id its own id names.
     *
     * <p>An id is dotted precisely so it says where it belongs, which makes this a lookup rather
     * than a merge: {@code settings.profile} goes under {@code settings} whether the same module
     * declared both or not. A parent id is always strictly shorter than its child's, so there is no
     * cycle to guard against.
     */
    private static List<NavigationItem> assemble(Map<String, NavigationItem> byId, Map<String, String> declaredBy) {
        Map<String, List<String>> childIds = new LinkedHashMap<>();
        List<String> rootIds = new ArrayList<>();
        for (String id : byId.keySet()) {
            int lastDot = id.lastIndexOf('.');
            if (lastDot < 0) {
                rootIds.add(id);
                continue;
            }
            String parentId = id.substring(0, lastDot);
            if (!byId.containsKey(parentId)) {
                throw new IllegalStateException("Navigation item " + id + " (declared by " + declaredBy.get(id)
                        + ") belongs under \"" + parentId + "\", which no module declares."
                        + " Either the owning module is not on the classpath, or the id is a typo."
                        + " It is refused rather than shown at the top level, because a section's"
                        + " child loose among the roots is a menu that reads wrongly.");
            }
            childIds.computeIfAbsent(parentId, key -> new ArrayList<>()).add(id);
        }
        return rootIds.stream().map(id -> attach(id, byId, childIds)).toList();
    }

    private static NavigationItem attach(
            String id, Map<String, NavigationItem> byId, Map<String, List<String>> childIds) {
        NavigationItem item = byId.get(id);
        List<String> children = childIds.get(id);
        if (children == null) {
            return item;
        }
        return new NavigationItem(
                item.id(),
                item.labelKey(),
                item.icon(),
                item.order(),
                item.requiredPermission(),
                children.stream().map(child -> attach(child, byId, childIds)).toList());
    }

    /** Sorts a level and every level below it, once, so filtering never has to sort again. */
    private static List<NavigationItem> sorted(List<NavigationItem> items) {
        return items.stream()
                .sorted(SIBLING_ORDER)
                .map(item -> item.hasChildren()
                        ? new NavigationItem(
                                item.id(),
                                item.labelKey(),
                                item.icon(),
                                item.order(),
                                item.requiredPermission(),
                                sorted(item.children()))
                        : item)
                .toList();
    }

    private static void collectIds(List<NavigationItem> items, List<String> into) {
        for (NavigationItem item : items) {
            into.add(item.id());
            collectIds(item.children(), into);
        }
    }

    private static void validate(NavigationItem item, NavigationItem parent, String source, Map<String, String> seen) {
        if (item == null) {
            throw new IllegalStateException("A NavigationProvider returned a null item (" + source + ")");
        }
        if (item.id() == null || !ID_PATTERN.matcher(item.id()).matches()) {
            throw new IllegalStateException("Not a usable navigation id: " + item.id()
                    + " (expected dotted lower case matching " + ID_PATTERN.pattern()
                    + " — an id is not a URL, and never contains one)");
        }
        if (parent != null && !item.id().startsWith(parent.id() + ".")) {
            throw new IllegalStateException("Navigation item " + item.id() + " is a child of " + parent.id()
                    + ", so its id must begin with \"" + parent.id() + ".\"");
        }
        if (item.labelKey() == null || item.labelKey().isBlank()) {
            throw new IllegalStateException("Navigation item " + item.id()
                    + " has no labelKey. The server sends a key and the frontend translates it (ADR-0008);"
                    + " an item with no key has nothing to render.");
        }
        tooLong(item.id(), "id", item.id(), MAX_ID_LENGTH);
        tooLong(item.id(), "labelKey", item.labelKey(), MAX_LABEL_KEY_LENGTH);
        tooLong(item.id(), "icon", item.icon(), MAX_ICON_LENGTH);

        String clash = seen.putIfAbsent(item.id(), source);
        if (clash != null) {
            throw new IllegalStateException(
                    "Navigation id " + item.id() + " is declared twice: by " + clash + " and by " + source);
        }

        for (NavigationItem child : item.children()) {
            validate(child, item, source, seen);
        }
    }

    private static void tooLong(String id, String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalStateException(
                    "Navigation item " + id + " has a " + field + " longer than " + max + " characters");
        }
    }
}
