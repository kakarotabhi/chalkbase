import { Gender, GuardianRelation, StudentStatus } from '../../core/api/models';
import { SelectOption } from '../../shared/components/select/select';

/**
 * What the three student screens share: the words for the closed sets in the contract, and the
 * error codes they branch on.
 *
 * Small on purpose. Anything that belongs to one screen lives with that screen — this file is for
 * the things that must read identically on all three, because a status called "Left the school" on
 * one screen and "Withdrawn" on another is two different facts as far as the office is concerned.
 */

/** The error code a 403 carries (ADR-0007). Branch on this, never on the message. */
export const ACCESS_DENIED = 'PERM_001';

/** A 404. On the detail screen this is a student id that is not (or is no longer) at this school. */
export const NOT_FOUND = 'NF_001';

/**
 * A 409 from the platform's constraint registry, when the student module has not said which clash
 * it was. Kept as the fallback below the specific codes.
 */
export const CONFLICT = 'CONF_001';

/**
 * The student module's own clash codes.
 *
 * Each names one constraint, so a screen can say what actually went wrong rather than wording a
 * bare 409 for whatever write it happened to be doing. That distinction matters most on the
 * enrolment form, where two entirely different mistakes — this child is already enrolled this
 * year, and another child already has that roll number — would otherwise share one sentence that
 * had to hedge between them.
 */
export const DUPLICATE_ADMISSION_NUMBER = 'STU_001';
export const ALREADY_ENROLLED_THIS_SESSION = 'STU_002';
export const ROLL_NUMBER_TAKEN = 'STU_003';
export const GUARDIAN_ALREADY_LINKED = 'STU_004';

/**
 * How a status reads.
 *
 * This field is how a school records that somebody left (ADR-0020 §6). "Inactive" is deliberately
 * vaguer than the other four, because it is the one the school uses when none of them fit.
 */
export const STATUS_LABELS: Readonly<Record<StudentStatus, string>> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  TRANSFERRED: 'Transferred out',
  GRADUATED: 'Graduated',
  WITHDRAWN: 'Withdrawn',
};

export const STATUS_OPTIONS: readonly SelectOption[] = (
  Object.keys(STATUS_LABELS) as StudentStatus[]
).map((status) => ({ value: status, label: STATUS_LABELS[status] }));

export const GENDER_LABELS: Readonly<Record<Gender, string>> = {
  MALE: 'Male',
  FEMALE: 'Female',
  OTHER: 'Other',
};

export const GENDER_OPTIONS: readonly SelectOption[] = (Object.keys(GENDER_LABELS) as Gender[]).map(
  (gender) => ({ value: gender, label: GENDER_LABELS[gender] }),
);

/**
 * What a guardian is to a student.
 *
 * "Local guardian" is a real and separate thing in Indian schools — the relative or family friend
 * in the same town as the school, who is who the office actually rings when a child is unwell and
 * the parents are three states away. Collapsing it into "Guardian" would lose that.
 */
export const RELATION_LABELS: Readonly<Record<GuardianRelation, string>> = {
  FATHER: 'Father',
  MOTHER: 'Mother',
  GUARDIAN: 'Guardian',
  LOCAL_GUARDIAN: 'Local guardian',
  OTHER: 'Other',
};

export const RELATION_OPTIONS: readonly SelectOption[] = (
  Object.keys(RELATION_LABELS) as GuardianRelation[]
).map((relation) => ({ value: relation, label: RELATION_LABELS[relation] }));

/**
 * Reads a value from one of the closed sets above, coping with one the backend added and this
 * build has not caught up with.
 *
 * A user must never be shown `LOCAL_GUARDIAN`. Turning the constant into "Local guardian" is
 * imperfect and legible, which is the right trade for a row that is otherwise correct — the same
 * reasoning as `navLabel`.
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

/**
 * `Class 5 · A` — where a child sits, said the way the office says it.
 *
 * One string rather than two columns, because "which section is this child in" is one question and
 * the class alone is not an answer to it.
 */
export function classAndSection(
  className: string | undefined,
  sectionName: string | undefined,
): string {
  const parts = [className?.trim(), sectionName?.trim()].filter(Boolean);
  return parts.join(' · ');
}
