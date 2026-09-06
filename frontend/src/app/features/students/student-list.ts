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
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import {
  SaveStudentRequest,
  SchoolClass,
  StudentStatus,
  StudentSummary,
} from '../../core/api/models';
import { STUDENT_PAGE_SIZE, StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { StudentForm } from './student-form';
import {
  ACCESS_DENIED,
  GENDER_LABELS,
  STATUS_LABELS,
  STATUS_OPTIONS,
  classAndSection,
  labelFor,
} from './students-shared';

/**
 * How long the search box waits before asking the server.
 *
 * Long enough that typing an eight-character name is one request rather than eight, short enough
 * that it still feels like the list is following along. Each of those requests carries the text to
 * a server access log, so fewer of them is a privacy improvement as well as a bandwidth one.
 */
const SEARCH_DEBOUNCE_MS = 300;

/** "Any status" is a real option rather than the select's placeholder: a filter must be clearable. */
const STATUS_FILTER_OPTIONS: readonly SelectOption[] = [
  { value: '', label: 'Any status' },
  ...STATUS_OPTIONS,
];

/** One row, with everything the template needs already decided. */
interface StudentRow {
  readonly id: string;
  readonly link: readonly (string | number)[];
  readonly admissionNumber: string;
  readonly fullName: string;
  readonly gender: string;
  readonly status: StudentStatus;
  readonly statusLabel: string;
  readonly statusClass: string;
  /** `Class 5 · A`, or null for a student who has been admitted but not yet enrolled. */
  readonly placement: string | null;
  readonly session: string | null;
  readonly rollNumber: string | null;
}

/**
 * The school's students (FR, ADR-0020).
 *
 * ## The question this screen answers
 *
 * "Which section is this child in." That is what somebody at the office counter is asking when
 * they type a name into the box, so the class, the section and the roll number are on the row
 * rather than one tap away on the record. A list that made you open each result to find out would
 * be a list that answered a different question.
 *
 * ## Nothing here is ever deleted
 *
 * ADR-0020 §6: a student is `WITHDRAWN` or `TRANSFERRED`, never removed, because fees, attendance
 * and marks all point at them. There is deliberately no delete affordance on this screen or the
 * record, and the status field on the form is where a school records that somebody left.
 *
 * ## The filters are not in the URL, and that is deliberate
 *
 * A student's name is Confidential (ADR-0014). Putting `q` into the router's query parameters would
 * mint a URL with a child's name in it — one that lands in browser history, in a bookmark, in a
 * screenshot, and in whatever a support ticket pastes. The search text does travel to the server as
 * `?q=`, because that is a box the user chose to type into; the difference is that this app is not
 * the one building a *link* out of it. The cost is that the back button does not restore a search,
 * which is a smaller thing than the leak.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008 warns against re-deriving authorization in the client, so there is no `canActivate`
 * checking `student:student:read`. The server leaves the menu item out for anyone without it and
 * the endpoint enforces it independently; typing the URL lands here and gets a 403 this screen
 * explains calmly, exactly as the audit log does.
 */
@Component({
  selector: 'cb-student-list',
  imports: [ReactiveFormsModule, RouterLink, Button, Select, TextInput, StudentForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-list.html',
  styleUrl: './student-list.scss',
})
export class StudentList {
  private readonly students = inject(StudentsApi);
  private readonly academics = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly pageSize = STUDENT_PAGE_SIZE;
  protected readonly statusFilterOptions = STATUS_FILTER_OPTIONS;

  /**
   * Whether to offer the two ways of adding a student at all.
   *
   * Both `POST /api/students` and both import endpoints are gated on this one permission, so both
   * affordances stand or fall together. Hidden rather than disabled: a classteacher will never be
   * able to enable it, and a greyed-out button is a question the screen cannot answer.
   */
  protected readonly canManageStudents = permitted(Permissions.STUDENT_MANAGE);

  protected readonly filters = this.formBuilder.group({
    q: '',
    status: '',
    sectionId: '',
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly StudentSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  /** The class ladder, which is the only way a section id can be offered as a thing to pick. */
  protected readonly ladder = signal<readonly SchoolClass[]>([]);
  protected readonly ladderFailed = signal(false);

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  protected readonly saveFieldErrors = signal<Readonly<Record<string, string>>>({});

  /**
   * Only the request that answers last may paint. Two keystrokes land two searches, and the
   * earlier one arriving second would leave a page of rows that does not match the box above it.
   */
  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  /**
   * Whether anything is narrowing the list — which decides whether "Clear filters" is offered and
   * whether an empty result reads as "nothing matches" or as "no students yet".
   *
   * `revision()` is read first and it is load-bearing: a reactive form is not a signal, so without
   * something to depend on this would be computed once and never again, and the screen would tell
   * a user who had just filtered to a name that the school has no students.
   */
  protected readonly filtered = computed(() => {
    this.revision();
    const { q, status, sectionId } = this.filters.getRawValue();
    return q.trim() !== '' || status !== '' || sectionId !== '';
  });

  /** Bumped on every filter change so `filtered` recomputes; the values come off the controls. */
  private readonly revision = signal(0);

  /**
   * The line under the title — how many students the school has.
   *
   * This replaced a two-line lede explaining that you can search by name or admission number.
   * That sentence was read once and then re-read past on every visit, and the thing it explained
   * now sits inside the box it was explaining: the search field's own placeholder says it. The
   * designs put a fact there instead, which is what a subtitle on a list screen is worth.
   *
   * Only the school's own total, and only when nothing is narrowing the list. Under a filter the
   * number would be a count of matches, which the paging line already prints as "Showing 1–25 of
   * 12" — and a heading that changed to "12 students" as somebody typed would read as a school
   * shrinking. Nothing while loading or after a failure, because there is no honest number then.
   *
   * The session name the mockup pairs with this is deliberately absent: no request on this screen
   * returns it, and a hard-coded "Session 2026–27" would be a fact this app invented.
   */
  protected readonly subtitle = computed(() => {
    if (this.loading() || this.failureCode() !== null || this.filtered()) {
      return null;
    }
    const total = this.totalElements();
    if (total === 0) {
      return null;
    }
    // en-IN, because 1,428 is 1,428 here but 12,45,678 is not 1,245,678.
    return `${total.toLocaleString('en-IN')} ${total === 1 ? 'student' : 'students'}`;
  });

  /**
   * Every section in the school, as `Class 5 · A`, flattened from the ladder.
   *
   * One control rather than a class picker feeding a section picker, because the API filters on a
   * section id and nothing else — a "Class" select on its own would look like a filter and do
   * nothing. Inactive rungs are listed and marked rather than hidden: a student can still be
   * enrolled in a section the school has stopped running, and leaving it out would make that
   * student unfindable by the one filter that would have found them.
   */
  protected readonly sectionOptions = computed<readonly SelectOption[]>(() => {
    const options: SelectOption[] = [{ value: '', label: 'Any class or section' }];
    for (const schoolClass of this.ladder()) {
      for (const section of schoolClass.sections) {
        const running = schoolClass.active && section.active;
        options.push({
          value: section.id,
          label:
            classAndSection(schoolClass.name, section.name) + (running ? '' : ' · not running'),
        });
      }
    }
    return options;
  });

  protected readonly view = computed<readonly StudentRow[]>(() =>
    this.rows().map((student) => ({
      id: student.id,
      // A route array, not a string: the id is a UUID and carries nothing about the child. Nothing
      // on this screen may build an href out of a name, a date of birth or an admission number.
      link: ['/students', student.id],
      admissionNumber: student.admissionNumber,
      fullName: student.fullName,
      gender: labelFor(GENDER_LABELS, student.gender),
      status: student.status,
      statusLabel: labelFor(STATUS_LABELS, student.status),
      statusClass: `status status--${student.status.toLowerCase()}`,
      placement: student.currentEnrolment
        ? classAndSection(student.currentEnrolment.className, student.currentEnrolment.sectionName)
        : null,
      session: student.currentEnrolment?.sessionName ?? null,
      rollNumber: student.currentEnrolment?.rollNumber?.trim() || null,
    })),
  );

  /** "1–25 of 613". Counted off the rows actually received, so the last page reads correctly. */
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
    this.loadLadder();

    this.filters.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));

    // The search box waits; the two selects do not. Debouncing a dropdown adds a pause to a
    // deliberate, single action, and a filter that lags behind the control reads as broken.
    this.filters.controls.q.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.refilter());

    merge(this.filters.controls.status.valueChanges, this.filters.controls.sectionId.valueChanges)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refilter());
  }

  protected reload(): void {
    this.load();
  }

  protected clearFilters(): void {
    // One reset and one reload: `valueChanges` would otherwise fire twice and request the same
    // page three times over.
    this.filters.reset({ q: '', status: '', sectionId: '' }, { emitEvent: false });
    this.revision.update((count) => count + 1);
    this.page.set(0);
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

  // ── Adding a student ─────────────────────────────────────────────────────────────────────

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
    // Back to the button that opened the form, so a keyboard user is not left at the top of the
    // document wondering where the form went.
    this.focusAfterRender('#student-add');
  }

  /**
   * Creates the student, then opens their record.
   *
   * Going straight to the record rather than back to the list is the flow the office actually
   * needs: a student who has just been admitted has no guardian and no enrolment, and both of those
   * live on the record. Dropping them back on a list of six hundred names would make the next two
   * steps something they have to go and find.
   */
  protected save(request: SaveStudentRequest): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});

    this.students
      .create(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (student) => {
          this.saving.set(false);
          this.adding.set(false);
          void this.router.navigate(['/students', student.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
          this.saveFieldErrors.set(apiErrorDetails(error));
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  /** Every filter change starts again from the first page — page four of the old filter is either
   * the wrong rows or an empty page, and both look like a broken screen. */
  private refilter(): void {
    this.page.set(0);
    this.load();
  }

  private load(): void {
    const { q, status, sectionId } = this.filters.getRawValue();
    const request = ++this.latestRequest;

    this.loading.set(true);
    this.failureCode.set(null);

    this.students
      .search({
        page: this.page(),
        size: this.pageSize,
        q: q.trim() || null,
        status: (status as StudentStatus) || null,
        sectionId: sectionId || null,
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

  /**
   * The class ladder, for the section filter only.
   *
   * A failure here is not a failure of this screen: the student list still works, so it says the
   * one filter is unavailable rather than replacing six hundred students with an error. This is
   * also the ordinary answer for somebody who may read students but not the academic structure —
   * the two permissions are separate and a role really can hold one without the other.
   */
  private loadLadder(): void {
    this.academics
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (classes) => {
          this.ladder.set(classes);
          this.ladderFailed.set(false);
        },
        error: () => {
          this.ladder.set([]);
          this.ladderFailed.set(true);
        },
      });
  }

  /**
   * Move focus once the DOM has caught up with the signal that changed. The app is zoneless, so
   * querying for the element on the next line would find the one about to be replaced.
   */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
