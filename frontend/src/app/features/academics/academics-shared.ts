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

/** How a school reads a date on screen: `1 Apr 2026`. Local, because it is read at the school. */
const DAY = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

/**
 * `2026-04-01` → `1 Apr 2026`.
 *
 * Parsed field by field rather than handed to `new Date(string)`, which reads a bare date as UTC
 * and shifts it by the viewer's offset — in India, five and a half hours, which is enough to turn
 * "1 April" into "31 March" and a session that starts on the wrong day. A value this cannot parse
 * is returned as it arrived rather than rendered as `Invalid Date`.
 */
export function formatDay(day: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day?.trim() ?? '');
  if (!match) {
    return day ?? '';
  }
  const [, year, month, date] = match;
  const parsed = new Date(Number(year), Number(month) - 1, Number(date));
  return Number.isNaN(parsed.getTime()) ? day : DAY.format(parsed);
}
