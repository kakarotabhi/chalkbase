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
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AcademicsApi, SUBJECT_PAGE_SIZE } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { Subject } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED } from './academics-shared';

/** Same pause as the guardian and student search: eight characters is one request, not eight. */
const SEARCH_DEBOUNCE_MS = 300;

/** `subject.name` is `varchar(80)`. */
const NAME_MAX_LENGTH = 80;

/** `subject.code` is `varchar(20)`. */
const CODE_MAX_LENGTH = 20;

/**
 * A name or a code another row already has.
 *
 * Neither uniqueness constraint accounts for `active`, so a subject keeps its name and its code
 * after it stops being taught — the same trap `school_class` has (see `school-classes.ts`). "Hindi
 * already exists" is baffling until someone notices a retired "Hindi" row still holding it.
 */
const DUPLICATE_NAME = 'ACAD_008';
const DUPLICATE_CODE = 'ACAD_009';

/** null when closed, 'new' when adding, or the id of the subject being edited. */
type Editor = 'new' | string | null;

/** One row, with everything the template needs already decided. */
interface SubjectRow {
  readonly id: string;
  readonly name: string;
  readonly code: string;
  readonly active: boolean;
  readonly editButtonId: string;
  readonly activeButtonId: string;
}

/**
 * The school's subject catalogue — the last piece of Phase 1 master data (docs/status.md).
 *
 * ## A flat list, unlike the class ladder
 *
 * `school-classes.ts` reorders a ladder in one transaction because "which comes first" always has
 * an answer for a class. It does not for a subject — English does not come before Mathematics —
 * so there is nothing to reorder here, no move-up and move-down buttons, and the list is read
 * alphabetically instead. That is also why this screen is paged and the ladder is not: a class list
 * tops out at a few dozen rungs a school reads as a whole, and a subject catalogue for a school
 * offering electives at Classes 9-12 can genuinely run past a page.
 *
 * ## Nothing here deletes anything
 *
 * There is no delete endpoint and there is not meant to be one (ADR-0019, the same reasoning as
 * classes and sections): a subject that stops being taught is deactivated, not removed, because the
 * timetable and marks modules that come next will reference one and by then it is too late to
 * decide that deleting it was wrong.
 */
