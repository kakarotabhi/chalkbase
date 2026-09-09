package in.chalkbase.fee.domain;

/**
 * The concession kinds FR-078 names: sibling discount, staff-child discount, management quota
 * concession, RTE/EWS tagging, and the general "concessions, scholarships" it opens with —
 * {@link #SCHOLARSHIP} for a named scholarship and {@link #OTHER} for anything a school offers
 * that does not fit the named categories.
 *
 * <p>This is a catalogue entry, not a grant. See {@code in.chalkbase.fee.package-info} for why
 * defining a concession type here and applying one to a student's charge are different pieces of
 * work, and why only the first is in this lane's scope.
 */
public enum FeeConcessionCategory {
    SIBLING,
    STAFF_CHILD,
    MANAGEMENT_QUOTA,
    RTE_EWS,
    SCHOLARSHIP,
    OTHER
}
