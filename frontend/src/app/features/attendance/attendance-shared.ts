import { AttendanceStatus } from '../../core/api/models';
import { BadgeTone } from '../../shared/components/badge/badge';

/** The code the backend answers when a permission this screen needs is missing. */
export const ACCESS_DENIED = 'PERM_001';

/** One status a teacher may pick, in the order it reads best on a phone: the common case first. */
export interface StatusOption {
  readonly value: AttendanceStatus;
  /** Fits a 44px-tall button without wrapping. */
  readonly shortLabel: string;
  readonly fullLabel: string;
}

/**
 * The six marks Phase 0 decision 8 settled on, present first because it is what "mark all present"
 * sets and what most students end up as on an ordinary day.
 */
export const STATUS_OPTIONS: readonly StatusOption[] = [
  { value: 'PRESENT', shortLabel: 'P', fullLabel: 'Present' },
  { value: 'ABSENT', shortLabel: 'A', fullLabel: 'Absent' },
  { value: 'LATE', shortLabel: 'L', fullLabel: 'Late' },
  { value: 'HALF_DAY', shortLabel: 'HD', fullLabel: 'Half day' },
  { value: 'EXCUSED_LEAVE', shortLabel: 'EL', fullLabel: 'Excused leave' },
  { value: 'HOLIDAY', shortLabel: 'H', fullLabel: 'Holiday' },
];

const LABEL_BY_STATUS: Readonly<Record<AttendanceStatus, string>> = Object.fromEntries(
  STATUS_OPTIONS.map((option) => [option.value, option.fullLabel]),
) as Record<AttendanceStatus, string>;

export function statusLabel(status: AttendanceStatus): string {
  return LABEL_BY_STATUS[status] ?? status;
}

/** How a mark reads as a badge. Late and half day share a tone — both are a partial present. */
const TONE_BY_STATUS: Readonly<Record<AttendanceStatus, BadgeTone>> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  LATE: 'warning',
  HALF_DAY: 'warning',
  EXCUSED_LEAVE: 'info',
  HOLIDAY: 'neutral',
};

/** `null` — nothing marked yet — reads as neutral, the same as a holiday: present, but unstated. */
export function statusTone(status: AttendanceStatus | null): BadgeTone {
  return status ? TONE_BY_STATUS[status] : 'neutral';
}

/** `2026-09-07` in the viewer's own calendar day, never shifted by a UTC offset. */
export function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** `Class 5 · A`, the same shape the student list's section filter reads. */
export function classAndSection(className: string, sectionName: string): string {
  return `${className} · ${sectionName}`;
}
