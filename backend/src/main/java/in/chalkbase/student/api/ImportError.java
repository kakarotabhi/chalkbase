package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;

/**
 * One thing wrong with one row of an uploaded file.
 *
 * <p><strong>Nothing out of the file is ever quoted here</strong> (ADR-0014, ADR-0021 §6). The
 * message says "not a date in yyyy-MM-dd form", never what the cell said, because what the cell said
 * is a child's date of birth and this string is a response body, a screenshot in a support ticket
 * and — if anybody ever logs the report — a log line. Where a message has to explain why something
 * did not match, it names the <em>school's own</em> classes and sections, which are Internal under
 * ADR-0014 and are the half of the comparison that is actually useful: "Class V" not matching is
 * explained by listing "Class 4, Class 5, Class 6", not by repeating "Class V" back.
 *
 * @param row the row number the person sees on the left of their spreadsheet. The header is row 1,
 *     so the first student is row 2. A record that spans several lines because a quoted field
 *     contains a newline is still one row, because that is what the spreadsheet shows.
 * @param column which column the problem is in, by the import's own name for it. <strong>The empty
 *     string</strong> — never absent, never null — when the problem belongs to the row as a whole
 *     rather than to one of its cells, such as a row with more values than the header has columns:
 *     a screen groups those together under a heading of their own, and a field that is sometimes
 *     missing would make that grouping a null check rather than a comparison. This is the one place
 *     the "omit a null" convention is deliberately not applied, because the empty string is a value
 *     here and means something.
 * @param message a sentence to show the person fixing the file. Classified Confidential not because
 *     it holds a value today — it does not, and a test asserts as much — but because this is the one
 *     field on the whole report where a future edit could put one, and the tier is what would stop
 *     that reaching a log sink.
 */
public record ImportError(
        @Classification(Tier.INTERNAL) int row,
        @Classification(Tier.INTERNAL) String column,
        @Classification(Tier.CONFIDENTIAL) String message) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
