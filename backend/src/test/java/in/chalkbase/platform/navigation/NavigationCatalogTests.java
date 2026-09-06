package in.chalkbase.platform.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is where a menu becomes one user's menu, so it is where the three ways of getting
 * that wrong have to be stopped: showing an item the user cannot use, showing a section that opens
 * onto nothing, and hiding a child while leaving its parent behind.
 *
 * <p>The validation failures are startup failures on purpose. A navigation id is the contract with
 * the frontend's route map — a malformed or duplicated one produces a menu entry that silently does
 * nothing, which is far harder to notice than an application that refuses to start.
 */
class NavigationCatalogTests {

    private static final String FEES_READ = "fee:invoice:read";
    private static final String FEES_COLLECT = "fee:payment:record";
    private static final String REPORTS_READ = "report:report:read";

    // ── Filtering ────────────────────────────────────────────────────────────────────────────

    @Test
    void keepsAnItemWithNoRequiredPermissionForAnyoneWithASession() {
        NavigationCatalog catalog = catalogOf(List.of(leaf("dashboard", 10, null)));

        assertThat(idsFor(catalog, Set.of())).containsExactly("dashboard");
    }

    @Test
    void dropsAnItemWhoseRequiredPermissionTheUserDoesNotHold() {
        NavigationCatalog catalog = catalogOf(List.of(leaf("dashboard", 10, null), leaf("fees", 20, FEES_READ)));

        assertThat(idsFor(catalog, Set.of())).containsExactly("dashboard");
        assertThat(idsFor(catalog, Set.of(FEES_READ))).containsExactly("dashboard", "fees");
    }

    /** Holding a permission that gates nothing must not conjure an item out of the catalogue. */
    @Test
    void anUnrelatedPermissionAddsNothing() {
        NavigationCatalog catalog = catalogOf(List.of(leaf("fees", 20, FEES_READ)));

        assertThat(idsFor(catalog, Set.of(REPORTS_READ))).isEmpty();
    }

    // ── Parents and subtrees ─────────────────────────────────────────────────────────────────

    /**
     * The rule that matters most: a section the user may not open takes its children with it.
     * Filtering children independently would leak the existence of a screen inside a section the
     * user was never meant to see.
     */
    @Test
    void aParentWithItsOwnRequiredPermissionDropsItsWholeSubtree() {
        NavigationCatalog catalog = catalogOf(List.of(parent(
                "fees",
                20,
                FEES_READ,
                List.of(leaf("fees.collect", 10, FEES_COLLECT), leaf("fees.summary", 20, null)))));

        // Holds the child's permission, but not the parent's: the whole branch is gone, and in
        // particular fees.summary — which needs nothing — is gone too.
        assertThat(allIdsFor(catalog, Set.of(FEES_COLLECT))).isEmpty();

        assertThat(allIdsFor(catalog, Set.of(FEES_READ, FEES_COLLECT)))
                .containsExactly("fees", "fees.collect", "fees.summary");
    }

    /** A menu entry that opens an empty submenu is worse than no entry. */
    @Test
    void dropsAParentWithNoRequiredPermissionWhenEveryChildIsDropped() {
        NavigationCatalog catalog = catalogOf(List.of(
                leaf("dashboard", 10, null),
                parent("settings", 90, null, List.of(leaf("settings.access", 10, REPORTS_READ)))));

        assertThat(idsFor(catalog, Set.of())).containsExactly("dashboard");
        assertThat(allIdsFor(catalog, Set.of(REPORTS_READ)))
                .containsExactly("dashboard", "settings", "settings.access");
    }

    /**
     * The same rule applied to a parent the user <em>may</em> open. It declared children, so it is a
     * container, and a container with nothing left in it is not an item either.
     */
    @Test
    void dropsAParentTheUserMayOpenWhenEveryChildIsDroppedAnyway() {
        NavigationCatalog catalog =
                catalogOf(List.of(parent("fees", 20, FEES_READ, List.of(leaf("fees.collect", 10, FEES_COLLECT)))));

        assertThat(allIdsFor(catalog, Set.of(FEES_READ))).isEmpty();
    }

    /** One surviving child is enough to keep the section. */
    @Test
    void keepsAParentWhenOneChildSurvives() {
        NavigationCatalog catalog = catalogOf(List.of(parent(
                "settings",
                90,
                null,
                List.of(leaf("settings.access", 10, REPORTS_READ), leaf("settings.school", 20, FEES_READ)))));

        assertThat(allIdsFor(catalog, Set.of(FEES_READ))).containsExactly("settings", "settings.school");
    }

