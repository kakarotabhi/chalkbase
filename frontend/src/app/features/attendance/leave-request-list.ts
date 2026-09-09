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
import { RouterLink } from '@angular/router';
import { apiErrorCode } from '../../core/api/api-error';
import { AttendanceApi, LEAVE_REQUEST_PAGE_SIZE } from '../../core/api/attendance-api';
import { LeaveDecision, LeaveRequestResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge, BadgeTone } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Select, SelectOption } from '../../shared/components/select/select';
import {
  ACCESS_DENIED,
  classAndSection,
  leaveDecisionLabel,
  leaveDecisionTone,
} from './attendance-shared';

/** "Any" is a real option rather than the select's placeholder, the same as every filter in this app. */
const DECISION_FILTER_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Any status' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

/** One row, with the badge tone already resolved so the template stays declarative. */
interface LeaveRequestRow {
  readonly id: string;
  readonly studentName: string;
  readonly placement: string;
  readonly dateRange: string;
  readonly reason: string;
  readonly decisionLabel: string;
  readonly decisionTone: BadgeTone;
}

/**
 * The leave request list (FR-047): every request, with a filter for pending, approved and
 * rejected, and the way in to filing a new one.
 *
 * ## Table above, cards below (ADR-0010)
 *
 * Unlike the marking screen, a row here has no interactive control of its own — it is a summary a
 * viewer reads and then opens for the decision, the same shape `student-list` already uses. So this
 * follows ADR-0010's ordinary pattern rather than departing from it the way the marking screen does.
 *
 * ## No client-side permission gate (ADR-0008)
 *
 * The list itself enforces `attendance:leave:read` on the server; `forbidden()` renders the same
 * banner every other screen's does on a 403. "New leave request" is hidden rather than disabled for
 * a caller without `attendance:leave:request` — the same reasoning `student-list` gives for hiding
 * its own write actions.
 */
@Component({
  selector: 'cb-leave-request-list',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './leave-request-list.html',
  styleUrl: './leave-request-list.scss',
})
export class LeaveRequestList {
  private readonly api = inject(AttendanceApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pageSize = LEAVE_REQUEST_PAGE_SIZE;
  protected readonly decisionFilterOptions = DECISION_FILTER_OPTIONS;

  protected readonly canRequest = permitted(Permissions.ATTENDANCE_LEAVE_REQUEST);

  protected readonly decisionFilter = this.formBuilder.control('');

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly LeaveRequestRow[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  private latestRequest = 0;

  constructor() {
    this.load();
    this.decisionFilter.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
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

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    const decision = (this.decisionFilter.value || undefined) as LeaveDecision | undefined;
    this.api
      .leaveRequests(this.page(), this.pageSize, decision)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set(result.content.map(toRow));
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set([]);
          this.totalPages.set(0);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }
}

function toRow(response: LeaveRequestResponse): LeaveRequestRow {
  return {
    id: response.id,
    studentName: response.studentName,
    placement: classAndSection(response.className, response.sectionName),
    dateRange:
      response.startDate === response.endDate
        ? response.startDate
        : `${response.startDate} – ${response.endDate}`,
    reason: response.reason,
    decisionLabel: leaveDecisionLabel(response.decision),
    decisionTone: leaveDecisionTone(response.decision),
  };
}
