import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';
import { AcademicsApi } from '../../core/api/academics-api';
import { AdmissionApi, ENQUIRY_PAGE_SIZE } from '../../core/api/admission-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import {
  CreateEnquiryRequest,
  EnquirySource,
  EnquiryStatus,
  EnquirySummary,
  SchoolClass,
  UserSummary,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge, BadgeTone } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { formatDay } from '../../shared/formatting/day';
import { pageFromQueryParams, syncListQueryParams } from '../../shared/routing/list-query-params';
import {
  ACCESS_DENIED,
  SOURCE_LABELS,
  SOURCE_OPTIONS,
  STATUS_LABELS,
  STATUS_OPTIONS,
  STATUS_TONES,
  labelFor,
} from './admissions-shared';
import { EnquiryForm } from './enquiry-form';

/** "Any status"/"Any source" are real options, the same convention `student-list` uses for its own pills. */
const STATUS_FILTER_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Any status' },
  ...STATUS_OPTIONS,
];
const SOURCE_FILTER_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Any source' },
  ...SOURCE_OPTIONS,
];

/** One row, with everything the template needs already decided. */
interface EnquiryRow {
  readonly id: string;
  readonly link: readonly (string | number)[];
  readonly childFullName: string;
  readonly parentName: string;
  readonly parentPhone: string;
  readonly interestedClassName: string | null;
  readonly source: string;
  readonly status: EnquiryStatus;
  readonly statusLabel: string;
  readonly statusTone: BadgeTone;
  readonly assignedCounsellorName: string;
  readonly nextFollowUpDate: string | null;
}

/**
 * The school's admission enquiries (FR-016, FR-017).
 *
 * ## The question this screen answers
 *
 * "Who has enquired, who owns following them up, and where do they stand." A front office typing a
 * parent's name or phone number into the box is answering "have we already spoken to this family",
 * so the counsellor and the status sit on the row rather than one tap away.
 *
 * ## The URL carries what is safe to carry, and no more
 *
 * `status`, `source` and `assignedCounsellorId` are mirrored into the URL (replacing the current
 * history entry, never pushing one) so a filtered view can be linked, bookmarked and survives a
 * trip to one enquiry's own record. `q` never is: a child's name and a parent's phone number are
 * Confidential (ADR-0014) — the same reasoning `student-list` gives for keeping its own search box
 * out of the router's query parameters applies here without change.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008 warns against re-deriving authorization in the client. The endpoint enforces
 * `admission:enquiry:read` independently; typing the URL with no permission lands here and gets a
 * 403 this screen explains calmly.
 */
@Component({
  selector: 'cb-enquiry-list',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Card, Select, TextInput, EnquiryForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './enquiry-list.html',
  styleUrl: './enquiry-list.scss',
})
export class EnquiryList {
  private readonly admissions = inject(AdmissionApi);
  private readonly academics = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly pageSize = ENQUIRY_PAGE_SIZE;
  protected readonly statusFilterOptions = STATUS_FILTER_OPTIONS;
  protected readonly sourceFilterOptions = SOURCE_FILTER_OPTIONS;
  protected readonly statusLabel = (status: EnquiryStatus) => labelFor(STATUS_LABELS, status);

  /** Both capturing an enquiry and reassigning its counsellor are gated on this one permission. */
  protected readonly canManage = permitted(Permissions.ADMISSION_ENQUIRY_MANAGE);

