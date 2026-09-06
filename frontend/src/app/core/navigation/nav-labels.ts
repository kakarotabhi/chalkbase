/**
 * English strings for the translation keys the server sends.
 *
 * `labelKey` is a key, not a sentence (ADR-0008): parent portals will need Hindi and regional
 * languages, and sending display strings from the backend would drag every translation into Java.
 *
 * TODO(i18n): this map is a placeholder for a real catalogue. When `@angular/localize` or an ICU
 * catalogue lands, these entries move into it wholesale and `navLabel` becomes a lookup against
 * the active locale. No i18n library is added for one menu — that decision belongs with the
 * parent portal, which is the first screen that actually needs Hindi.
 */
const NAV_LABELS: Readonly<Record<string, string>> = {
  'nav.dashboard': 'Dashboard',
  'nav.schools': 'Schools',
  'nav.admissions': 'Admissions',
  'nav.students': 'Students',
  'nav.students.all': 'All students',
  'nav.students.guardians': 'Guardians',
  'nav.academics': 'Academics',
  'nav.academics.sessions': 'Academic sessions',
  'nav.academics.classes': 'Classes and sections',
  'nav.attendance': 'Attendance',
  'nav.exams': 'Exams',
  'nav.fees': 'Fees',
  'nav.fees.collect': 'Collect fees',
  'nav.fees.receipts': 'Receipts',
  'nav.fees.defaulters': 'Defaulters',
  'nav.timetable': 'Timetable',
  'nav.communication': 'Communication',
  'nav.transport': 'Transport',
  'nav.hostel': 'Hostel',
  'nav.library': 'Library',
  'nav.staff': 'Staff',
  'nav.reports': 'Reports',
  'nav.settings': 'Settings',
  'nav.settings.access': 'Roles and access',
  'nav.settings.profile': 'School profile',
  'nav.audit': 'Audit log',
  /** Not a server item. The compact bar's own overflow entry (ADR-0010). */
  'nav.more': 'More',
  /** The heading on the More sheet. */
  'nav.more.heading': 'All sections',
  'nav.more.close': 'Close menu',
};

/** Keys already reported, so a menu of twenty unknown items logs twenty lines and not twenty a second. */
const reported = new Set<string>();

/**
 * Turns a `labelKey` into something to show a user.
 *
 * Three sources, in order:
 *
 * 1. `override` — the school's own renaming ("Fees & Dues"). That is per-school data rather than a
 *    translation (ADR-0006), so it beats anything in the catalogue.
 * 2. The catalogue.
 * 3. A readable guess from the key itself.
 *
 * The third case matters more than it looks. A key the backend added and this app has not caught
 * up with must never reach a user as `nav.fees.collect`; "Collect" is imperfect and legible, which
 * is the right trade for a menu item that is otherwise correct.
 */
export function navLabel(labelKey: string, override?: string | null): string {
  const trimmedOverride = override?.trim();
  if (trimmedOverride) {
    return trimmedOverride;
  }

  const known = Object.prototype.hasOwnProperty.call(NAV_LABELS, labelKey)
    ? NAV_LABELS[labelKey]
    : undefined;
  if (known) {
    return known;
  }

  if (!reported.has(labelKey)) {
    reported.add(labelKey);
    console.warn(`[nav] no label for "${labelKey}" — showing a guess from the key`);
  }
  return humanise(labelKey);
}

/** `nav.fees.collect` → `Collect`; `nav.transport_routes` → `Transport routes`. */
function humanise(labelKey: string): string {
  const last = labelKey.split('.').filter(Boolean).at(-1) ?? '';
  const words = last.replace(/[_-]+/g, ' ').trim();
  if (!words) {
    // Nothing usable in the key at all. Say so rather than rendering an empty tap target.
    return 'Untitled';
  }
  return words.charAt(0).toUpperCase() + words.slice(1);
}
