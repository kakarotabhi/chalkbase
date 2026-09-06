package in.chalkbase.platform.classification;

/**
 * The four data classification tiers of ADR-0014, most sensitive first.
 *
 * <p>The tier is declared once, on the DTO record component, because the DTO is the mandatory
 * boundary under {@code AGENTS.md} rule 4 — everything that crosses a module or HTTP boundary
 * passes through one.
 */
public enum Tier {

    /**
     * Biometrics, health and counselling records, caste/community, religion, disability/CWSN,
     * guardian income, EWS/BPL/RTE category, APAAR and Aadhaar references.
     *
     * <p>ADR-0014: "Encrypted at rest. Never logged, at any level. Never in an error message. Every
     * read is audited. Masked by default in the UI, revealed by an explicit permission and a
     * recorded action."
     */
    RESTRICTED,

    /**
     * Student and guardian names, date of birth, address, phone, photographs, marks, fee ledger,
     * attendance.
     *
     * <p>ADR-0014: "Never logged. Permission-gated per ADR-0005. Export is audited."
     */
    CONFIDENTIAL,

    /**
     * Class and section structure, timetables, subjects, fee heads, staff roles, academic calendar.
     *
     * <p>ADR-0014: "Permission-gated. May be logged freely."
     */
    INTERNAL,

    /**
     * School profile, mandatory public disclosure pages, the certificate verification response.
     *
     * <p>ADR-0014: "No authentication required. Deliberately published."
     */
    PUBLIC;

    /**
     * Whether a value at this tier must never reach a log sink or an error message.
     *
     * <p>True for {@link #RESTRICTED} and {@link #CONFIDENTIAL}. Both ADR-0014 rows say "never
     * logged" without qualifying it by level, so this is not a threshold anyone may lower for a
     * debug build.
     */
    public boolean isRedacted() {
        return this == RESTRICTED || this == CONFIDENTIAL;
    }
}
