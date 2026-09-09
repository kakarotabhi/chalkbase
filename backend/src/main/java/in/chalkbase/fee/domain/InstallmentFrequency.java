package in.chalkbase.fee.domain;

/** FR-077's six fee schedules, verbatim. What a {@link FeeStructureItem} tells a collection screen about itself. */
public enum InstallmentFrequency {
    ONE_TIME,
    MONTHLY,
    QUARTERLY,
    TERM_WISE,
    ANNUAL,
    CUSTOM
}
