/**
 * Document: certificate, compliance and identity documents attached to a student (FR-013, FR-032).
 *
 * <p>Owns {@code document} (per tenant) — the metadata: type, issue and expiry dates, verification
 * status, and where the bytes are. The bytes themselves live behind
 * {@code platform.storage.StorageService}, not in this database, following the storage port ADR-0025
 * defines. See that ADR for what is authoritative when the two disagree, how one school's files are
 * kept from another's, and why the download path proxies every byte rather than issuing a signed
 * URL.
 *
 * <p><strong>{@link #getId} on a document does not point at a {@code student} entity.</strong> The
 * table carries {@code student_id} as a plain {@code uuid} with a database foreign key, never a
 * {@code @ManyToOne} — the same shape {@code student_enrolment} uses for {@code academic_session_id}
 * and {@code section_id} — so this module has no Java dependency on {@code student} at all. The
 * constraint still lives in the database, where it belongs; a document naming a student this school
 * does not have is refused by {@code fk_document_student}, not by a lookup this module would
 * otherwise need {@code student}'s API for.
 *
 * <p>Every document type this build offers is Confidential under ADR-0014, deliberately: a document
 * whose <em>type</em> would itself disclose a Restricted category — a caste certificate, an Aadhaar
 * copy, a disability certificate — is excluded until the encryption, masking and read-auditing
 * ADR-0020 §2 is waiting on for the student record's own Restricted columns exists for this module
 * too. See ADR-0025.
 *
 * <p>Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Document")
package in.chalkbase.document;
