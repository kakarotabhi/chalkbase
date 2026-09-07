package in.chalkbase.document.domain;

/** Whether the office has checked a document against the original (FR-032). */
public enum VerificationStatus {

    /** Uploaded, not yet looked at. What every document starts as. */
    UNVERIFIED,

    /** Checked against the original and accepted. */
    VERIFIED,

    /** Checked and found wanting — wrong document, illegible scan, does not match the child. */
    REJECTED
}
