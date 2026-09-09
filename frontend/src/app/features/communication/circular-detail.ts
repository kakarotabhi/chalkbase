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
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { apiErrorCode } from '../../core/api/api-error';
import { CommunicationApi } from '../../core/api/communication-api';
import {
  CircularDetail as CircularDetailModel,
  CircularRecipientResponse,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { SessionStore, permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { BottomSheet } from '../../shared/components/bottom-sheet/bottom-sheet';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  NOT_FOUND,
  circularStatusLabel,
  circularStatusTone,
  circularTimeFormat,
  classAndSection,
  formatCircularTime,
} from './communication-shared';

/**
 * One circular in full: its content, its targets, and its recipient list (Phase 2, FR-101/FR-104).
 *
 * No client-side permission gate (ADR-0008): both the circular and its recipient list enforce
 * `communication:circular:read` on the server, and `forbidden()` renders the same way every other
 * screen's does if the call 403s. Recording an acknowledgement is gated separately —
 * {@link CircularDetail#canAcknowledge} hides that action for a caller who can read but not act on
 * one.
 *
 * **Acknowledging here is recorded on a family's behalf**, not by the family themselves — there is
 * no parent login yet (see the backend module's own package doc). A staff member records that a
 * family acknowledged the circular by phone, in writing, or in person.
 */
@Component({
  selector: 'cb-circular-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Badge,
    BottomSheet,
    Button,
    Card,
    FormField,
    TextInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './circular-detail.html',
  styleUrl: './circular-detail.scss',
})
export class CircularDetail {
  /** Bound from the route by `withComponentInputBinding`. */
  readonly id = input.required<string>();

  private readonly api = inject(CommunicationApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessionStore = inject(SessionStore);

  protected readonly canAcknowledge = permitted(Permissions.COMMUNICATION_ACKNOWLEDGE);

  protected readonly circularStatusLabel = circularStatusLabel;
  protected readonly circularStatusTone = circularStatusTone;
  protected readonly classAndSection = classAndSection;

  /** The school's own zone, not the reader's (ADR-0032) — see `communication-shared.ts`. */
  private readonly timeZone = computed(() => this.sessionStore.schoolTimezone());
  private readonly timeFormat = computed(() => circularTimeFormat(this.timeZone()));

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly circular = signal<CircularDetailModel | null>(null);

  protected readonly recipients = signal<readonly CircularRecipientResponse[]>([]);
  /** {@link recipients}, with delivered/acknowledged already rendered in the school's own zone. */
  protected readonly recipientRows = computed(() => {
    const format = this.timeFormat();
    return this.recipients().map((recipient) => ({
      recipient,
      deliveredAt: formatCircularTime(format, recipient.deliveredAt),
      acknowledgedAt: recipient.acknowledgedAt
        ? formatCircularTime(format, recipient.acknowledgedAt)
        : null,
    }));
  });
  protected readonly recipientsLoading = signal(false);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly missing = computed(() => this.failureCode() === NOT_FOUND);
  protected readonly failed = computed(() => {
    const code = this.failureCode();
    return code !== null && code !== ACCESS_DENIED && code !== NOT_FOUND;
  });
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  protected readonly acknowledgeTarget = signal<CircularRecipientResponse | null>(null);
  protected readonly acknowledgeForm = this.formBuilder.group({
    note: ['', Validators.maxLength(500)],
  });
  protected readonly acknowledgeSaving = signal(false);
  protected readonly acknowledgeFailureCode = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.id();
      this.page.set(0);
      this.load(id);
    });
  }

  protected reload(): void {
    this.load(this.id());
  }

  protected previousPage(): void {
    if (!this.hasPrevious()) {
      return;
    }
    this.page.update((current) => current - 1);
    this.loadRecipients();
  }

  protected nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update((current) => current + 1);
    this.loadRecipients();
  }

  protected openAcknowledge(recipient: CircularRecipientResponse): void {
    this.acknowledgeFailureCode.set(null);
    this.acknowledgeForm.reset({ note: '' });
    this.acknowledgeTarget.set(recipient);
  }

  protected closeAcknowledge(): void {
    if (this.acknowledgeSaving()) {
      return;
    }
    this.acknowledgeTarget.set(null);
  }

  protected submitAcknowledge(): void {
    const target = this.acknowledgeTarget();
    if (!target || this.acknowledgeSaving()) {
      return;
    }
    const note = this.acknowledgeForm.getRawValue().note.trim();

    this.acknowledgeSaving.set(true);
    this.acknowledgeFailureCode.set(null);
    this.api
      .acknowledge(this.id(), target.id, { note: note || undefined })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.acknowledgeSaving.set(false);
          this.acknowledgeTarget.set(null);
          this.recipients.update((rows) =>
            rows.map((row) => (row.id === updated.id ? updated : row)),
          );
          this.circular.update((current) =>
            current ? { ...current, acknowledgedCount: current.acknowledgedCount + 1 } : current,
          );
          this.announcement.set(`Recorded ${target.studentFullName}'s acknowledgement.`);
        },
        error: (error: unknown) => {
          this.acknowledgeSaving.set(false);
          this.acknowledgeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.failureCode.set(null);
    this.recipients.set([]);

    this.api
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.circular.set(detail);
          this.loading.set(false);
          if (detail.status === 'PUBLISHED') {
            this.loadRecipients();
          }
        },
        error: (error: unknown) => {
          this.circular.set(null);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private loadRecipients(): void {
    this.recipientsLoading.set(true);
    this.api
      .recipients(this.id(), this.page())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.recipients.set(result.content);
          this.totalPages.set(result.totalPages);
          this.recipientsLoading.set(false);
        },
        error: () => {
          this.recipients.set([]);
          this.totalPages.set(0);
          this.recipientsLoading.set(false);
        },
      });
  }
}
