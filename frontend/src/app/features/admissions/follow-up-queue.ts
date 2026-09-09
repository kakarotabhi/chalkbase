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
import { AdmissionApi, ENQUIRY_PAGE_SIZE } from '../../core/api/admission-api';
import { apiErrorCode } from '../../core/api/api-error';
import { EnquiryFollowUpQueueItem } from '../../core/api/models';
import { Badge, BadgeTone } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Select, SelectOption } from '../../shared/components/select/select';
import { formatDay } from '../../shared/formatting/day';
import { ACCESS_DENIED, STATUS_LABELS, labelFor } from './admissions-shared';

const SCOPE_OPTIONS: readonly SelectOption[] = [
  { value: 'mine', label: 'My enquiries' },
  { value: 'all', label: "Every counsellor's" },
];

/** One row of the queue, with everything the template needs already decided. */
interface QueueRow {
  readonly enquiryId: string;
  readonly link: readonly (string | number)[];
  readonly childFullName: string;
  readonly parentName: string;
  readonly parentPhone: string;
  readonly interestedClassName: string | null;
  readonly statusLabel: string;
  readonly assignedCounsellorName: string;
  readonly nextFollowUpDate: string;
  readonly dueLabel: string;
  readonly dueTone: BadgeTone;
}

/**
 * The due-date follow-up queue (FR-017) — the thing that makes enquiry management more than a
 * mailbox. What a counsellor opens on Monday morning: their own overdue and due-today follow-ups,
 * not everyone's, which is why `mine` is the default rather than a filter someone has to remember
 * to set. See `EnquiryRepository.findDueFollowUps` for exactly which enquiries qualify — a row here
 * always has a `nextFollowUpDate` and it is always today or earlier.
 *
 * There is no client-side guard on this route, the same reasoning `enquiry-list` gives: the
 * endpoint enforces `admission:enquiry:read` independently.
 */
@Component({
  selector: 'cb-follow-up-queue',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './follow-up-queue.html',
  styleUrl: './follow-up-queue.scss',
})
export class FollowUpQueue {
  private readonly admissions = inject(AdmissionApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pageSize = ENQUIRY_PAGE_SIZE;
  protected readonly scopeOptions = SCOPE_OPTIONS;

  protected readonly scope = this.formBuilder.control('mine');

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly EnquiryFollowUpQueueItem[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly view = computed<readonly QueueRow[]>(() =>
    this.rows().map((item) => ({
      enquiryId: item.enquiryId,
      link: ['/admissions/enquiries', item.enquiryId],
      childFullName: item.childFullName,
      parentName: item.parentName,
      parentPhone: item.parentPhone,
      interestedClassName: item.interestedClassName ?? null,
      statusLabel: labelFor(STATUS_LABELS, item.status),
      assignedCounsellorName: item.assignedCounsellorName,
      nextFollowUpDate: formatDay(item.nextFollowUpDate),
      dueLabel: item.overdue ? 'Overdue' : 'Due today',
      dueTone: item.overdue ? 'danger' : 'warning',
    })),
  );

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

    this.scope.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
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

    this.admissions
      .dueFollowUps(this.scope.value === 'mine', this.page(), this.pageSize)
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
