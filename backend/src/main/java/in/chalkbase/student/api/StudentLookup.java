package in.chalkbase.student.api;

import java.util.List;
import java.util.UUID;

/**
 * How another module asks this one about the roster, without a join and without reaching into
 * {@code student.domain} (ADR-0011, module map).
 *
 * <p>Added for the first basic dashboard (Phase 1), which is the first caller outside this module
 * to need anything from it in bulk. {@code academics.api.AcademicsLookup} is the precedent this
 * follows: read-only, scoped to the school bound to the request by {@code search_path} (no school
 * argument anywhere, and one taking one is a review blocker), and answering counts rather than
 * handing back {@link in.chalkbase.student.domain.Student} or
 * {@link in.chalkbase.student.domain.Guardian} rows — a caller outside this module gets a number,
 * never a name, date of birth or phone number, which keeps a dashboard tile from becoming a second,
 * unaudited way to read Confidential data this module already guards behind
 * {@code student:student:read} and {@code student:guardian:read}.
 *
 * <p><strong>This is a contract change to this module.</strong> It did not need a cross-module read
 * interface before; the dashboard is what makes it need one, the same way {@code AcademicsLookup}
 * was added when {@code student} first needed to name a session or a class it does not own.
 *
 * <p>Every count is scoped to <em>active</em> enrolments only — a student who left the school two
 * years ago does not belong in "students enrolled" today, however their historical rows still read.
 */
public interface StudentLookup {

    /**
     * How many students hold a live enrolment in {@code academicSessionId}.
     *
     * @param academicSessionId the year to count, or null — a null session has no enrolments by
     *     definition, and the caller gets {@code 0} rather than an exception, the same way
     *     {@code AcademicsLookup} answers "not in this school" with an empty result rather than a
     *     throw
     */
    long activeEnrolmentCount(UUID academicSessionId);

    /**
     * Live enrolment counts for {@code academicSessionId}, one row per section that has at least
     * one — a section with none is simply absent, not present with a zero.
     *
     * <p>Keyed by section rather than by class: see {@link SectionEnrolmentCount}. A caller wanting
     * totals by class resolves each {@code sectionId} through
     * {@code AcademicsLookup.sections(Collection)} and sums.
     */
    List<SectionEnrolmentCount> activeEnrolmentCountsBySection(UUID academicSessionId);

    /**
     * How many students with a live enrolment in {@code academicSessionId} have no guardian linked
     * at all — not "no primary contact set", which is a smaller and less urgent gap; this is a
     * child with nobody on record to call.
     */
    long activeStudentsWithoutAGuardianCount(UUID academicSessionId);

    /**
     * How many guardians in the directory are linked to no student at all — a record that exists
     * only because it was created and then never attached, or was detached from the one child it
     * held (ADR-0020 §6 keeps the person on file when that happens).
     */
    long guardiansWithoutAStudentCount();
}
