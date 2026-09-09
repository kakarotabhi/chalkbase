import { SelectOption } from '../../shared/components/select/select';

/** `PlatformErrorCode.ACCESS_DENIED` (`PERM_001`). Shared across every fees screen. */
export const ACCESS_DENIED = 'PERM_001';

/** `FeeErrorCode.DUPLICATE_FEE_HEAD_NAME`. */
export const DUPLICATE_FEE_HEAD_NAME = 'FEE_001';

/** `FeeErrorCode.DUPLICATE_CONCESSION_TYPE_NAME`. */
export const DUPLICATE_CONCESSION_TYPE_NAME = 'FEE_002';

/** `FeeErrorCode.CAP_PERCENT_NOT_APPLICABLE`. */
export const CAP_PERCENT_NOT_APPLICABLE = 'FEE_003';

/** `FeeErrorCode.DEVELOPMENT_FEE_EXCEEDS_CAP`. */
export const DEVELOPMENT_FEE_EXCEEDS_CAP = 'FEE_004';

/** `FeeErrorCode.DUPLICATE_HEAD_IN_STRUCTURE`. */
export const DUPLICATE_HEAD_IN_STRUCTURE = 'FEE_005';

/** `FeeErrorCode.INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT`. */
export const INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT = 'FEE_006';

/** `FeeErrorCode.DUPLICATE_INSTALLMENT_DATE`. */
export const DUPLICATE_INSTALLMENT_DATE = 'FEE_007';

/** `FeeErrorCode.INSTALLMENT_BEFORE_SESSION_START`. */
export const INSTALLMENT_BEFORE_SESSION_START = 'FEE_008';

/** `FeeErrorCode.STRUCTURE_SESSION_CLOSED` — the lock ADR-0012 rule 6 exists for. */
export const STRUCTURE_SESSION_CLOSED = 'FEE_009';

/** `FeeErrorCode.INACTIVE_FEE_HEAD`. */
export const INACTIVE_FEE_HEAD = 'FEE_010';

/** `FeeErrorCode.CANNOT_COPY_SESSION_INTO_ITSELF`. */
export const CANNOT_COPY_SESSION_INTO_ITSELF = 'FEE_011';

/** The seven fee heads Phase 0 §4 confirmed — a closed set, never school-editable. */
export const FEE_HEAD_CATEGORY_OPTIONS: readonly SelectOption[] = [
  { value: 'TUITION', label: 'Tuition' },
  { value: 'ADMISSION', label: 'Admission' },
  { value: 'ANNUAL_DEVELOPMENT', label: 'Annual / Development' },
  { value: 'TRANSPORT', label: 'Transport' },
  { value: 'EXAM', label: 'Exam' },
  { value: 'ACTIVITY', label: 'Activity' },
  { value: 'LATE_FEE', label: 'Late fee' },
];

/** FR-078's six concession kinds. */
export const FEE_CONCESSION_CATEGORY_OPTIONS: readonly SelectOption[] = [
  { value: 'SIBLING', label: 'Sibling discount' },
  { value: 'STAFF_CHILD', label: 'Staff-child discount' },
  { value: 'MANAGEMENT_QUOTA', label: 'Management quota' },
  { value: 'RTE_EWS', label: 'RTE / EWS' },
  { value: 'SCHOLARSHIP', label: 'Scholarship' },
  { value: 'OTHER', label: 'Other' },
];

/** FR-077's six fee schedules. */
export const INSTALLMENT_FREQUENCY_OPTIONS: readonly SelectOption[] = [
  { value: 'ONE_TIME', label: 'One-time' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'QUARTERLY', label: 'Quarterly' },
  { value: 'TERM_WISE', label: 'Term-wise' },
  { value: 'ANNUAL', label: 'Annual' },
  { value: 'CUSTOM', label: 'Custom' },
];

/** A short human label for a fee head category, for a row that has no `cb-select` to show it. */
export function feeHeadCategoryLabel(category: string): string {
  return FEE_HEAD_CATEGORY_OPTIONS.find((option) => option.value === category)?.label ?? category;
}

/** As {@link feeHeadCategoryLabel}, for a concession category. */
export function feeConcessionCategoryLabel(category: string): string {
  return (
    FEE_CONCESSION_CATEGORY_OPTIONS.find((option) => option.value === category)?.label ?? category
  );
}

/** As {@link feeHeadCategoryLabel}, for an installment frequency. */
export function installmentFrequencyLabel(frequency: string): string {
  return (
    INSTALLMENT_FREQUENCY_OPTIONS.find((option) => option.value === frequency)?.label ?? frequency
  );
}

/** `12000.00` → `₹12,000.00`. Indian digit grouping, via the locale rather than a hand-rolled regex. */
export function formatRupees(amount: string | number): string {
  const value = typeof amount === 'string' ? Number(amount) : amount;
  if (!Number.isFinite(value)) {
    return String(amount);
  }
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(value);
}
