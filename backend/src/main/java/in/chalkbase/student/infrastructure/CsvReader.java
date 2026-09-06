package in.chalkbase.student.infrastructure;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A CSV reader, hand-written and about a hundred lines of it.
 *
 * <p><strong>Why not a library.</strong> {@code AGENTS.md} rule 8 says ask before adding a
 * dependency, and nothing on the classpath reads CSV. What a school's file actually needs is the
 * RFC 4180 core — quoted fields, commas inside them, doubled quotes inside those, and a byte order
 * mark from Excel — which is this file. A dependency would buy dialects, type inference and header
 * mapping that this import does not want and would still have to be told to ignore.
 *
 * <p><strong>It streams, and that is a requirement rather than a preference</strong> (ADR-0021 §6).
 * The file is several hundred children's names and dates of birth. It is never written to disk, and
 * it is read one record at a time so the caller can stop at the row cap without having materialised
 * the rest of a file somebody uploaded by mistake.
 *
 * <h2>Row numbers count records, not lines</h2>
 *
 * <p>A quoted field may contain a newline, so one record can span several lines of the file. The
 * number this reader hands out is the record's position — header first — because that is the number
 * the person is looking at on the left of their spreadsheet, and a report that named the underlying
 * line would send them to the wrong row of the only view of the file they have.
 *
 * <h2>What it is lenient about, deliberately</h2>
 *
 * <ul>
 *   <li>{@code \r\n}, {@code \n} and a bare {@code \r} all end a record. Files arrive from Windows,
 *       from LibreOffice and occasionally from a Mac old enough to matter.
 *   <li>A byte order mark at the very start is consumed. Excel writes one, and left in place it
 *       becomes part of the first column's name — so the header check would refuse a file whose
 *       first column is spelt exactly right.
 *   <li>Whitespace before an opening quote still opens a quoted field, so {@code a, "Nair, Meera"}
 *       reads as two cells rather than three.
 *   <li>A stray quote inside an unquoted field is kept as a character. It is what a school's
 *       ditto-marked column looks like, and refusing the row would be a worse answer than 5" being
 *       taken literally.
 *   <li>Bytes that are not valid UTF-8 are decoded to the replacement character rather than
 *       throwing. A file saved in a Windows code page still imports; the names in it may come out
 *       wrong, which the school can see and fix, and which is better than an upload that fails with
 *       nothing to act on.
 * </ul>
 *
 * <p>The one thing it refuses is a record whose quote is never closed, which is reported as that
 * record's {@link CsvRow#problem()} rather than as a failure of the whole parse — one row a school
 * cannot fix blindly must not cost them the report on the other five hundred and ninety-nine.
 */
public final class CsvReader implements Closeable {

    /** Excel's byte order mark, consumed if it is the first character in the file. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /** No character is pushed back. -1 is a real value here — it is the end of the file. */
    private static final int NOTHING_PUSHED_BACK = Integer.MIN_VALUE;

    private final Reader in;

    private int pushedBack = NOTHING_PUSHED_BACK;
    private int records = 0;
    private boolean atStart = true;
    private boolean finished = false;

    public CsvReader(InputStream stream) {
        // The decoder replaces malformed input rather than throwing: see the class javadoc.
        this.in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /**
     * The next record, or null once the file is exhausted.
     *
     * @throws IOException if the upload itself cannot be read — a dropped connection, not a
     *     malformed file. A file that is merely wrong comes back as a row with a
     *     {@link CsvRow#problem()}.
     */
    public CsvRow next() throws IOException {
        if (finished) {
            return null;
        }

        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean cellWasQuoted = false;
        boolean insideQuotes = false;
        boolean anythingRead = false;
        String problem = null;

        while (true) {
            int c = read();
            if (c == -1) {
                finished = true;
                if (!anythingRead) {
                    // A file that ends with a newline, which every editor writes. There is no
                    // trailing empty record to report.
                    return null;
                }
                if (insideQuotes) {
                    problem = "this row opens a quote that is never closed";
                }
                cells.add(cell.toString());
                break;
            }
            anythingRead = true;

            if (insideQuotes) {
                if (c != '"') {
                    cell.append((char) c);
                    continue;
                }
                int next = read();
                if (next == '"') {
                    cell.append('"'); // "" inside a quoted field is one literal quote (RFC 4180).
                } else {
                    insideQuotes = false;
                    pushBack(next);
                }
                continue;
            }

            if (c == '"' && !cellWasQuoted && cell.toString().isBlank()) {
                cellWasQuoted = true;
                insideQuotes = true;
                cell.setLength(0);
                continue;
            }
            if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                cellWasQuoted = false;
                continue;
            }
            if (c == '\r') {
                int next = read();
                if (next != '\n') {
                    pushBack(next);
                }
                cells.add(cell.toString());
                break;
            }
            if (c == '\n') {
                cells.add(cell.toString());
                break;
            }
            cell.append((char) c);
        }

        return new CsvRow(++records, cells, problem);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private int read() throws IOException {
        if (pushedBack != NOTHING_PUSHED_BACK) {
            int c = pushedBack;
            pushedBack = NOTHING_PUSHED_BACK;
            return c;
        }
        int c = in.read();
        if (atStart) {
            atStart = false;
            if (c == BYTE_ORDER_MARK) {
                return in.read();
            }
        }
        return c;
    }

    private void pushBack(int c) {
        pushedBack = c;
    }
}
