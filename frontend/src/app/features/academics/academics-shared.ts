/**
 * The two things both academics screens need and neither owns.
 *
 * Small on purpose: shared code between sibling screens earns its place by being the thing that
 * must not drift, and nothing else. Anything that is only about sessions lives with the sessions
 * screen, and anything only about the ladder lives with the classes screen.
 */

/** The error code a 403 carries (ADR-0007). Branch on this, never on the message. */
export const ACCESS_DENIED = 'PERM_001';

/**
 * A school names each academic year once (`uq_academic_session_name`).
 *
 * Worth its own message rather than the generic "could not save": the user has almost certainly
 * meant to edit the year that is already there, and saying so is more useful than saying no.
 */
export const DUPLICATE_SESSION_NAME = 'ACAD_001';

/**
 * Two sections of the same class cannot share a name (`uq_section_name_in_class`).
 *
 * Distinct from the class clash (`ACAD_004`), because the fix is different: a section name only has
 * to be unique inside its own class, so "A" existing elsewhere in the ladder is not the problem.
 */
export const DUPLICATE_SECTION_NAME = 'ACAD_006';

/**
 * `2026-04-01` → `1 Apr 2026`.
 *
 * Re-exported rather than defined here: a date of birth on the student screens needs exactly the
 * same parsing, and the timezone rule inside it is the sort of thing that must not exist twice.
 * The academics screens keep importing it from this file, so nothing about them changed.
 */
export { formatDay } from '../../shared/formatting/day';
