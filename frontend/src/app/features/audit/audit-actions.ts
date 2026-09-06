import { SelectOption } from '../../shared/components/select/select';

/**
 * English wording for the audit verbs this build knows about.
 *
 * The same shape as `nav-labels.ts`, and for the same reason: the backend's set is open. Audit
 * actions are string constants rather than an enum precisely so a module can name its own verb —
 * `FEE_RECEIPT_REVERSED`, `STUDENT_PROMOTED` — without editing shared code, which means **this map
 * will always be incomplete and the screen has to stay legible anyway**. `actionLabel` guarantees
 * that; adding an entry here only improves the wording.
 *
 * The wording is a phrase, not a shout: a reader is scanning a column of these, and `Signed in`
 * reads where `LOGIN_SUCCEEDED` has to be decoded.
 *
 * TODO(i18n): these are display strings, and they move into the catalogue alongside `NAV_LABELS`
 * when one exists. No i18n library is added for one screen.
 */
const ACTION_LABELS: Readonly<Record<string, string>> = {
  // Security events. Recorded in their own transaction on the backend.
  LOGIN_SUCCEEDED: 'Signed in',
  LOGIN_FAILED: 'Sign-in failed',
  ACCOUNT_LOCKED: 'Account locked',
  LOGOUT: 'Signed out',
  PASSWORD_CHANGED: 'Password changed',
  PERMISSION_DENIED: 'Permission denied',
  DATA_EXPORTED: 'Data exported',
  // Data changes. Recorded in the transaction that made the change.
  ENTITY_CREATED: 'Record created',
  ENTITY_UPDATED: 'Record updated',
  ENTITY_DELETED: 'Record deleted',
};

/**
 * The actions offered in the filter, in the order a reader looks for them: what happened to
 * someone's access first, then what happened to the data.
 *
 * TODO(reference-data): hardcoded, exactly like the board and state lists on the school profile
 * and for the same reason — the set a school's log actually contains is data, and there is no
 * endpoint that reports it. A module that names its own verb will emit rows this filter cannot
 * select, which is a gap and not a bug: the rows are still listed, still labelled, and still
 * reachable by narrowing on the actor or the date instead. When the backend can report its
 * catalogue (or the distinct actions in this school's log), this list is replaced by that call.
 */
const FILTERABLE_ACTIONS: readonly string[] = [
  'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
  'ACCOUNT_LOCKED',
  'LOGOUT',
  'PASSWORD_CHANGED',
  'PERMISSION_DENIED',
  'DATA_EXPORTED',
  'ENTITY_CREATED',
  'ENTITY_UPDATED',
  'ENTITY_DELETED',
];

/** The filter's options, labelled the same way the rows are so the two cannot read differently. */
export const AUDIT_ACTION_OPTIONS: readonly SelectOption[] = FILTERABLE_ACTIONS.map((action) => ({
  value: action,
  label: actionLabel(action),
}));

/** Keys already reported, so a page of twenty unknown actions logs twenty lines and not twenty a second. */
const reported = new Set<string>();

/**
 * Turns an audit action into something to show a user.
 *
 * The fallback is the point. A verb a module added and this build has not caught up with must
 * never reach a reader as `ENTITY_UPDATED`, and must never reach them as nothing at all — an audit
 * log with a blank cell in it is one nobody can testify from. `Student promoted` is a guess, it is
 * legible, and it is the right trade for a row that is otherwise entirely correct.
 */
export function actionLabel(action: string): string {
  const known = Object.prototype.hasOwnProperty.call(ACTION_LABELS, action)
    ? ACTION_LABELS[action]
    : undefined;
  if (known) {
    return known;
  }

  const trimmed = action?.trim() ?? '';
  if (!trimmed) {
    // The backend rejects a blank action, so this cannot come from the API — but a blank cell in
    // an audit log is the one thing worse than an ugly one, so it is named rather than left empty.
    return 'Unnamed action';
  }

  if (!reported.has(trimmed)) {
    reported.add(trimmed);
    console.warn(`[audit] no label for action "${trimmed}" — showing a guess from the name`);
  }
  return humaniseConstant(trimmed);
}

/**
 * What an event acted on — `entityType`, e.g. `FEE_RECEIPT` → `Fee receipt`.
 *
 * Open in the same way `action` is: it is whatever the calling module named its own kind of thing,
 * so there is no map to look it up in and no point pretending otherwise. Sentence case rather than
 * the constant, because a column of SHOUTED WORDS is slower to read than a phrase.
 */
export function entityLabel(entityType: string): string {
  const trimmed = entityType?.trim() ?? '';
  return trimmed ? humaniseConstant(trimmed) : '';
}

/**
 * `FEE_RECEIPT_REVERSED` → `Fee receipt reversed`. Sentence case, not Title Case: these sit in a
 * column being read, and a row of Capitalised Words is slower to scan than a phrase.
 */
function humaniseConstant(action: string): string {
  const words = action.replace(/[_-]+/g, ' ').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * A changed field's name, made readable — `addressLine1` → `Address line 1`.
 *
 * Cosmetic only, and it must stay that way. These are the names of the fields that changed and
 * nothing else: ADR-0014 means no value was recorded, so there is nothing here to pair a name
 * with, and this function must never be given something to render as `name → value`.
 *
 * A name it cannot improve is returned as it arrived rather than mangled, because a field name a
 * reader can look up beats a prettier one they cannot.
 */
export function fieldLabel(field: string): string {
  const trimmed = field?.trim() ?? '';
  if (!trimmed) {
    return '';
  }

  const spaced = trimmed
    // `guardian.phone` is one nested name, and reads as one phrase.
    .replace(/\./g, ' ')
    .replace(/[_-]+/g, ' ')
    // `addressLine1` → `address Line 1`; `pincode` is left alone.
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Za-z])(\d)/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim();

  const lowered = spaced
    .split(' ')
    // An acronym a school would recognise — UDISE, APAAR, PAN — keeps its case; anything else is
    // lowercased so the phrase reads as a phrase.
    .map((word) => (isAcronym(word) ? word : word.toLowerCase()))
    .join(' ');

  return lowered.charAt(0).toUpperCase() + lowered.slice(1);
}

function isAcronym(word: string): boolean {
  return word.length > 1 && word === word.toUpperCase() && /[A-Z]/.test(word);
}
