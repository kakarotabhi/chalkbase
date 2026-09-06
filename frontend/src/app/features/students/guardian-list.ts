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
import { Observable, debounceTime, distinctUntilChanged } from 'rxjs';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { GUARDIAN_PAGE_SIZE, GuardiansApi } from '../../core/api/guardians-api';
import { GuardianSummary } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED, CONFLICT } from './students-shared';

/** Same pause as the student search: eight characters is one request, not eight. */
const SEARCH_DEBOUNCE_MS = 300;

/** One row, with everything the template needs already decided. */
interface GuardianRow {
  readonly id: string;
  readonly fullName: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly occupation: string | null;
  readonly linkedTo: string;
  readonly editButtonId: string;
}

/**
 * The school's guardians, as people rather than as fields on a child (ADR-0020 §5).
 *
 * ## Why this screen exists at all
 *
 * Because a guardian is shared. A father with four children here is one row, and this is the one
 * place his phone number is corrected — once, for all four. If guardians were copied per student
 * there would be nothing to put on this screen and four numbers to keep in step by hand, which is
 * exactly the failure the model avoids.
 *
 * So the count on each row is not decoration. "Linked to 4 students" is what tells a clerk that
 * editing this row reaches further than the record they came from.
 *
 * ## Nothing is deleted, and there is no way to unlink from here
 *
 * ADR-0020 §6. A guardian who no longer has a child at the school is left alone; a link is ended
 * from the student's own record, where you can see which child it is about. A "remove" button on
 * this screen would be a button that ends a relationship without showing whose.
 *
 * ## Which students, though
 *
 * The count is a number and there is no endpoint that turns it into a list of names, so this screen
 * cannot answer "which four?". That is a real gap and it is stated here rather than papered over
 * with a filter that does not exist.
 *
 * ## Confidential (ADR-0014), and the filters are not in the URL
 *
 * Guardians' names, phone numbers and email addresses. The search text goes to the server as `?q=`
 * because it is a box the user chose to type into; it is never put into the router, because that
 * would mint a link with a family's name in it.
 */
