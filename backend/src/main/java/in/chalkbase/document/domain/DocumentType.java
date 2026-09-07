package in.chalkbase.document.domain;

/**
 * What kind of document this is (FR-013, FR-032).
 *
 * <p><strong>This list deliberately excludes every Restricted-category document</strong> — a caste
 * certificate, an Aadhaar or APAAR copy, a disability certificate. ADR-0025 explains why: the
 * <em>type</em> of a document is itself Restricted-category information under ADR-0014 — a row
 * saying a child has a {@code CASTE_CERTIFICATE} on file discloses the same fact a {@code caste}
 * column would — and ADR-0020 §2 already left those columns out of the student record until
 * encryption at rest, masking and read-auditing exist for them. Adding a document type that
 * discloses the same category one layer up would reopen exactly the gap that decision closed.
 * {@link #OTHER} is not an escape hatch for that: it is Confidential like every value here, and a
 * school attaching something Restricted under it is a trust boundary this type cannot enforce, not
 * a feature.
 */
public enum DocumentType {
    BIRTH_CERTIFICATE,
    TRANSFER_CERTIFICATE,
    REPORT_CARD,
    PHOTO,
    SIGNATURE,
    OTHER
}
