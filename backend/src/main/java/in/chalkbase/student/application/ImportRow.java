package in.chalkbase.student.application;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.StudentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of an upload, after its cells have been read and found to be well formed.
 *
 * <p>Not a boundary DTO and never serialised — the report is what crosses the wire — but classified
 * and redacted like one for the reason {@code CsvRow} is: it holds a child's name and date of birth,
 * and a record's generated {@code toString} prints every component. Nothing in this module logs one
 * today, and the annotation is what keeps that true for the edit that does not know it must not.
 *
 * <p>Held in memory for the length of one request and no longer (ADR-0021 §6). The file is never
 * written to disk.
 *
 * @param row the row number in the person's spreadsheet, so a failure at commit time can still be
 *     attributed to the row that caused it
 * @param sectionId the section the row's class and section <em>names</em> resolved to (ADR-0021 §3)
 */
public record ImportRow(
        @Classification(Tier.INTERNAL) int row,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName,
        @Classification(Tier.CONFIDENTIAL) LocalDate dateOfBirth,
        @Classification(Tier.CONFIDENTIAL) Gender gender,
        @Classification(Tier.INTERNAL) StudentStatus status,
        @Classification(Tier.CONFIDENTIAL) LocalDate admittedOn,
        @Classification(Tier.INTERNAL) UUID sectionId,
        @Classification(Tier.CONFIDENTIAL) String rollNumber) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
