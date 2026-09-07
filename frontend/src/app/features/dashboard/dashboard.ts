import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { apiErrorCode } from '../../core/api/api-error';
import { DashboardApi } from '../../core/api/dashboard-api';
import { Dashboard as DashboardData } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { actionLabel } from '../audit/audit-actions';

/** "9 Sep 2026, 07:15" — a glance, not the full audit-log detail. */
const SHORT_TIME = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** "1 Apr 2026" — enough to place the session, never the time of day it started. */
const SHORT_DATE = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
});

/** One row of the recent-activity tile, with everything the template needs already decided. */
interface RecentActivityRow {
  readonly id: string;
  readonly when: string;
  readonly action: string;
  readonly actorName: string;
}

/**
 * The landing screen for most users (ADR-0008): what a principal opening Chalkbase in the morning
 * wants to know, cut down tile by tile to what their own permissions allow.
 *
 * **No client-side permission guard, deliberately** — the same reasoning `AuditLog` and every other
 * unguarded route in this app already carries. Which tiles arrive is a server decision
 * (`DashboardService`, one check per tile); this screen renders whatever comes back and asks no
 * question about why a tile is absent. A viewer with none of the four permissions still gets `200`
 * with every field absent, never a `403` on their own landing page.
 *
 * **A tile is either fully present or fully absent — never present with a placeholder value.**
 * `students` is missing entirely when no session is current, not shown with `enrolled: 0`; the two
 * halves of `linkageGaps` arrive independently, by which of the two permissions behind them the
 * caller holds. The template mirrors that: an `@if` per tile and per field, nothing defaulted.
 */
@Component({
  selector: 'cb-dashboard',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(DashboardApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly data = signal<DashboardData | null>(null);

  protected readonly session = computed(() => this.data()?.session ?? null);
  protected readonly students = computed(() => this.data()?.students ?? null);
  protected readonly linkageGaps = computed(() => this.data()?.linkageGaps ?? null);
  protected readonly recentAudit = computed(() => this.data()?.recentAudit ?? null);

  protected readonly sessionStartsOn = computed(() => {
    const startsOn = this.session()?.startsOn;
    return startsOn ? formatDate(startsOn) : '';
  });

  protected readonly recentActivity = computed<readonly RecentActivityRow[]>(() => {
    const events = this.data()?.recentAudit?.events ?? [];
    return events.map((event) => ({
      id: event.id,
      when: formatTime(event.occurredAt),
      action: actionLabel(event.action),
      // A failed sign-in has no actor at all — the account was never established.
      actorName: event.actorName?.trim() || 'Not signed in',
    }));
  });

  /** Whether at least one tile came back. Distinguishes "nothing for you" from "nothing loaded". */
  protected readonly hasAnyTile = computed(() => {
    const data = this.data();
    return (
      !!data && (!!data.session || !!data.students || !!data.linkageGaps || !!data.recentAudit)
    );
  });

  constructor() {
    this.load();
  }

  protected reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .get()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.data.set(null);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }
}

function formatTime(iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : SHORT_TIME.format(parsed);
}

/** `startsOn` is a plain date (`yyyy-MM-dd`), so it is parsed as local, never shifted by a zone. */
function formatDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate.trim());
  if (!match) {
    return isoDate;
  }
  const [, year, month, day] = match;
  const parsed = new Date(Number(year), Number(month) - 1, Number(day));
  return Number.isNaN(parsed.getTime()) ? isoDate : SHORT_DATE.format(parsed);
}
