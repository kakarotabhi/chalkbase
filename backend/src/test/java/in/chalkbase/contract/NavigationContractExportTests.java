package in.chalkbase.contract;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.platform.navigation.NavigationCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exports every navigation id the running build declares to {@code contracts/navigation-ids.json},
 * the same way {@link OpenApiContractTests} exports the API shape.
 *
 * <p>ADR-0008 says the navigation contract "needs a test on both sides — the backend must not emit
 * an id the frontend cannot resolve." This is the backend half of that: the honest source of "every
 * id this build declares" is {@link NavigationCatalog}, built from every {@code NavigationProvider}
 * bean the running application actually wires up. Parsing the {@code *Navigation.java} files with a
 * pattern would drift the moment one of them is refactored; this instead asks the same object the
 * bootstrap endpoint asks.
 *
 * <p>The frontend half — comparing this file against {@code nav-routes.ts} — is a script run in CI
 * rather than a JUnit test, because it needs both artefacts and this module cannot see the frontend
 * tree. See {@code tools/navigation-contract/check.mjs} and
 * {@code .github/workflows/navigation-contract.yml}.
 *
 * <h2>Determinism, and why the ids are sorted rather than left in menu order</h2>
 *
 * <p>{@link NavigationCatalog#ids()} returns ids in display order, which depends on each item's
 * {@code order} field — a value that changes whenever a screen is reordered in the menu, with no
 * change to which ids exist. Sorting alphabetically before writing means the committed file only
 * moves when an id is actually added, renamed or removed, which is the only kind of change the
 * contract check cares about.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NavigationContractExportTests {

    /**
     * A floor, not the true count, so this does not have to be edited every time a module adds a
     * screen. It exists so that a bug which empties the catalogue — a provider bean that stops
     * being picked up, a filter applied somewhere it should not be — fails here as a suspiciously
     * small count, rather than writing a near-empty file that the comparison script would read as
     * "nothing to check", which is a check that passes by finding nothing.
     */
    private static final int MINIMUM_EXPECTED_IDS = 15;

    @Autowired
    NavigationCatalog catalog;

    @Test
    void exportsTheDeclaredIds() throws IOException {
        List<String> ids = sortedIds();

        assertThat(ids.size())
                .as("navigation ids declared across every module on the classpath")
                .isGreaterThanOrEqualTo(MINIMUM_EXPECTED_IDS);

        Path target = contractsDirectory().resolve("navigation-ids.json");
        Files.writeString(target, render(ids), StandardCharsets.UTF_8);
        assertThat(target)
                .content(StandardCharsets.UTF_8)
                .startsWith("[\n")
                .contains("\"dashboard\"")
                .endsWith("]\n");
    }

    @Test
    void exportIsDeterministic() {
        assertThat(render(sortedIds())).isEqualTo(render(sortedIds()));
    }

    private List<String> sortedIds() {
        return List.copyOf(new TreeSet<>(catalog.ids()));
    }

    /** Walks up from the working directory until the sibling {@code contracts/} appears. */
    private static Path contractsDirectory() throws IOException {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            Path contracts = candidate.resolve("contracts");
            if (Files.isDirectory(contracts)) {
                return contracts;
            }
        }
        throw new IOException("no contracts/ directory above " + Path.of("").toAbsolutePath());
    }

    /**
     * A flat JSON array, one id per line. The caller passes an already-sorted list; no pretty
     * printer, so the bytes never depend on which Jackson version renders them, matching {@link
     * OpenApiContractTests}'s own reasoning.
     */
    private static String render(List<String> sortedIds) {
        StringBuilder out = new StringBuilder(sortedIds.size() * 24);
        out.append("[\n");
        for (int i = 0; i < sortedIds.size(); i++) {
            out.append("  \"").append(sortedIds.get(i)).append('"');
            if (i < sortedIds.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("]\n");
        return out.toString();
    }
}
