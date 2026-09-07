package in.chalkbase.school.domain;

/**
 * Examination board a school is affiliated to.
 *
 * <p>ADR-0006 lists boards among Tier-1 master data, and ADR-0029 looked at moving this to a
 * {@code public} table the way states were and chose not to: {@code Board} is already embedded as a
 * typed enum in {@code CreateSchoolRequest}, {@code BootstrapSchoolRequest} and two response
 * records, and the value is a {@code varchar} check-constrained column on {@code school} and {@code
 * school_profile}. Moving it to data would mean a migration touching all of those plus a backfill of
 * every existing school's stored value, for a list that changes only when a new curriculum body
 * appears in India — rarer than a state being renamed and not worth the risk this lane was scoped to
 * avoid (see {@code docs/status.md}'s note on modules that already name a non-exposed enum type).
 *
 * <p>{@link #label} exists so the frontend does not keep its own copy of this same text — see
 * {@code SchoolController#boards}.
 */
public enum Board {
    CBSE("CBSE"),
    CISCE("CISCE (ICSE / ISC)"),
    STATE("State board"),
    IB("International Baccalaureate"),
    CAIE("Cambridge (CAIE)"),
    OTHER("Other");

    private final String label;

    Board(String label) {
        this.label = label;
    }

    /** The name Indian schools actually use for this board, not the wire constant. */
    public String label() {
        return label;
    }
}