@Component({
  selector: 'cb-guardian-list',
  imports: [ReactiveFormsModule, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guardian-list.html',
  styleUrl: './guardian-list.scss',
})
export class GuardianList {
  private readonly guardians = inject(GuardiansApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly pageSize = GUARDIAN_PAGE_SIZE;

  protected readonly search = this.formBuilder.control('');

  /**
   * No maximum length on any of these, deliberately.
   *
   * The contract states limits for a student's name and admission number and states none here, and
   * guessing one would refuse a name or an occupation the server is willing to accept. The server's
   * own reason arrives in `error.details` and is shown under the field it names.
   */
  protected readonly form = this.formBuilder.group({
    fullName: ['', Validators.required],
    phone: '',
    email: ['', Validators.email],
    occupation: '',
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly GuardianSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);

  /** null when closed, 'new' when adding, or the id of the guardian being edited. */
  protected readonly editing = signal<'new' | string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  protected readonly saveFieldErrors = signal<Readonly<Record<string, string>>>({});
  private readonly attempted = signal(false);
  private readonly revision = signal(0);

  /** What just happened, said once, in one live region. */
  protected readonly announcement = signal('');

  /** Only the newest search may paint; two keystrokes can land in either order. */
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
    this.adding() ? 'Add a guardian' : 'Edit this guardian',
  );

  protected readonly view = computed<readonly GuardianRow[]>(() =>
    this.rows().map((guardian) => ({
      id: guardian.id,
      fullName: guardian.fullName,
      phone: guardian.phone?.trim() || null,
      email: guardian.email?.trim() || null,
      occupation: guardian.occupation?.trim() || null,
      linkedTo: describeLinkCount(guardian.linkedStudentCount),
      editButtonId: `guardian-edit-${guardian.id}`,
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

  protected readonly fieldErrors = computed(() => {
    this.revision();
    this.attempted();
    this.saveFieldErrors();
    return {
      fullName: this.messageFor('fullName', "Enter the guardian's name."),
      phone: this.saveFieldErrors()['phone'] ?? null,
      email: this.messageFor('email', null),
      occupation: this.saveFieldErrors()['occupation'] ?? null,
    };
  });

  protected readonly saveFailure = computed(() => {
    switch (this.saveFailureCode()) {
      case null:
        return null;
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case CONFLICT:
        return {
          title: 'These details clash with a guardian already at this school',
          detail: 'Search for them above — the record you want may already be here.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change guardians',
          detail: 'Ask your principal to add "Manage guardians" to your role.',
        };
      default:
        return {
          title: 'Could not save this guardian',
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
      if (Object.keys(this.saveFieldErrors()).length > 0) {
        this.saveFieldErrors.set({});
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
    this.form.reset({ fullName: '', phone: '', email: '', occupation: '' }, { emitEvent: false });
    this.editing.set('new');
    this.focusAfterRender('#guardian-name');
  }

  protected startEdit(row: GuardianRow): void {
    const guardian = this.rows().find((candidate) => candidate.id === row.id);
    if (!guardian) {
      return;
    }
    this.resetEditor();
    this.form.reset(
      {
        fullName: guardian.fullName,
        phone: guardian.phone ?? '',
        email: guardian.email ?? '',
        occupation: guardian.occupation ?? '',
      },
      { emitEvent: false },
    );
    this.editing.set(guardian.id);
    this.focusAfterRender('#guardian-name');
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    const id = this.editingId();
    this.editing.set(null);
    this.focusAfterRender(id ? `#guardian-edit-${id}` : '#guardian-add');
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      this.focusAfterRender(this.fieldErrors().fullName ? '#guardian-name' : '#guardian-email');
      return;
    }

    const value = this.form.getRawValue();
    const request = {
      fullName: value.fullName.trim(),
      phone: value.phone.trim(),
      email: value.email.trim(),
      occupation: value.occupation.trim(),
    };
    const editingId = this.editingId();

    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});
    this.announcement.set('');
    this.form.disable({ emitEvent: false });

    // Typed as the widest of the two rather than left to inference: the branches answer different
    // shapes — `create` returns the new person, `update` returns nothing — and neither is read
    // here, because the page is re-read from the server afterwards either way.
    const call: Observable<unknown> = editingId
      ? this.guardians.update(editingId, request)
      : this.guardians.create(request);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.enable({ emitEvent: false });
        this.editing.set(null);
        this.announcement.set(
          editingId
            ? `${request.fullName} was saved, for every student they are linked to.`
            : `${request.fullName} was added.`,
        );
        // The page is re-read rather than patched: `linkedStudentCount` is computed per row and a
        // new guardian belongs wherever the server's own ordering puts them, not at the top.
        this.refresh(() =>
          this.focusAfterRender(editingId ? `#guardian-edit-${editingId}` : '#guardian-add'),
        );
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.form.enable({ emitEvent: false });
        this.saveFailureCode.set(apiErrorCode(error));
        this.saveFieldErrors.set(apiErrorDetails(error));
      },
    });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.guardians
      .search({ page: this.page(), size: this.pageSize, q: this.search.getRawValue() })
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
   * A full `load()` would drop the rows and show "Loading…" for the length of a request the user
   * did not ask for, which reads as the screen having lost what they just did. A failure here is
   * left alone on purpose: the write succeeded, so the rows are stale rather than wrong.
   */
  private refresh(then?: () => void): void {
    const request = ++this.latestRequest;
    this.guardians
      .search({ page: this.page(), size: this.pageSize, q: this.search.getRawValue() })
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
    this.saveFieldErrors.set({});
    this.announcement.set('');
    this.form.enable({ emitEvent: false });
    this.form.markAsUntouched();
  }

  private messageFor(name: 'fullName' | 'email', required: string | null): string | null {
    const fromServer = this.saveFieldErrors()[name];
    if (fromServer) {
      return fromServer;
    }
    const control = this.form.controls[name];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (required && control.hasError('required')) {
      return required;
    }
    if (control.hasError('email')) {
      return 'That does not look like an email address.';
    }
    return null;
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}

/** "Linked to 4 students". The number is what says this record reaches beyond one child. */
function describeLinkCount(count: number): string {
  if (!count || count < 1) {
    return 'Not linked to any student';
  }
  return count === 1 ? 'Linked to 1 student' : `Linked to ${count} students`;
}
