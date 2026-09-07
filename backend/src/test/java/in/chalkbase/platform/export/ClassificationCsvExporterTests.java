package in.chalkbase.platform.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.platform.export.ClassificationCsvExporter.Mode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-0014 and ADR-0027 in force: masking is derived from a row's own {@code @Classification}, not
 * from a second, hand-written column list.
 *
 * <p>{@link #aRestrictedFieldAddedToAnExportRowIsMaskedWithoutAnyChangeHere} is the test the export
 * feature's brief asked for by name: a Restricted field appearing on a row type is masked out of a
 * masked export automatically, because nothing in this class or its caller ever names a column by
 * hand. If {@link ClassificationCsvExporter} ever regressed into reading a list of "safe" column
 * names instead of the annotation, this is the test that would fail.
 */
class ClassificationCsvExporterTests {

    /**
     * Shaped like a real export row: one Internal id, one Confidential name that must always be
     * present (a masked export that hid every child's name would be useless — see the class Javadoc
     * on {@code ClassificationCsvExporter}), and one Restricted field standing in for a caste
     * category or a blood group.
     */
    record Fixture(
            @Classification(Tier.INTERNAL) UUID id,
            @Classification(Tier.CONFIDENTIAL) String fullName,
            @Classification(Tier.RESTRICTED) String secretMedicalDetail) {
        @Override
        public String toString() {
            return Classified.describe(this);
        }
    }

    record Unclassified(String name) {
        @Override
        public String toString() {
            return Classified.describe(this);
        }
    }

    private static final UUID ID = UUID.fromString("018f4d3a-1234-7890-abcd-0123456789ab");
    private static final String SECRET = "SickleCellTraitZx9Sentinel";

    // ── The test the brief asked for by name ────────────────────────────────────────────────

    @Test
    void aRestrictedFieldAddedToAnExportRowIsMaskedWithoutAnyChangeHere() throws Exception {
        Fixture row = new Fixture(ID, "Aarav Kulkarni", SECRET);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        List<String> fields = ClassificationCsvExporter.write(out, Fixture.class, Stream.of(row), Mode.MASKED);

        String csv = out.toString(StandardCharsets.UTF_8);
        // Not merely "the column is absent" — the raw bytes never contain the secret at all, which
        // is the property that actually matters if a future column is added and forgotten about.
        assertThat(csv).doesNotContain(SECRET).doesNotContain("Secret Medical Detail");
        assertThat(fields).containsExactly("id", "fullName");
        // Confidential is not masked: a masked export that hid the child's own name would be
        // useless, per ADR-0014 and the class Javadoc.
        assertThat(csv).contains("Aarav Kulkarni");
    }

    @Test
    void unmaskedIncludesEveryColumn() throws Exception {
        Fixture row = new Fixture(ID, "Aarav Kulkarni", SECRET);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        List<String> fields = ClassificationCsvExporter.write(out, Fixture.class, Stream.of(row), Mode.UNMASKED);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains(SECRET).contains("Aarav Kulkarni");
        assertThat(fields).containsExactly("id", "fullName", "secretMedicalDetail");
    }

    @Test
    void headersAreDerivedFromTheComponentName() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ClassificationCsvExporter.write(out, Fixture.class, Stream.of(), Mode.UNMASKED);

        String header = firstLine(out);
        assertThat(header).isEqualTo("Id,Full Name,Secret Medical Detail");
    }

    @Test
    void writesAByteOrderMarkSoExcelReadsUtf8Correctly() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ClassificationCsvExporter.write(out, Fixture.class, Stream.of(), Mode.MASKED);

        byte[] bytes = out.toByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    void quotesACellContainingACommaOrAQuote() throws Exception {
        Fixture row = new Fixture(ID, "Nair, \"Meera\"", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ClassificationCsvExporter.write(out, Fixture.class, Stream.of(row), Mode.MASKED);

        String[] lines = firstLines(out, 2);
        assertThat(lines[1]).endsWith("\"Nair, \"\"Meera\"\"\"");
    }

    @Test
    void guardsAValueThatLooksLikeAFormula() throws Exception {
        Fixture row = new Fixture(ID, "=cmd|'/c calc'!A1", null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ClassificationCsvExporter.write(out, Fixture.class, Stream.of(row), Mode.MASKED);

        String csv = out.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("'=cmd|'/c calc'!A1").doesNotContain("\n=cmd").doesNotContain(",=cmd");
    }

    @Test
    void anUnannotatedComponentFailsRatherThanBeingAssumedSafe() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatIllegalStateException()
                .isThrownBy(() -> ClassificationCsvExporter.write(
                        out, Unclassified.class, Stream.of(new Unclassified("x")), Mode.MASKED))
                .withMessageContaining("no @Classification");
    }

    /**
     * A normal caller cannot reach this at all — {@code <T extends Record>} refuses a non-record
     * type at compile time — so this exercises it the only way anything ever could, through a raw
     * type, the same way a reflective caller with no compile-time check would.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void refusesATypeThatIsNotARecord() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Class rowType = String.class;
        Stream rows = Stream.of("x");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassificationCsvExporter.write(out, rowType, rows, Mode.MASKED));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static String firstLine(ByteArrayOutputStream out) {
        return firstLines(out, 1)[0];
    }

    /** Strips the BOM so a test comparing text does not have to know about it. */
    private static String[] firstLines(ByteArrayOutputStream out, int count) {
        String csv = out.toString(StandardCharsets.UTF_8);
        if (!csv.isEmpty() && csv.charAt(0) == '\uFEFF') {
            csv = csv.substring(1);
        }
        return csv.split("\r\n", count + 1);
    }
}
