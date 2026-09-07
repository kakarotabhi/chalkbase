package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;

/**
 * What an upload was found to contain, and what was done about it.
 *
 * <p>One shape for both endpoints, on purpose. {@code POST /api/students/import/validate} answers
 * with {@code imported} at zero because it wrote nothing; {@code POST /api/students/import} answers
 * with the same report and {@code imported} at zero too if anything was wrong, because the commit is
 * all or nothing (ADR-0021 §2). A screen that can render one can render the other, and the two
 * cannot drift apart.
 *
 * <p>Both endpoints answer <strong>200 with a report</strong> rather than a 4xx with an error
 * envelope, including when nothing could be imported. A file with twenty-seven bad rows is not a
 * malformed request — it is a request that was understood perfectly and whose answer is a list of
 * twenty-seven things to fix. The error envelope carries a code and a sentence (ADR-0007) and has
 * nowhere to put that list. What does produce a 4xx is a file that could not be read as a file at
 * all: no header, no rows, too many rows, a workbook instead of a CSV.
 *
 * <p><strong>The four guardian counts follow {@code imported}'s own rule</strong> (ADR-0021 §4):
 * zero from {@code validate}, and zero from a commit that found anything wrong, because nothing was
 * written in either case. They do not say what a clean file <em>would</em> do — only what actually
 * happened — for the same reason {@code imported} does not: a number that might not materialise once
 * an unrelated row is fixed is a number that trains a school not to trust this report.
 *
 * @param totalRows data rows in the file, not counting the header and not counting blank lines
 * @param validRows rows with nothing wrong with them. Equal to {@code totalRows} exactly when the
 *     file is clean, which is the only case in which the commit endpoint writes anything.
 * @param imported students actually created. Zero from {@code validate}, always; zero from a commit
 *     that found anything wrong; otherwise equal to {@code validRows}.
 * @param guardiansCreated new guardian person records actually written — a phone number in the file
 *     that matched nobody in this school's directory and nobody else in the file either.
 * @param guardiansMatched distinct guardians the import's students were linked to
 *     <strong>without</strong> creating a new person: already in the directory, or already claimed
 *     by an earlier row in this same file.
 * @param guardianLinksCreated student-guardian links actually written in total. Can exceed
 *     {@code guardiansCreated + guardiansMatched}: four rows naming one father produce one guardian
 *     and four links, so this is a count of <em>rows</em> with a guardian, not of guardians.
 * @param studentsLinkedToExistingGuardians how many of those links pointed at a guardian that
 *     already existed before this import ran, rather than one this import just created. The number
 *     this report exists to make trustworthy — "created 12 guardians, linked 47 students to
 *     existing guardians" is the difference between a clerk trusting this and a clerk checking six
 *     hundred rows by hand.
 * @param errorCount how many problems were found in total. Equal to {@code errors.size()} unless
 *     the list was capped, in which case this is the honest number and the list is the first 200 of
 *     it — so a screen renders "showing 200 of 1,412" rather than silently hiding the rest, which on
 *     this screen is the worst failure available.
 * @param errors what is wrong, in row order, <strong>capped at 200 entries</strong>. A school fixing
 *     one error per upload would give up, so this reports everything it found rather than the first
 *     thing — but a file where every row is wrong produces thousands of these, and a report nobody
 *     can read is not more useful than a report they can. Compare with {@code errorCount} to know
 *     whether the list was cut short.
 */
public record ImportReport(
        @Classification(Tier.INTERNAL) int totalRows,
        @Classification(Tier.INTERNAL) int validRows,
        @Classification(Tier.INTERNAL) int imported,
        @Classification(Tier.INTERNAL) int guardiansCreated,
        @Classification(Tier.INTERNAL) int guardiansMatched,
        @Classification(Tier.INTERNAL) int guardianLinksCreated,
        @Classification(Tier.INTERNAL) int studentsLinkedToExistingGuardians,
        @Classification(Tier.INTERNAL) int errorCount,
        @Classification(Tier.CONFIDENTIAL) List<ImportError> errors) {

    public ImportReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
