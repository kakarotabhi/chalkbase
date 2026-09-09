import { EnquirySource, EnquiryStatus } from '../../core/api/models';
import { BadgeTone } from '../../shared/components/badge/badge';
import { SelectOption } from '../../shared/components/select/select';

/**
 * What every admissions screen shares: the words for the two closed sets in the contract, and the
 * error codes they branch on. Small on purpose, the same reasoning `students-shared.ts` gives for
 * its own file: a status called "Converted" on one screen and "Admitted" on another is two
 * different facts as far as the front office is concerned.
 */

/** The error code a 403 carries (ADR-0007). Branch on this, never on the message. */
export const ACCESS_DENIED = 'PERM_001';

/** A 404 — an enquiry, a class or a counsellor id that does not (or no longer) resolve. */
export const NOT_FOUND = 'NF_001';

/** The admission module's own codes (`AdmissionErrorCode`) — see `docs/api/README.md` for each condition. */
export const ENQUIRY_CLOSED = 'ADM_001';
export const COUNSELLOR_NOT_ACTIVE = 'ADM_002';
export const STATUS_CANNOT_REOPEN_TO_NEW = 'ADM_003';
export const NEXT_FOLLOW_UP_DATE_REQUIRED = 'ADM_004';

/** Where an enquiry came from (FR-016), in the order a front-office form reads best. */
export const SOURCE_LABELS: Readonly<Record<EnquirySource, string>> = {
  WALK_IN: 'Walk-in',
  PHONE: 'Phone call',
  WEBSITE: 'Website',
  REFERRAL: 'Referral',
  CAMPAIGN: 'Campaign',
  IMPORTED: 'Imported',
};

export const SOURCE_OPTIONS: readonly SelectOption[] = (
  Object.keys(SOURCE_LABELS) as EnquirySource[]
).map((source) => ({ value: source, label: SOURCE_LABELS[source] }));

/** Where an enquiry stands (see the backend's `EnquiryStatus` for why these four, not FR-021's nine). */
export const STATUS_LABELS: Readonly<Record<EnquiryStatus, string>> = {
  NEW: 'New',
  IN_PROGRESS: 'In progress',
  CONVERTED: 'Converted',
  LOST: 'Lost',
};

export const STATUS_OPTIONS: readonly SelectOption[] = (
  Object.keys(STATUS_LABELS) as EnquiryStatus[]
).map((status) => ({ value: status, label: STATUS_LABELS[status] }));

/** How a status reads as a badge. Converted is the good outcome; lost is not a fault, just closed. */
export const STATUS_TONES: Readonly<Record<EnquiryStatus, BadgeTone>> = {
  NEW: 'info',
  IN_PROGRESS: 'warning',
  CONVERTED: 'success',
  LOST: 'neutral',
};

/** A status a follow-up may move an enquiry to. Never `NEW` — see `AdmissionErrorCode.STATUS_CANNOT_REOPEN_TO_NEW`. */
export const FOLLOW_UP_RESULT_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'No change' },
  { value: 'IN_PROGRESS', label: STATUS_LABELS.IN_PROGRESS },
  { value: 'CONVERTED', label: STATUS_LABELS.CONVERTED },
  { value: 'LOST', label: STATUS_LABELS.LOST },
];

/**
 * Reads a value from one of the closed sets above, coping with one the backend added and this
 * build has not caught up with — the same fallback `students-shared.ts#labelFor` uses.
 */
export function labelFor<T extends string>(
  labels: Readonly<Record<T, string>>,
  value: T | string,
): string {
  const known = (labels as Readonly<Record<string, string>>)[value];
  if (known) {
    return known;
  }
  const words = String(value ?? '')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : '—';
}
