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

/**
 * An instant, as a school reads it: `9 Sept 2026, 15:35`, in the *school's* time zone.
 *
 * A timestamp off the wire carries no zone of its own — it is UTC — so rendering it with no zone
 * named reads correctly only for a viewer sitting in the same zone as the school, and prints the
 * device's own local time everywhere else. That is wrong for the same reason a bare
 * `new Date(string)` is wrong for a calendar day (see {@link formatDay}): a reader elsewhere sees
 * a plausible but different time and has no way to tell. The school's own zone is what
 * `SessionStore.schoolTimezone` carries off `GET /api/me` (ADR-0032) — build the formatter with it
 * once per screen and reuse it for every row, rather than reading the zone per row.
 */
export function instantFormat(timeZone: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  });
}

/**
 * Renders one ISO instant with an already-built {@link instantFormat}. A value this cannot parse
 * is shown as it arrived rather than as `Invalid Date` — the same rule {@link formatDay} follows.
 */
export function formatInstant(format: Intl.DateTimeFormat, iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : format.format(parsed);
}
