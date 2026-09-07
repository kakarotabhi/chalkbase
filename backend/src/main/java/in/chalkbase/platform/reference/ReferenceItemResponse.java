package in.chalkbase.platform.reference;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;

/**
 * One choice in a reference list — a state, a board — shaped so a frontend {@code Select} can use
 * it directly.
 *
 * <p>{@code value} is what a form submits and what the backend already stores: for a state this is
 * the name itself ({@code school_profile.state} is, and remains, a plain string, matching what it
 * stored before this endpoint existed), not {@code code} — {@code code} is an internal seeding key
 * (see {@code IndianState}) that nothing on the wire needs. For a board it is the enum constant's
 * name, e.g. {@code "CBSE"}, the same string {@code Board} already serialises as.
 *
 * <p>{@code label} is what a person reads. For a state the two happen to be equal — a state has no
 * separate short form — for a board they differ, e.g. {@code CISCE} / {@code "CISCE (ICSE / ISC)"}.
 *
 * <p><strong>{@link Tier#PUBLIC}, stated rather than left off (ADR-0029).</strong> This is the least
 * sensitive tier ADR-0014 has — the same one {@code Board} already carries on {@code
 * SchoolProfileResponse} — and it is worth saying explicitly: a state name or a board's label is
 * identical for every school and every reader, fit to log, cache and publish freely.
 */
public record ReferenceItemResponse(
        @Classification(Tier.PUBLIC) String value,
        @Classification(Tier.PUBLIC) String label) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
