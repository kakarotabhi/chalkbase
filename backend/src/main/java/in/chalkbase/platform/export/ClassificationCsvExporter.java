package in.chalkbase.platform.export;

import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes a stream of flat DTO records to a CSV, deciding which columns to include from each
 * component's {@link in.chalkbase.platform.classification.Classification} — never from a
 * hand-maintained column list (ADR-0014).
 *
 * <h2>Why the columns come from the annotation and not from a list somewhere</h2>
 *
 * <p>A hand-written "these are the safe columns" list is a second statement of a fact the
 * {@code @Classification} on the row's own record already states once. The two can disagree, and
 * whichever one the export code happens to read wins — silently, because nothing checks the other
 * one. {@code ClassificationTests} already refuses to let a DTO field ship unclassified; this class
 * is what makes that one annotation the entire truth for what an export may print, so a
 * {@code RESTRICTED} field added to an export row tomorrow is masked correctly tomorrow, without
 * anyone remembering to update a second list. {@code ClassificationCsvExporterTests} is the test
 * that would fail if this class ever fell back to reading a name off a list instead.
 *
 * <h2>What "masked" means here, per tier — a decision, not the obvious one</h2>
 *
 * <p>Three shapes were on the table for a {@code RESTRICTED} column in {@link Mode#MASKED}: leave
 * it out of the file entirely, print an empty cell, or print a marker like {@code [restricted]}.
 * This class does the first. An empty cell is indistinguishable from "nothing was recorded", which
 * is a different fact from "something was recorded and this file is not allowed to say what"; a
 * marker announces to anyone who opens the file, or intercepts it, exactly which of a child's
 * fields are sensitive enough to hide, which is itself a small disclosure. Omitting the column says
 * neither of those things — the file simply does not have a "Blood Group" heading — and it is what
 * ADR-0014 already means by "masked by default in the UI": {@code MedicalSummary} does not send an
 * empty {@code bloodGroup} field either, it sends none at all.
 *
 * <p>{@code CONFIDENTIAL} and {@code INTERNAL} columns are never masked by this class, in either
 * mode. ADR-0014 only asks Restricted data to be masked by default; a masked export of children's
 * data that also hid every child's <em>name</em> would not be a safer export, it would be a
 * useless one nobody could act on.
 *
 * <h2>Streaming, not buffering</h2>
 *
 * <p>{@code rows} is consumed one element at a time and each one is written and discarded before the
 * next is read — nothing here accumulates the file's text in memory. A school of a few thousand
 * students produces a CSV of a few hundred kilobytes either way, but the difference is what happens
 * on a free-tier instance with 512 MB of memory: building the whole response body as one
 * {@code String} or {@code byte[]} before sending it is exactly the kind of allocation that kills a
 * process with no stack trace to show for it, and streaming straight into the servlet's own output
 * stream never makes that allocation. See {@code StudentController#export} for the response this
 * writes into directly, on the request thread — not {@code StreamingResponseBody}, whose async
 * dispatch thread would leave this class writing with no tenant schema bound (ADR-0011).
 */
public final class ClassificationCsvExporter {

    public enum Mode {
        /** ADR-0014's default. Every {@code RESTRICTED} column is left out of the file — see the class Javadoc. */
        MASKED,

        /**
         * Every column, {@code RESTRICTED} included. The caller is responsible for having already
         * checked the permission that allows this and for auditing the read — this class only
         * decides what a CSV row looks like, never who may ask for one.
         */
        UNMASKED
    }

    private ClassificationCsvExporter() {}

    /**
     * Writes {@code rows} to {@code out} as CSV: a byte-order-marked header row, then one row per
     * element of the stream, in order.
     *
     * <p>Does not close {@code out} — see {@link CsvWriter}'s own Javadoc for why a caller writing
     * into a servlet response must keep that stream's lifecycle to itself.
     *
     * @return the component names actually written, in column order — the field-name list an
     *     unmasked caller passes to {@code AuditService#recordSecurityEvent}, never the values
     * @throws IllegalStateException if {@code rowType} has a component with no
     *     {@code @Classification}. {@code ClassificationTests} should already have failed the build
     *     before this is ever reached in a shipped export; this is the belt to that braces.
     */
    public static <T extends Record> List<String> write(OutputStream out, Class<T> rowType, Stream<T> rows, Mode mode)
            throws IOException {
        List<RecordComponent> columns = columnsFor(rowType, mode);
        CsvWriter csv = new CsvWriter(out);
        try {
            csv.writeByteOrderMark();
            csv.writeRecord(
                    columns.stream().map(ClassificationCsvExporter::headerOf).toList());
            rows.forEach(row -> writeRowUnchecked(csv, columns, row));
            csv.flush();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return columns.stream().map(RecordComponent::getName).toList();
    }

    private static void writeRowUnchecked(CsvWriter csv, List<RecordComponent> columns, Object row) {
        try {
            List<String> cells = new ArrayList<>(columns.size());
            for (RecordComponent column : columns) {
                cells.add(cellOf(column, row));
            }
            csv.writeRecord(cells);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<RecordComponent> columnsFor(Class<?> rowType, Mode mode) {
        if (!rowType.isRecord()) {
            throw new IllegalArgumentException(
                    rowType.getName() + " is not a record; an export row needs @Classification on record"
                            + " components, and only a record has those");
        }
        List<RecordComponent> columns = new ArrayList<>();
        for (RecordComponent component : rowType.getRecordComponents()) {
            Tier tier = Classified.tierOf(component);
            if (tier == null) {
                throw new IllegalStateException("Export column " + rowType.getSimpleName() + "." + component.getName()
                        + " has no @Classification (ADR-0014). ClassificationTests should already have refused to"
                        + " let this into a build; add one before wiring the column into an export.");
            }
            if (mode == Mode.MASKED && tier == Tier.RESTRICTED) {
                // ADR-0014's default: omitted, not blanked and not marked — see the class Javadoc.
                continue;
            }
            columns.add(component);
        }
        return columns;
    }

    private static String cellOf(RecordComponent component, Object row) {
        try {
            Object value = component.getAccessor().invoke(row);
            if (value == null) {
                return "";
            }
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            return String.valueOf(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + component + " while building an export row", e);
        }
    }

    /**
     * {@code admissionNumber} becomes {@code Admission Number}. A generic split on the record
     * component's own name, not a second, hand-maintained header list — for the same reason the
     * columns themselves are not one: a header that could fall out of sync with the field it names
     * is exactly the kind of second source of truth this class exists to avoid. The result reads
     * acceptably rather than beautifully for an acronym-shaped name like {@code penUdiseId}, which
     * is the trade this class makes deliberately in exchange for never going stale.
     */
    private static String headerOf(RecordComponent component) {
        String name = component.getName();
        StringBuilder header = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i == 0) {
                header.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                header.append(' ').append(c);
            } else {
                header.append(c);
            }
        }
        return header.toString();
    }
}
