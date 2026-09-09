package in.chalkbase.admission.domain;

/** What this module calls the things it writes to the audit log (ADR-0018). */
public final class AdmissionAudit {

    public static final String ENQUIRY = "ENQUIRY";
    public static final String FOLLOW_UP = "ENQUIRY_FOLLOW_UP";

    private AdmissionAudit() {}
}
