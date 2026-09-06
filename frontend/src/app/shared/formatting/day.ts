/**
 * A calendar day, as a school reads it.
 *
 * Lives here rather than beside one feature because two of them now show dates a user picked —
 * academics shows the start and end of a session, students show a date of birth and a date of
 * admission — and the parsing rule below is the sort of thing that must not exist twice. A second
 * copy is a second chance to get the timezone wrong in only one of them.
 */

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
 * "1 April" into "31 March" and a session that starts on the wrong day. On a date of birth it is
 * enough to put a child's birthday on the wrong date on a certificate.
 *
 * A value this cannot parse is returned as it arrived rather than rendered as `Invalid Date`.
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

/**
 * Today, as `yyyy-MM-dd` in the viewer's own calendar.
 *
 * `toISOString().slice(0, 10)` is the usual way to write this and it is wrong east of Greenwich:
 * it converts to UTC first, so before 05:30 IST it returns yesterday. A form that refuses a date
 * of birth as "not in the past" on a date that is in fact in the past would be unexplainable to
 * the clerk looking at it.
 */
export function today(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const date = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${date}`;
}