    // ── Ordering ─────────────────────────────────────────────────────────────────────────────

    @Test
    void sortsByOrderThenIdAtEveryLevel() {
        NavigationCatalog catalog = catalogOf(
                List.of(
                        leaf("zebra", 10, null),
                        parent(
                                "students",
                                20,
                                null,
                                List.of(
                                        leaf("students.transfers", 30, null),
                                        leaf("students.admissions", 10, null),
                                        leaf("students.roll", 30, null)))),
                List.of(leaf("alpha", 10, null), leaf("hostel", 5, null)));

        // order first — hostel(5) before the three tens — then id among the ties, across providers.
        assertThat(idsFor(catalog, Set.of())).containsExactly("hostel", "alpha", "zebra", "students");
        assertThat(allIdsFor(catalog, Set.of()))
                .containsExactly(
                        "hostel",
                        "alpha",
                        "zebra",
                        "students",
                        "students.admissions",
                        "students.roll",
                        "students.transfers");
    }

    /** The same catalogue asked twice, and asked with different permissions, keeps its order. */
    @Test
    void orderIsStableAcrossCalls() {
        NavigationCatalog catalog =
                catalogOf(List.of(leaf("beta", 10, null), leaf("alpha", 10, FEES_READ), leaf("gamma", 10, null)));

        assertThat(idsFor(catalog, Set.of(FEES_READ))).containsExactly("alpha", "beta", "gamma");
        assertThat(idsFor(catalog, Set.of(FEES_READ))).containsExactly("alpha", "beta", "gamma");
        assertThat(idsFor(catalog, Set.of())).containsExactly("beta", "gamma");
        assertThat(catalog.all()).extracting(NavigationItem::id).containsExactly("alpha", "beta", "gamma");
    }

    // ── Validation ───────────────────────────────────────────────────────────────────────────

    /**
     * An id is dotted and lower case, and cannot express a URL: no slash, no scheme, no query. That
     * is the structural half of ADR-0008's "the server never sends a URL" — the other half is that
     * {@link NavigationItem} has no field one could go in.
     */
    @Test
    void refusesAnIdThatIsNotDottedLowerCase() {
        for (String bad : List.of(
                "/fees",
                "fees/collect",
                "Fees",
                "fees.",
                ".fees",
                "fees..collect",
                "https://example.test/fees",
                "fees collect",
                "fees-collect",
                "2fees")) {
            assertThatIllegalStateException()
                    .as("id %s", bad)
                    .isThrownBy(() -> catalogOf(List.of(leaf(bad, 10, null))))
                    .withMessageContaining("Not a usable navigation id");
        }
    }

