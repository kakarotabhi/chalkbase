import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AttendanceApi, CORRECTION_QUEUE_PAGE_SIZE } from '../../core/api/attendance-api';
import { apiErrorCode } from '../../core/api/api-error';
import { CorrectionRequestResponse } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { ACCESS_DENIED, statusLabel } from './attendance-shared';

/** One row, with the note the admin is typing for it kept alongside it rather than in a form array. */
interface QueueRow {
  readonly request: CorrectionRequestResponse;
  readonly note: string;
}

/**
 * An administrator's queue of attendance correction requests, oldest first.
 *
 * No client-side permission gate (ADR-0008): the queue and the decision both enforce
 * `attendance:correction:approve` on the server, and `forbidden()` renders the same way every
 * other screen's does if the call 403s. There is nothing narrower to gate within the screen —
 * reading the queue and deciding a request share the one permission, unlike the marking screen's
 * separate read and manage — so a row that loaded at all may always be decided.
 */
@Component({
  selector: 'cb-attendance-corrections',
  imports: [Button, Card],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './attendance-corrections.html',
  styleUrl: './attendance-corrections.scss',
})
export class AttendanceCorrections {
  private readonly api = inject(AttendanceApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly statusLabel = statusLabel;

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly QueueRow[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly decidingId = signal<string | null>(null);
  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  private latestRequest = 0;

  constructor() {
    this.load();
  }

  protected reload(): void {
    this.load();
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

  protected setNote(requestId: string, note: string): void {
    this.rows.update((rows) =>
      rows.map((row) => (row.request.id === requestId ? { ...row, note } : row)),
    );
  }

  protected decide(requestId: string, decision: 'APPROVED' | 'REJECTED'): void {
    if (this.decidingId()) {
      return;
    }
    const row = this.rows().find((candidate) => candidate.request.id === requestId);
    if (!row) {
      return;
    }
    this.decidingId.set(requestId);
    this.api
      .decideCorrection(requestId, { decision, note: row.note.trim() || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.decidingId.set(null);
          this.announcement.set(
            decision === 'APPROVED'
              ? `${row.request.studentName}'s attendance was corrected.`
              : `${row.request.studentName}'s correction request was rejected.`,
          );
          this.load();
        },
        error: (error: unknown) => {
          this.decidingId.set(null);
          this.announcement.set('');
          this.failureCode.set(null);
          // A failed decision leaves the queue as it was; the row's own state says so implicitly by
          // not disappearing. The error code still matters for a screen reader, so it is announced.
          this.announcement.set(
            `Could not decide this request (${apiErrorCode(error)}). Try again.`,
          );
        },
      });
  }

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .pendingCorrections(this.page(), CORRECTION_QUEUE_PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set(result.content.map((item) => ({ request: item, note: '' })));
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
