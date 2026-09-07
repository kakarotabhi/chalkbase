package in.chalkbase.platform.export;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A CSV writer, hand-written and about as long as {@code CsvReader} — its sibling on the way in
 * (ADR-0021 §6).
 *
 * <p><strong>Why not a library.</strong> The same reasoning as {@code CsvReader}: {@code AGENTS.md}
 * rule 8 says ask before adding a dependency, and what an export actually needs is RFC 4180's core
 * — quote a field that contains a comma, a quote or a newline, double an embedded quote — which is
 * this file. Apache POI was put to the product owner for the richer {@code .xlsx} format and
 * refused on size and CVE-surface grounds (see {@code docs/status.md}); a CSV library would be the
 * same argument for less.
 *
 * <p><strong>It writes a byte order mark.</strong> Excel is the reader every school actually has,
 * and Excel only opens a UTF-8 CSV correctly — accented and Devanagari names included — when the
 * file starts with one. {@code CsvReader} already documents the mirror image: it consumes a BOM on
 * the way in for the same reason.
 *
 * <p><strong>It guards against formula injection.</strong> A cell is written by this application,
 * but its content is not: a guardian's occupation or a reason-for-leaving is free text a parent or
 * an office clerk typed, and a value starting with {@code =}, {@code +}, {@code -} or {@code @} is
 * a formula to Excel and Google Sheets the moment the file is opened. A leading apostrophe is what
 * both readers already treat as "the rest of this is text" (OWASP's own mitigation for this class
 * of issue), so that is what a cell shaped like a formula gets, silently and without changing what
 * a human reads.
 *
 * <p><strong>{@code \r\n} line endings</strong>, per RFC 4180, because that is what a spreadsheet
 * expects regardless of the platform this backend happens to run on.
 *
 * <p>Does not close the stream it is given. A caller writing straight into an HTTP response owns
 * that stream's lifecycle — see {@code ClassificationCsvExporter}, which never calls
 * {@link #close()} for exactly this reason and only ever {@link #flush()}es.
 */
public final class CsvWriter implements Flushable, Closeable {

    private static final String LINE_ENDING = "\r\n";

    /** Excel's byte order mark. {@code CsvReader} consumes exactly this character on the way in. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final Writer out;

    public CsvWriter(OutputStream stream) {
        this.out = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    /** Written once, before the header row. See the class Javadoc for why Excel needs it. */
    public void writeByteOrderMark() throws IOException {
        out.write(BYTE_ORDER_MARK);
    }

    /** One row. Every cell is escaped independently; the caller supplies plain, unescaped text. */
    public void writeRecord(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(cells.get(i)));
        }
        out.write(LINE_ENDING);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes the underlying stream. Callers writing into a servlet response should not call this —
     * see the class Javadoc — and should {@link #flush()} instead.
     */
    @Override
    public void close() throws IOException {
        out.close();
    }

    private static String escape(String cell) {
        String value = guardAgainstFormulaInjection(cell == null ? "" : cell);
        boolean needsQuoting = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * A leading apostrophe, the same mitigation Excel and Google Sheets themselves offer when you
     * type a formula-looking value into a cell and mean it as text. See the class Javadoc.
     */
    private static String guardAgainstFormulaInjection(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
            return "'" + value;
        }
        return value;
    }
}
