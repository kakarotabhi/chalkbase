import { BadgeTone } from '../../shared/components/badge/badge';
import { CircularStatus } from '../../core/api/models';

/** The code the backend answers when a permission this screen needs is missing. */
export const ACCESS_DENIED = 'PERM_001';

/** The code the backend answers when a circular id does not resolve in this school. */
export const NOT_FOUND = 'NF_001';

/** `Class 5 · A`, the same shape the attendance and student screens read a section as. */
export function classAndSection(className: string, sectionName: string): string {
  return `${className} · ${sectionName}`;
}

const STATUS_LABEL: Readonly<Record<CircularStatus, string>> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
};

export function circularStatusLabel(status: CircularStatus): string {
  return STATUS_LABEL[status] ?? status;
}

const STATUS_TONE: Readonly<Record<CircularStatus, BadgeTone>> = {
  DRAFT: 'neutral',
  PUBLISHED: 'success',
};

export function circularStatusTone(status: CircularStatus): BadgeTone {
  return STATUS_TONE[status] ?? 'neutral';
}

/**
 * A delivery or acknowledgement instant, in the school's own zone rather than the reader's
 * ([ADR-0032](../../../../docs/architecture/adr/0032-school-timezone.md)) — the same reasoning
 * `audit-log.ts` gives: a circular delivered at 21:00 in Mumbai must not print as 15:30 to a
 * reader opening this screen from abroad.
 */
export function circularTimeFormat(timeZone: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  });
}

/** Renders one ISO instant with an already-built formatter, or the raw string if it does not parse. */
export function formatCircularTime(format: Intl.DateTimeFormat, iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : format.format(parsed);
}