    @Test
    void refusesTheSameIdFromTwoModules() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(leaf("fees", 10, null)), List.of(leaf("fees", 20, null))))
                .withMessageContaining("is declared twice");
    }

    @Test
    void refusesTheSameIdTwiceFromOneModule() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(leaf("fees", 10, null), leaf("fees", 20, null))))
                .withMessageContaining("is declared twice");
    }

    /** A child clashing with a root somewhere else is the same bug, and easier to miss. */
    @Test
    void refusesAChildWhoseIdIsAlreadyARootElsewhere() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(
                        List.of(parent("settings", 90, null, List.of(leaf("settings.access", 10, null)))),
                        List.of(leaf("settings.access", 10, null))))
                .withMessageContaining("is declared twice");
    }

    /** So that any id read on its own says where it sits, and a copy-pasted child is caught. */
    @Test
    void refusesAChildWhoseIdIsNotPrefixedByItsParent() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(parent("settings", 90, null, List.of(leaf("access", 10, null))))))
                .withMessageContaining("must begin with \"settings.\"");
    }

    @Test
    void refusesAnItemWithNoLabelKeyBecauseThereWouldBeNothingToRender() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(new NavigationItem("fees", " ", "receipt", 10, null))))
                .withMessageContaining("has no labelKey");
    }

    // ── Contributions from another module ────────────────────────────────────────────────────

    /**
     * The reason this exists: the school module owns the school-profile screen, the identity module
     * owns the settings section it belongs in, and neither may reach into the other. The id says
     * where the item goes, so declaring it at the top level is enough.
     */
    @Test
    void placesAContributedChildUnderASectionAnotherModuleOwns() {
        NavigationCatalog catalog = catalogOf(
                List.of(parent("settings", 90, null, List.of(leaf("settings.access", 10, null)))),
                List.of(leaf("settings.profile", 20, null)));

        assertThat(allIdsFor(catalog, Set.of())).containsExactly("settings", "settings.access", "settings.profile");
    }

    /** Contributed and inline children are one list, so they interleave by order like any siblings. */
    @Test
    void sortsAContributedChildAmongTheSectionsOwnChildren() {
        NavigationCatalog catalog = catalogOf(
                List.of(parent("settings", 90, null, List.of(leaf("settings.access", 30, null)))),
                List.of(leaf("settings.profile", 10, null)));

        assertThat(allIdsFor(catalog, Set.of())).containsExactly("settings", "settings.profile", "settings.access");
    }

    /** A section survives on a contributed child alone — otherwise the contribution would be pointless. */
    @Test
    void keepsASectionAliveWhenOnlyAContributedChildSurvives() {
        NavigationCatalog catalog = catalogOf(
                List.of(parent("settings", 90, null, List.of(leaf("settings.access", 10, REPORTS_READ)))),
                List.of(leaf("settings.profile", 20, FEES_READ)));

        assertThat(allIdsFor(catalog, Set.of(FEES_READ))).containsExactly("settings", "settings.profile");
        assertThat(idsFor(catalog, Set.of())).isEmpty();
    }

    /**
     * A screen that gains a contributed child is still a screen. Deciding container-ness from the
     * assembled tree would get this backwards and hide {@code schools} whenever the one thing
     * contributed under it happened to be filtered away.
     */
    @Test
    void keepsALeafThatGainedAContributedChildWhenThatChildIsFiltered() {
        NavigationCatalog catalog =
                catalogOf(List.of(leaf("schools", 20, null)), List.of(leaf("schools.import", 10, FEES_READ)));

        assertThat(allIdsFor(catalog, Set.of(FEES_READ))).containsExactly("schools", "schools.import");
        assertThat(allIdsFor(catalog, Set.of())).containsExactly("schools");
    }

    /**
     * The failure mode this prevents: a contribution to a section that is not there ends up as a
     * root item, which reads as a top-level menu entry called "profile" rather than as a bug.
     */
    @Test
    void refusesAContributionWhoseParentNoModuleDeclares() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(leaf("settings.profile", 20, null))))
                .withMessageContaining("belongs under \"settings\"");
    }

    /** Contributions nest as deeply as their ids say, not one level only. */
    @Test
    void placesAContributionMoreThanOneLevelDeep() {
        NavigationCatalog catalog = catalogOf(
                List.of(parent(
                        "settings",
                        90,
                        null,
                        List.of(parent(
                                "settings.access", 10, null, List.of(leaf("settings.access.roles", 10, null)))))),
                List.of(leaf("settings.access.permissions", 20, null)));

        assertThat(allIdsFor(catalog, Set.of()))
                .containsExactly("settings", "settings.access", "settings.access.roles", "settings.access.permissions");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static NavigationItem leaf(String id, int order, String requiredPermission) {
        return new NavigationItem(id, "nav." + id, "receipt", order, requiredPermission);
    }

    private static NavigationItem parent(
            String id, int order, String requiredPermission, List<NavigationItem> children) {
        return new NavigationItem(id, "nav." + id, "folder", order, requiredPermission, children);
    }

    /** Root ids only. */
    private static List<String> idsFor(NavigationCatalog catalog, Set<String> permissions) {
        return catalog.navigationFor(permissions).stream()
                .map(NavigationItem::id)
                .toList();
    }

    /** Every id in the filtered tree, depth first, in the order it would be rendered. */
    private static List<String> allIdsFor(NavigationCatalog catalog, Set<String> permissions) {
        return flatten(catalog.navigationFor(permissions));
    }

    private static List<String> flatten(List<NavigationItem> items) {
        return items.stream()
                .flatMap(item -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(item.id()), flatten(item.children()).stream()))
                .toList();
    }

    @SafeVarargs
    private static NavigationCatalog catalogOf(List<NavigationItem>... providers) {
        return new NavigationCatalog(Arrays.stream(providers)
                .map(items -> (NavigationProvider) () -> items)
                .toList());
    }
}
