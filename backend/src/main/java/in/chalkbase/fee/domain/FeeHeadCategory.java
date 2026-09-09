package in.chalkbase.fee.domain;

/**
 * The seven fee heads Phase 0 §4 confirmed (07-phase-0-decisions.md §6, ADR-0012). A closed set —
 * a school does not invent an eighth category, though it may name several {@link FeeHead} rows
 * under the same one (a "Sports Fee" and an "Annual Day Fee", both {@link #ACTIVITY}).
 *
 * <p>{@link #ANNUAL_DEVELOPMENT} is the one category that may carry a cap, because Delhi's DoE caps
 * Development Fee as a proportion of tuition — see {@link FeeHead#getCapPercentOfTuition()}.
 */
public enum FeeHeadCategory {
    TUITION,
    ADMISSION,
    ANNUAL_DEVELOPMENT,
    TRANSPORT,
    EXAM,
    ACTIVITY,
    LATE_FEE
}
