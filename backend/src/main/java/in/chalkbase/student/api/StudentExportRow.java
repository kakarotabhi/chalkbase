package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.Gender;
import in.chalkbase.student.domain.StudentStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One flattened row of the student export (ADR-0014, ADR-0027).
 *
 * <p><strong>This is the one DTO in the module that ever carries a Restricted value in a Java
 * field.</strong> Every other student DTO takes ADR-0022's masking decision at construction time —
 * {@link MedicalSummary} and {@link ComplianceSummary} are built from an entity but never given
 * anywhere to put a caste category or a blood group. This record is built once, with the real
 * values, for both the masked and the unmasked export; the masking happens afterwards, in
 * {@code in.chalkbase.platform.export.ClassificationCsvExporter}, which reads the
 * {@link Classification} on each component below and leaves every {@link Tier#RESTRICTED} column
 * out of a masked file. That is not a weaker guarantee than the rest of the module's masking — it
 * is what makes the exporter's masking mechanical rather than a second, hand-written list of "the
 * columns an export may print" that this record's own annotations could quietly disagree with. See
 * the exporter's class Javadoc for the argument in full.
 *
 * <p>Holding the real values in memory for the instant it takes to write one CSV row is the same
 * thing {@code StudentRecordService#medicalSummary} already does when it decrypts a row to compute
 * an {@code hasBloodGroup} flag: the entity's {@code @Convert(converter = EncryptedStringConverter.class)}
 * decrypts on load regardless of which DTO the value ends up in. What ADR-0014 calls "a read" is the
 * value <em>leaving the server</em>, not sitting briefly in a Java object — see
 * {@code StudentAudit#RESTRICTED_DATA_REVEALED}. Only the unmasked export writes that value into a
 * response, and only the unmasked export is what is audited as a reveal.
 *
 * <p>Guardians are flattened to two semicolon-joined columns — {@link #guardianNames()} and
 * {@link #guardianPhones()}, primary contact first — rather than repeating a row per guardian: a CSV
 * has one row per student, and a school opening this in a spreadsheet wants one line per child, not
 * a child repeated once per parent.
 */
public record StudentExportRow(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName,
        @Classification(Tier.CONFIDENTIAL) Gender gender,
        @Classification(Tier.INTERNAL) StudentStatus status,
        @Classification(Tier.CONFIDENTIAL) LocalDate dateOfBirth,
        @Classification(Tier.CONFIDENTIAL) LocalDate admittedOn,

        // ── This year's placement (empty when the student has none) ────────────────────────
        @Classification(Tier.INTERNAL) String currentSessionName,
        @Classification(Tier.INTERNAL) String currentClassName,
        @Classification(Tier.INTERNAL) String currentSectionName,
        @Classification(Tier.CONFIDENTIAL) String rollNumber,

        // ── Contact (FR-028), Confidential, never masked ────────────────────────────────────
        @Classification(Tier.CONFIDENTIAL) String address,
        @Classification(Tier.CONFIDENTIAL) String phone,
        @Classification(Tier.CONFIDENTIAL) String email,

        // ── Guardians, flattened; see the class Javadoc ─────────────────────────────────────
        @Classification(Tier.CONFIDENTIAL) String guardianNames,
        @Classification(Tier.CONFIDENTIAL) String guardianPhones,

        // ── Previous school and transfer certificate (FR-033), Confidential ─────────────────
        @Classification(Tier.CONFIDENTIAL) String previousSchoolName,
        @Classification(Tier.CONFIDENTIAL) String previousSchoolBoard,
        @Classification(Tier.CONFIDENTIAL) String transferCertificateNumber,
        @Classification(Tier.CONFIDENTIAL) LocalDate transferCertificateIssuedOn,

        // ── Medical (FR-034): emergency contact is Confidential, six fields are Restricted ──
        @Classification(Tier.CONFIDENTIAL) String emergencyContactName,
        @Classification(Tier.CONFIDENTIAL) String emergencyContactPhone,
        @Classification(Tier.CONFIDENTIAL) String emergencyContactRelation,
        @Classification(Tier.RESTRICTED) String bloodGroup,
        @Classification(Tier.RESTRICTED) String cwsnStatus,
        @Classification(Tier.RESTRICTED) String disabilityDetails,
        @Classification(Tier.RESTRICTED) String allergies,
        @Classification(Tier.RESTRICTED) String chronicConditions,
        @Classification(Tier.RESTRICTED) String medication,

        // ── Compliance (FR-029): identifiers are Confidential, four fields are Restricted ───
        @Classification(Tier.CONFIDENTIAL) String penUdiseId,
        @Classification(Tier.CONFIDENTIAL) String boardRegistrationNumber,
        @Classification(Tier.INTERNAL) boolean apaarConsentGiven,
        @Classification(Tier.CONFIDENTIAL) String apaarConsentGivenBy,
        @Classification(Tier.RESTRICTED) String casteCategory,
        @Classification(Tier.RESTRICTED) String religion,
        @Classification(Tier.RESTRICTED) String specialCategory,
        @Classification(Tier.RESTRICTED) String apaarId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
