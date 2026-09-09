package in.chalkbase.admission.domain;

/**
 * Where one enquiry stands, before it ever becomes an application.
 *
 * <p>Phase 0 decision §5 draws the admission pipeline as
 * {@code Enquiry → Application → Document verification → Screening outcome → Approval →
 * Admission fee → Student record created}. FR-021's nine stages — submitted, document pending,
 * interaction scheduled, selected, waitlisted, rejected, offered, admitted, withdrawn — describe
 * the <strong>Application</strong> box of that pipeline, which this lane deliberately does not
 * build (see the pull request that introduced this file for what "enquiry management" covers and
 * does not). Reusing those names for an enquiry would misdescribe a row that has not reached that
 * box yet.
 *
 * <p>This enum is instead what happens to a row sitting in the pipeline's <em>first</em> box: a
 * front office logs it, a counsellor follows it up some number of times, and it eventually either
 * crosses into {@code Application} or does not. Four values — the ordinary shape of a sales funnel,
 * and nothing more exotic (ADR-0006: this earns no configurability tier of its own until a second
 * school disagrees with it):
 *
 * <ul>
 *   <li>{@link #NEW} — captured, nobody has followed up yet.
 *   <li>{@link #IN_PROGRESS} — a counsellor has made at least one follow-up contact.
 *   <li>{@link #CONVERTED} — the family is going ahead. This <em>is</em> the pipeline's own arrow
 *       into {@code Application} — the application record itself is a later lane's work, and
 *       converting this enquiry does not create one.
 *   <li>{@link #LOST} — the family will not be proceeding (unresponsive, chose elsewhere, withdrew).
 * </ul>
 */
public enum EnquiryStatus {
    NEW,
    IN_PROGRESS,
    CONVERTED,
    LOST;

    /** {@link #CONVERTED} and {@link #LOST} both end this row's own life; nothing follows either. */
    public boolean isClosed() {
        return this == CONVERTED || this == LOST;
    }
}