  /** `status`, `source` and `assignedCounsellorId` seed from the URL; `q` never does — see the class Javadoc. */
  protected readonly filters = this.formBuilder.group({
    q: '',
    status: this.route.snapshot.queryParamMap.get('status') ?? '',
    source: this.route.snapshot.queryParamMap.get('source') ?? '',
    assignedCounsellorId: this.route.snapshot.queryParamMap.get('assignedCounsellorId') ?? '',
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly EnquirySummary[]>([]);
  protected readonly page = signal(pageFromQueryParams(this.route));
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly classes = signal<readonly SchoolClass[]>([]);
  protected readonly counsellors = signal<readonly UserSummary[]>([]);
  protected readonly referenceDataFailed = signal(false);

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  protected readonly saveFieldErrors = signal<Readonly<Record<string, string>>>({});

  /** Only the request that answers last may paint — the same guard `student-list` keeps. */
  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly counsellorFilterOptions = computed<readonly SelectOption[]>(() => {
    const options: SelectOption[] = [{ value: '', label: 'Any counsellor' }];
    for (const counsellor of this.counsellors()) {
      options.push({ value: counsellor.id, label: counsellor.displayName });
    }
    return options;
  });

  private readonly revision = signal(0);

  protected readonly filtered = computed(() => {
    this.revision();
    const { q, status, source, assignedCounsellorId } = this.filters.getRawValue();
    return q.trim() !== '' || status !== '' || source !== '' || assignedCounsellorId !== '';
  });

  protected readonly subtitle = computed(() => {
    if (this.loading() || this.failureCode() !== null || this.filtered()) {
      return null;
    }
    const total = this.totalElements();
    if (total === 0) {
      return null;
    }
    return `${total.toLocaleString('en-IN')} ${total === 1 ? 'enquiry' : 'enquiries'}`;
  });

  protected readonly view = computed<readonly EnquiryRow[]>(() =>
    this.rows().map((enquiry) => ({
      id: enquiry.id,
      link: ['/admissions/enquiries', enquiry.id],
      childFullName: enquiry.childFullName,
      parentName: enquiry.parentName,
      parentPhone: enquiry.parentPhone,
      interestedClassName: enquiry.interestedClassName ?? null,
      source: labelFor(SOURCE_LABELS, enquiry.source),
      status: enquiry.status,
      statusLabel: labelFor(STATUS_LABELS, enquiry.status),
      statusTone: STATUS_TONES[enquiry.status],
      assignedCounsellorName: enquiry.assignedCounsellorName,
      nextFollowUpDate: enquiry.nextFollowUpDate ? formatDay(enquiry.nextFollowUpDate) : null,
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
    this.loadReferenceData();

    this.filters.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));

    this.filters.controls.q.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refilter());

    merge(
      this.filters.controls.status.valueChanges,
      this.filters.controls.source.valueChanges,
      this.filters.controls.assignedCounsellorId.valueChanges,
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refilter());
  }

  protected reload(): void {
    this.load();
  }

  protected clearFilters(): void {
    this.filters.reset(
      { q: '', status: '', source: '', assignedCounsellorId: '' },
      { emitEvent: false },
    );
    this.revision.update((count) => count + 1);
    this.page.set(0);
    this.syncUrl();
    this.load();
  }

  protected previousPage(): void {
    if (!this.hasPrevious()) {
      return;
    }
    this.page.update((current) => current - 1);
    this.syncUrl();
    this.load();
  }

  protected nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update((current) => current + 1);
    this.syncUrl();
    this.load();
  }

  // ── Capturing an enquiry ─────────────────────────────────────────────────────────────────

  protected startAdd(): void {
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});
    this.adding.set(true);
  }

  protected cancelAdd(): void {
    if (this.saving()) {
      return;
    }
    this.adding.set(false);
    this.focusAfterRender('#enquiry-add');
  }

  /** Creates the enquiry, then opens its record — the same flow `student-list` uses for the same reason. */
  protected save(request: CreateEnquiryRequest): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});

    this.admissions
      .create(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enquiry) => {
          this.saving.set(false);
          this.adding.set(false);
          void this.router.navigate(['/admissions/enquiries', enquiry.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
          this.saveFieldErrors.set(apiErrorDetails(error));
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private refilter(): void {
    this.page.set(0);
    this.syncUrl();
    this.load();
  }

  /**
   * Mirrors `status`, `source`, `assignedCounsellorId` and `page` into the URL — never `q`, see the
   * class Javadoc.
   */
  private syncUrl(): void {
    const { status, source, assignedCounsellorId } = this.filters.getRawValue();
    syncListQueryParams(this.router, this.route, {
      status: status || undefined,
      source: source || undefined,
      assignedCounsellorId: assignedCounsellorId || undefined,
      page: this.page() || undefined,
    });
  }

  private load(): void {
    const { q, status, source, assignedCounsellorId } = this.filters.getRawValue();
    const request = ++this.latestRequest;

    this.loading.set(true);
    this.failureCode.set(null);

    this.admissions
      .enquiries(
        {
          q: q.trim() || null,
          status: (status as EnquiryStatus) || null,
          source: (source as EnquirySource) || null,
          assignedCounsellorId: assignedCounsellorId || null,
        },
        this.page(),
        this.pageSize,
      )
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

  /**
   * The class ladder and the counsellor list, for the filters and the add form. A failure here is
   * not a failure of this screen: the enquiry list still works — see `student-list#loadLadder` for
   * the same reasoning.
   */
  private loadReferenceData(): void {
    this.academics
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (classes) => this.classes.set(classes),
        error: () => this.referenceDataFailed.set(true),
      });

    this.admissions
      .counsellors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (counsellors) => this.counsellors.set(counsellors),
        error: () => this.referenceDataFailed.set(true),
      });
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
