package in.chalkbase.student.infrastructure;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;

/**
 * One record read out of an uploaded CSV, before anything has decided what its cells mean.
 *
 * <p>Not a boundary DTO — it never crosses a module or an HTTP boundary — but it is classified and
 * redacted like one, and that is deliberate. Its cells are a child's name and date of birth
 * straight off the wire (ADR-0014), and a record's generated {@code toString} prints every
 * component, so one {@code log.debug("read {}", row)} added on a bad afternoon would put the whole
 * file in a log file. Carrying the annotation also brings this record under
 * {@code ClassificationTests}, so the protection cannot be removed quietly.
 *
 * @param number the record's position in the file, counting the header as 1. This is the row number
 *     the person sees on the left of their spreadsheet, which is the only row number worth
 *     reporting — see {@link CsvReader} for why it counts records rather than lines.
 * @param cells the values, in the order the file gave them, with quoting already resolved. Empty
 *     for a record that could not be read.
 * @param problem why this record could not be read, or null when it read fine. A fixed sentence
 *     with nothing out of the file in it.
 */
public record CsvRow(
        @Classification(Tier.INTERNAL) int number,
        @Classification(Tier.CONFIDENTIAL) List<String> cells,
        @Classification(Tier.INTERNAL) String problem) {

    public CsvRow {
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    /** True when the record could not be read at all — an unterminated quote, and nothing else yet. */
    public boolean malformed() {
        return problem != null;
    }

    /**
     * True for a record with nothing in it.
     *
     * <p>Excel ends a saved CSV with a newline and a school's file often has a blank line or two in
     * the middle where a class ended. Neither is a mistake anyone should be told about, so a blank
     * record is skipped rather than reported — but it still consumes a record number, because it
     * still occupies a row in the spreadsheet the person is looking at.
     */
    public boolean blank() {
        return cells.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
