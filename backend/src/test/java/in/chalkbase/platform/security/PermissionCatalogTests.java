package in.chalkbase.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is the one place a permission can come into existence, so it is the one place a
 * malformed or duplicated identifier can be stopped.
 *
 * <p>These failures are deliberately startup failures rather than runtime ones. A permission code
 * is stored in every school's {@code role_permission} table, so a typo that reached a database
 * would need a migration to undo — far worse than a build that refuses to start.
 */
class PermissionCatalogTests {

    @Test
    void collectsWhatTheModulesDeclare() {
        PermissionCatalog catalog = catalogOf(
                List.of(new PermissionDefinition("school:school:read", "school", "View schools", "See them.")),
                List.of(new PermissionDefinition("fee:invoice:create", "fee", "Raise an invoice", "Bill a parent.")));

        assertThat(catalog.all())
                .extracting(PermissionDefinition::code)
                // sorted, so a UI, a log line and a test all agree on the order
                .containsExactly("fee:invoice:create", "school:school:read");
        assertThat(catalog.contains("fee:invoice:create")).isTrue();
        assertThat(catalog.contains("fee:invoice:approve")).isFalse();
        assertThat(catalog.require("school:school:read").label()).isEqualTo("View schools");
    }

    @Test
    void refusesACodeThatIsNotModuleResourceAction() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(new PermissionDefinition("invoices", "fee", "Invoices", null))))
                .withMessageContaining("Not a usable permission code");

        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(new PermissionDefinition("fee:invoice", "fee", "Invoices", null))))
                .withMessageContaining("Not a usable permission code");

        assertThatIllegalStateException()
                .isThrownBy(() ->
                        catalogOf(List.of(new PermissionDefinition("Fee:Invoice:Create", "Fee", "Invoices", null))))
                .withMessageContaining("Not a usable permission code");

        assertThatIllegalStateException()
                .isThrownBy(() ->
                        catalogOf(List.of(new PermissionDefinition("fee:invoice:create:now", "fee", "Invoices", null))))
                .withMessageContaining("Not a usable permission code");
    }

    /** The {@code module} column must not be able to disagree with the first segment of the code. */
    @Test
    void refusesAPermissionWhoseModuleDoesNotMatchItsCode() {
        assertThatIllegalStateException()
                .isThrownBy(() ->
                        catalogOf(List.of(new PermissionDefinition("fee:invoice:create", "school", "Invoices", null))))
                .withMessageContaining("claims module school");
    }

    @Test
    void refusesTheSameCodeFromTwoModules() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(
                        List.of(new PermissionDefinition("fee:invoice:create", "fee", "Raise an invoice", null)),
                        List.of(new PermissionDefinition("fee:invoice:create", "fee", "Bill a parent", null))))
                .withMessageContaining("is declared twice");
    }

    /** Two modules is the interesting case, but one provider listing a code twice is the same bug. */
    @Test
    void refusesTheSameCodeTwiceFromOneModule() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(
                        new PermissionDefinition("fee:invoice:create", "fee", "Raise an invoice", null),
                        new PermissionDefinition("fee:invoice:create", "fee", "Raise an invoice", null))))
                .withMessageContaining("is declared twice");
    }

    @Test
    void refusesAPermissionWithNoLabelBecauseAPrincipalHasToReadThisList() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(List.of(new PermissionDefinition("fee:invoice:create", "fee", " ", null))))
                .withMessageContaining("has no label");
    }

    @Test
    void refusesAValueLongerThanTheColumnThatHasToHoldIt() {
        assertThatIllegalStateException()
                .isThrownBy(() -> catalogOf(
                        List.of(new PermissionDefinition("fee:invoice:create", "fee", "x".repeat(121), null))))
                .withMessageContaining("label longer than");
    }

    @Test
    void namesEveryUnknownPermissionAtOnce() {
        PermissionCatalog catalog =
                catalogOf(List.of(new PermissionDefinition("fee:invoice:create", "fee", "Raise an invoice", null)));

        assertThatCode(() -> catalog.requireAll(List.of("fee:invoice:create"))).doesNotThrowAnyException();
        assertThatIllegalStateException()
                .isThrownBy(() -> catalog.requireAll(List.of("fee:invoice:void", "fee:invoice:create", "fee:x:y")))
                .withMessageContaining("[fee:invoice:void, fee:x:y]");
    }

    @SafeVarargs
    private static PermissionCatalog catalogOf(List<PermissionDefinition>... providers) {
        return new PermissionCatalog(java.util.Arrays.stream(providers)
                .map(list -> (PermissionProvider) () -> list)
                .toList());
    }
}