@Component({
  selector: 'cb-subjects',
  imports: [ReactiveFormsModule, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './subjects.html',
  styleUrl: './subjects.scss',
})
export class Subjects {
  private readonly api = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /** Whether to offer adding or correcting a subject. `academics:subject:manage` on the wire. */
  protected readonly canManageSubjects = permitted(Permissions.SUBJECT_MANAGE);

  protected readonly pageSize = SUBJECT_PAGE_SIZE;

  protected readonly search = this.formBuilder.control('');

  protected readonly form = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    code: ['', [Validators.required, Validators.maxLength(CODE_MAX_LENGTH)]],
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly Subject[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly editing = signal<Editor>(null);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  /** VAL_001's `details`, keyed by field name — the server's own reason, when it disagrees. */
  private readonly serverFieldErrors = signal<Readonly<Record<string, string>>>({});
  private readonly attempted = signal(false);
  private readonly revision = signal(0);
  /** The name or code a write was refused for, so a clash can be explained against its own row. */
  private readonly clash = signal<{ readonly name: string; readonly code: string } | null>(null);

  /** What just happened, said once, in the live region. */
  protected readonly announcement = signal('');

  /** Only the newest request may paint; a keystroke and a page change can land in either order. */
  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly searching = computed(() => {
    this.revision();
    return this.search.getRawValue().trim() !== '';
  });

  protected readonly adding = computed(() => this.editing() === 'new');
  protected readonly editingId = computed(() => {
    const editing = this.editing();
    return editing === 'new' ? null : editing;
  });

  protected readonly editorHeading = computed(() =>
    this.adding() ? 'Add a subject' : 'Edit this subject',
  );

  protected readonly view = computed<readonly SubjectRow[]>(() =>
    this.rows().map((subject) => ({
      id: subject.id,
      name: subject.name,
      code: subject.code,
      active: subject.active,
      editButtonId: `subject-edit-${subject.id}`,
      activeButtonId: `subject-active-${subject.id}`,
    })),
  );

  /** "1–25 of 42". Counted off the rows actually received, so the last page reads correctly. */
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

  protected readonly fieldErrors = computed(() => {
    this.revision();
    this.attempted();
    const fromServer = this.serverFieldErrors();
    return {
      name:
        fromServer['name'] ??
        this.messageFor('name', 'Give the subject a name, for example Mathematics.'),
      code:
        fromServer['code'] ??
        this.messageFor('code', 'Give the subject a short code, for example MATH.'),
    };
  });

  /**
   * A duplicate name or code, said against the row that already holds it.
   *
   * Worth the trouble because the confusing case is the common one: a subject keeps its name and
   * its code after it stops being taught, so "Hindi already exists" is baffling until someone
   * notices Hindi is still there, switched off. Naming it turns a dead end into an instruction.
   */
  private readonly ownerOfClash = computed(() => {
    const wanted = this.clash();
    if (!wanted) {
      return null;
    }
    const byName = this.view().find(
      (row) => row.name.trim().toLowerCase() === wanted.name.trim().toLowerCase(),
    );
    const byCode = this.view().find(
      (row) => row.code.trim().toLowerCase() === wanted.code.trim().toLowerCase(),
    );
    return { byName, byCode };
  });

  protected readonly saveFailure = computed(() => {
    const code = this.saveFailureCode();
    if (code === DUPLICATE_NAME || code === DUPLICATE_CODE) {
      const owner =
        code === DUPLICATE_NAME ? this.ownerOfClash()?.byName : this.ownerOfClash()?.byCode;
      const what = code === DUPLICATE_NAME ? 'name' : 'code';
      if (owner && !owner.active) {
        return {
          title: `That ${what} is already used by a subject that has stopped being taught`,
          detail: `${owner.name} (${owner.code}) kept its ${what} when it was retired. Reinstate it rather than adding a second one.`,
        };
      }
      return {
        title: `A subject already has that ${what}`,
        detail: owner
          ? `${owner.name} (${owner.code}) is already using it.`
          : `Something on this list already has it — possibly a subject that has stopped being taught, because retiring one does not free its ${what}.`,
      };
    }
    switch (code) {
      case null:
        return null;
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change subjects',
          detail: 'Ask your principal to add "Manage subjects" to your role.',
        };
      default:
        return {
          title: 'Could not save this subject',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  constructor() {
    this.load();

    this.search.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));

    this.search.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        // A new search starts again from the first page: page four of the previous one is either
        // the wrong rows or an empty page, and both look like a broken screen.
        this.page.set(0);
        this.load();
      });

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
      if (Object.keys(this.serverFieldErrors()).length > 0) {
        this.serverFieldErrors.set({});
      }
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

  // ── The editor ───────────────────────────────────────────────────────────────────────────

  protected startAdd(): void {
    this.resetEditor();
    this.form.reset({ name: '', code: '' }, { emitEvent: false });
    this.editing.set('new');
    this.focusAfterRender('#subject-name');
  }

  protected startEdit(row: SubjectRow): void {
    const subject = this.rows().find((candidate) => candidate.id === row.id);
    if (!subject) {
      return;
    }
    this.resetEditor();
    this.form.reset({ name: subject.name, code: subject.code }, { emitEvent: false });
    this.editing.set(subject.id);
    this.focusAfterRender('#subject-name');
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    const id = this.editingId();
    this.editing.set(null);
    this.focusAfterRender(id ? `#subject-edit-${id}` : '#subject-add');
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      this.focusAfterRender(this.fieldErrors().name ? '#subject-name' : '#subject-code');
      return;
    }

    const value = this.form.getRawValue();
    const request = { name: value.name.trim(), code: value.code.trim() };
    const editingId = this.editingId();

    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.serverFieldErrors.set({});
    this.clash.set(request);
    this.announcement.set('');
    this.form.disable({ emitEvent: false });

    const call = editingId
      ? this.api.updateSubject(editingId, {
          ...request,
          // Editing must not retire or reinstate a subject as a side effect, so its current state
          // goes back with the new name and code.
          active: this.subjectById(editingId)?.active ?? true,
        })
      : this.api.createSubject(request);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.enable({ emitEvent: false });
        this.editing.set(null);
        this.announcement.set(
          editingId ? `${request.name} was saved.` : `${request.name} was added.`,
        );
        this.refresh(() =>
          this.focusAfterRender(editingId ? `#subject-edit-${editingId}` : '#subject-add'),
        );
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.form.enable({ emitEvent: false });
        this.saveFailureCode.set(apiErrorCode(error));
        this.serverFieldErrors.set(apiErrorDetails(error));
      },
    });
  }

  // ── Active and inactive ──────────────────────────────────────────────────────────────────

  protected toggle(row: SubjectRow): void {
    if (this.saving()) {
      return;
    }
    const subject = this.subjectById(row.id);
    if (!subject) {
      return;
    }
    const next = !row.active;
    this.saving.set(true);
    this.saveFailureCode.set(null);

    this.api
      .updateSubject(row.id, { name: subject.name, code: subject.code, active: next })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.announcement.set(
            next
              ? `${row.name} is being taught again.`
              : `${row.name} has been retired. It can be brought back at any time.`,
          );
          this.refresh(() => this.focusAfterRender(`#${row.activeButtonId}`));
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
          this.focusAfterRender(`#${row.activeButtonId}`);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .subjects({ page: this.page(), size: this.pageSize, q: this.search.getRawValue() })
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
   * Re-read the page after a write, without blanking the screen.
   *
   * A failure here is deliberately left alone: the write succeeded, so the rows are stale rather
   * than wrong, and turning a successful save into an error banner would be a lie.
   */
  private refresh(then?: () => void): void {
    const request = ++this.latestRequest;
    this.api
      .subjects({ page: this.page(), size: this.pageSize, q: this.search.getRawValue() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set(result.content);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          then?.();
        },
        error: () => then?.(),
      });
  }

  private resetEditor(): void {
    this.attempted.set(false);
    this.saveFailureCode.set(null);
    this.serverFieldErrors.set({});
    this.clash.set(null);
    this.announcement.set('');
    this.form.enable({ emitEvent: false });
    this.form.markAsUntouched();
  }

  private subjectById(id: string): Subject | undefined {
    return this.rows().find((candidate) => candidate.id === id);
  }

  private messageFor(name: 'name' | 'code', required: string): string | null {
    const control = this.form.controls[name];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return required;
    }
    if (control.hasError('maxlength')) {
      const max = name === 'name' ? NAME_MAX_LENGTH : CODE_MAX_LENGTH;
      return `A subject ${name} is ${max} characters or fewer.`;
    }
    return null;
  }

  /**
   * Move focus once the DOM has caught up with the signal that changed.
   *
   * The app is zoneless, so setting a signal does not update the DOM before the next line runs;
   * querying here would find the element that is about to be replaced, or nothing at all.
   */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
