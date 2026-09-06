import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { apiErrorCode } from '../../core/api/api-error';
import { AUDIT_PAGE_SIZE, AuditApi } from '../../core/api/audit-api';
import { AuditEvent, AuditOutcome } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { AUDIT_ACTION_OPTIONS, actionLabel, entityLabel, fieldLabel } from './audit-actions';

/** The error code a 403 carries (ADR-0007). Branch on this, never on the message. */
const ACCESS_DENIED = 'PERM_001';

/**
 * "Any action" is a real, selectable option rather than `cb-select`'s placeholder.
 *
 * The placeholder is disabled once something is chosen — right for a required field, wrong for a
 * filter, where going back to "everything" is the most common thing a reader does next.
 */
const ACTION_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Any action' },
  ...AUDIT_ACTION_OPTIONS,
];

/** How an outcome is worded. Never colour alone — a badge carries its word too (ADR-0009). */
const OUTCOME_LABELS: Readonly<Record<AuditOutcome, string>> = {
  SUCCESS: 'Succeeded',
  FAILURE: 'Failed',
  DENIED: 'Denied',
};

/** The day and time as a reader scans them. Local, because the log is read at the school. */
const SHORT_TIME = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/**
 * The same instant, said in full and with the zone named.
 *
 * The zone is not decoration. An audit row is evidence, and "14:32" is only evidence if a reader
 * can tell which 14:32 it was — so the detail panel says the zone out loud, even though the column
 * above it does not have room to.
 */
const FULL_TIME = new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'full',
  timeStyle: 'long',
});

/** One row, with everything the template needs already decided. */
interface AuditRow {
  readonly id: string;
  readonly occurredAt: string;
  readonly occurredAtFull: string;
  readonly occurredAtIso: string;
  readonly actorId: string | null;
  readonly actorName: string;
  readonly actorRoles: string;
  readonly action: string;
  readonly outcome: AuditOutcome;
  readonly outcomeLabel: string;
  /** The full class list, decided here so the template does not call a method per row per pass. */
  readonly outcomeClass: string;
  readonly record: string | null;
  readonly changedFields: readonly string[];
  readonly ipAddress: string;
  readonly userAgent: string;
  readonly traceId: string;
}

/** Who the actor filter is currently pinned to. The name is for the chip; the id does the work. */
interface ActorFilter {
  readonly id: string;
  readonly name: string;
}

/**
 * The school's audit log: who did what here, and when (ADR-0018, FR-008).
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008 is explicit that a menu is a convenience and never an authorization control, and warns
 * against re-deriving the authorization model client-side — which is exactly what a
 * `canActivate` checking `platform:audit:read` would be. The server already leaves this item out
 * of the menu for anyone without the permission, and `GET /api/audit` enforces it independently.
 * So someone who types `/audit` reaches this screen, the API answers 403, and the screen says so
 * calmly. No crash, no redirect, and no second copy of the permission model to drift.
 *
 * ## The date range is inclusive at both ends, and the API is not
 *
 * See `instantAfter`. This is the one piece of arithmetic on this screen that must not be wrong.
 *
 * ## Values are never shown, because none were recorded
 *
 * `changedFields` is a list of field NAMES (ADR-0014). Nothing here may render them as
 * `name → value`, and nothing may imply a value is available behind a click: the audit table does
 * not hold one, and it is not an oversight that it does not.
 */
