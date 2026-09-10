import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { apiErrorCode } from '../../core/api/api-error';
import { AttendanceApi } from '../../core/api/attendance-api';
import { LeaveRequestResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { SessionStore, permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { formatDay, formatInstant, instantFormat } from '../../shared/formatting/day';
import {
  ACCESS_DENIED,
  NOT_FOUND,
  classAndSection,
  leaveDecisionLabel,
  leaveDecisionTone,
} from './attendance-shared';

/**
 * One leave request: the student, the dates, the reason, and — while it is still pending — the
 * decision.
 *
 * No client-side gate (ADR-0008): the load enforces `attendance:leave:read`, and the decision
 * enforces `attendance:leave:approve` independently, so a caller who can see this page but not
 * decide it simply never sees the approve/reject actions render.
 */
@Component({
  selector: 'cb-leave-request-detail',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Card],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './leave-request-detail.html',
  styleUrl: './leave-request-detail.scss',
})
export class LeaveRequestDetail {
  /** Bound from the route by `withComponentInputBinding`. */
  readonly id = input.required<string>();

  private readonly api = inject(AttendanceApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessionStore = inject(SessionStore);

  protected readonly canApprove = permitted(Permissions.ATTENDANCE_LEAVE_APPROVE);

  /** The zone the decision timestamp below is rendered in — the school's, not the reader's device (ADR-0032). */
  private readonly decidedAtFormat = computed(() =>
    instantFormat(this.sessionStore.schoolTimezone()),
  );

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly request = signal<LeaveRequestResponse | null>(null);

  protected readonly noteControl = this.formBuilder.control('');
  protected readonly deciding = signal<'APPROVED' | 'REJECTED' | null>(null);
  protected readonly decisionFailureCode = signal<string | null>(null);
  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly missing = computed(() => this.failureCode() === NOT_FOUND);
  protected readonly failed = computed(() => {
    const code = this.failureCode();
    return code !== null && code !== ACCESS_DENIED && code !== NOT_FOUND;
  });

  /** The header and dates, with everything the template needs already decided. */
  protected readonly view = computed(() => {
    const request = this.request();
    if (!request) {
      return null;
    }
    return {
      studentName: request.studentName,
      placement: classAndSection(request.className, request.sectionName),
      dateRange:
        request.startDate === request.endDate
          ? formatDay(request.startDate)
          : `${formatDay(request.startDate)} – ${formatDay(request.endDate)}`,
      reason: request.reason,
      requestedAt: request.requestedAt,
      decisionLabel: leaveDecisionLabel(request.decision),
      decisionTone: leaveDecisionTone(request.decision),
      pending: request.decision === 'PENDING',
      decidedAt: request.decidedAt
        ? formatInstant(this.decidedAtFormat(), request.decidedAt)
        : null,
      decisionNote: request.decisionNote ?? null,
    };
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.announcement.set('');
      this.load(id);
    });
  }

  protected reload(): void {
    this.load(this.id());
  }

  protected decide(decision: 'APPROVED' | 'REJECTED'): void {
    if (this.deciding()) {
      return;
    }
    this.deciding.set(decision);
    this.decisionFailureCode.set(null);
    this.api
      .decideLeaveRequest(this.id(), { decision, note: this.noteControl.value.trim() || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.deciding.set(null);
          this.request.set(updated);
          this.announcement.set(
            decision === 'APPROVED' ? 'Leave request approved.' : 'Leave request rejected.',
          );
        },
        error: (error: unknown) => {
          this.deciding.set(null);
          this.decisionFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.failureCode.set(null);
    this.api
      .leaveRequest(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          this.request.set(result);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.request.set(null);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }
}