@Component({
  selector: 'cb-audit-log',
  imports: [ReactiveFormsModule, FormField, Select, TextInput, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.scss',
})
export class AuditLog {
  private readonly auditApi = inject(AuditApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly actionOptions = ACTION_OPTIONS;
  protected readonly pageSize = AUDIT_PAGE_SIZE;

  /** `from` and `to` are `yyyy-MM-dd` — a day the user picked, not an instant. */
  protected readonly filters = this.formBuilder.group({
    action: '',
    from: '',
    to: '',
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly AuditEvent[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly expandedId = signal<string | null>(null);
  protected readonly actor = signal<ActorFilter | null>(null);

  /**
   * Only the request that answers last should be allowed to paint. Two filter changes in quick
   * succession can otherwise land out of order and leave the screen showing the earlier answer
   * under the later filter — on an audit log, a page of rows that does not match the filter above
   * it is not a cosmetic problem.
   */
  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly filtered = computed(() => {
    const { action, from, to } = this.filters.getRawValue();
    return this.actor() !== null || action !== '' || from !== '' || to !== '';
  });

  /** Set when the range reads backwards. Shown against `To`, and nothing is requested. */
  protected readonly rangeError = signal<string | null>(null);

  protected readonly view = computed<readonly AuditRow[]>(() =>
    this.rows().map((event) => ({
      id: event.id,
      occurredAt: formatTime(SHORT_TIME, event.occurredAt),
      occurredAtFull: formatTime(FULL_TIME, event.occurredAt),
      occurredAtIso: event.occurredAt,
      actorId: event.actorId,
      // A failed sign-in has no actor at all — the account was never established. Saying so beats
      // an empty cell, which reads as data we lost rather than data that never existed.
      actorName: event.actorName?.trim() || 'Not signed in',
      actorRoles: event.actorRoles.join(', '),
      action: actionLabel(event.action),
      outcome: event.outcome,
      outcomeLabel: OUTCOME_LABELS[event.outcome] ?? event.outcome,
      outcomeClass: `outcome outcome--${event.outcome.toLowerCase()}`,
      record: describeRecord(event),
      changedFields: event.changedFields.map(fieldLabel).filter((name) => name !== ''),
      ipAddress: event.ipAddress?.trim() || 'Not recorded',
      userAgent: event.userAgent?.trim() || 'Not recorded',
      traceId: event.traceId?.trim() || 'Not recorded',
    })),
  );

  /** "1–25 of 137". Counted off the rows actually received, so the last page reads correctly. */
  protected readonly position = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return '';
    }
    const first = this.page() * this.pageSize + 1;
    const last = first + this.rows().length - 1;
    return `${first}–${last} of ${total}`;
  });

  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  constructor() {
    this.load();

    // Every filter change starts again from the first page. Staying on page four of the previous
    // filter would show either the wrong rows or an empty page, and both look like a broken screen.
    this.filters.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
  }

  protected reload(): void {
    this.load();
  }

  /**
   * Narrow to one person, by clicking their name in a row.
   *
   * The API filters on `actorId`, a UUID. A UUID text box is useless to a human and there is no
   * endpoint that lists accounts to pick from, so the row itself is the picker: the id is already
   * on screen, attached to a name the reader recognises. Nothing new is needed on the server.
   */
  protected filterByActor(row: AuditRow): void {
    if (!row.actorId || this.actor()?.id === row.actorId) {
      return;
    }
    this.actor.set({ id: row.actorId, name: row.actorName });
    this.page.set(0);
    this.load();
  }

  protected clearActor(): void {
    if (this.actor() === null) {
      return;
    }
    this.actor.set(null);
    this.page.set(0);
    this.load();
  }

  protected clearFilters(): void {
    this.actor.set(null);
    this.page.set(0);
    // One reset, one reload: `valueChanges` would otherwise fire here and request the same page
    // twice.
    this.filters.reset({ action: '', from: '', to: '' }, { emitEvent: false });
    this.load();
  }

  protected toggleDetails(id: string): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  protected previousPage(): void {
    if (!this.hasPrevious()) {
      return;
    }
    this.page.update((current) => current - 1);
    this.load();
  }

  protected nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update((current) => current + 1);
    this.load();
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    const { action, from, to } = this.filters.getRawValue();

    if (from && to && from > to) {
      // ISO dates compare correctly as strings, which is the one thing `yyyy-MM-dd` is good for.
      this.rangeError.set('The end of the range is before its start.');
      this.loading.set(false);
      return;
    }
    this.rangeError.set(null);

    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);
    // A detail panel belongs to the row it was opened on, and that row is about to be replaced.
    this.expandedId.set(null);

    this.auditApi
      .search({
        page: this.page(),
        size: this.pageSize,
        actorId: this.actor()?.id ?? null,
        action: action || null,
        from: instantAtStartOfDay(from),
        to: instantAfter(to),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set(result.content);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set([]);
          this.totalElements.set(0);
          this.totalPages.set(0);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }
}

/** `2026-09-01` → the instant that local day began. `from` is inclusive, so this is the bound. */
function instantAtStartOfDay(day: string): string | null {
  const midnight = localMidnight(day);
  return midnight ? midnight.toISOString() : null;
}

/**
 * `2026-09-05` → the instant the **next** local day began.
 *
 * **`to` is exclusive on the API, and the picker is inclusive to the user.** Someone who asks for
 * "1 Sep to 5 Sep" means the 5th of September included — they picked it, so they expect to see it.
 * Sending the start of the 5th as an exclusive upper bound would return everything up to midnight
 * *before* that day and silently drop every row from the last day asked for. Nothing on screen
 * would say so: the page would look complete, the totals would agree with themselves, and the one
 * day someone was investigating would be missing. On an audit log that is not a rounding error, it
 * is a wrong answer to the only question being asked, so the conversion happens here, once, with
 * this comment attached to it.
 *
 * Adding a day to the date rather than to the instant is what makes 30 September, 31 December and
 * the end of a leap February work — `new Date(2026, 8, 30 + 1)` is 1 October, not the 31st of a
 * month with thirty days.
 */
function instantAfter(day: string): string | null {
  const midnight = localMidnight(day);
  if (!midnight) {
    return null;
  }
  midnight.setDate(midnight.getDate() + 1);
  return midnight.toISOString();
}

/**
 * Midnight **local**, not UTC. The user picked a day off a calendar at a school, and "6 September"
 * there starts at midnight there — parsing `2026-09-06` with `new Date(string)` would read it as
 * UTC and shift the whole range by the offset, which in India is five and a half hours of rows
 * landing on the wrong day.
 */
function localMidnight(day: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day?.trim() ?? '');
  if (!match) {
    return null;
  }
  const [, year, month, date] = match;
  const parsed = new Date(Number(year), Number(month) - 1, Number(date));
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** `STUDENT · 1a2b` → what the row acted on, or null when the action was not about a record. */
function describeRecord(event: AuditEvent): string | null {
  const type = event.entityType?.trim();
  const id = event.entityId?.trim();
  if (type && id) {
    return `${entityLabel(type)} · ${id}`;
  }
  return type ? entityLabel(type) : (id ?? null);
}

/** A timestamp the backend sent that this app cannot parse is shown as it arrived, not as junk. */
function formatTime(format: Intl.DateTimeFormat, iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : format.format(parsed);
}
