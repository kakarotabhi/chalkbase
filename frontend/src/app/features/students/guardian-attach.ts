import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, switchMap } from 'rxjs';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { GUARDIAN_PAGE_SIZE, GuardiansApi } from '../../core/api/guardians-api';
import { GuardianRelation, GuardianSummary } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Button } from '../../shared/components/button/button';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  CONFLICT,
  GUARDIAN_ALREADY_LINKED,
  RELATION_OPTIONS,
} from './students-shared';

/** Same pause as the student search: eight characters is one request, not eight. */
const SEARCH_DEBOUNCE_MS = 300;

/** Which of the three steps is on screen. */
type Step = 'search' | 'link' | 'create';

/** One guardian in the result list, with the decision about whether they can be picked. */
interface ResultRow {
  readonly id: string;
  readonly fullName: string;
  readonly phone: string | null;
  readonly occupation: string | null;
  /** "Linked to 4 students" — the sentence that makes the shared record visible. */
  readonly linkedTo: string;
  readonly alreadyLinked: boolean;
  readonly chooseButtonId: string;
}

/**
 * Attaching a guardian to a student — search first, create only if the search finds nobody.
 *
 * ## Why the order of the two options is the whole design
 *
 * ADR-0020 §5 makes a guardian a **shared** record on purpose: a father with four children at this
 * school is one row, so correcting his phone number once corrects it for all four. The value of
 * that is entirely destroyed by a screen that offers "Add a new guardian" as the obvious thing to
 * do, because the office will take it every time — and nothing in the system will know that the
 * four rows called "Suresh Kumar" are one man, or that three of the numbers stopped answering last
 * March. The constraint would still be in the database and would be decorative.
 *
 * So this screen opens on the existing guardians, **already listed before anything is typed**.
 * Searching is not a step the user has to think to take; it is where they land. Creating is
 * reachable, always — a first child of a new family is a real case and a screen that hid it would
 * be its own kind of dishonest — but it is the second thing on the page, and after a search that
 * found nothing it is offered by name, which is the moment it is actually the right answer.
 *
 * ## Creating is two requests, and it can half-fail
 *
 * There is no endpoint that creates a person and links them in one go. So "Add and link" is
 * `POST /api/guardians` followed by `POST /api/students/{id}/guardians`, and if the second fails
 * the guardian exists and is attached to nobody. That is said out loud rather than reported as
 * "could not save", because the recovery is different: the person is now on the guardian list and
 * searching for them will find them. Retrying the whole thing would create a duplicate, which is
 * the exact failure this screen exists to prevent.
 *
 * ## Confidential (ADR-0014)
 *
 * Guardians' names and phone numbers. Nothing here is logged, and the search box is
 * `autocomplete="off"` so a shared counter machine does not offer the last six families' names to
 * whoever clicks in next.
 */
@Component({
  selector: 'cb-guardian-attach',
  imports: [ReactiveFormsModule, Button, Checkbox, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guardian-attach.html',
  styleUrl: './guardian-attach.scss',
})
export class GuardianAttach {
  readonly studentId = input.required<string>();
  /** Who is already on this child, so the list can say so instead of offering a second link. */
  readonly linkedGuardianIds = input<readonly string[]>([]);

  /** A guardian is now attached. The parent re-reads the student and closes this. */
  readonly linked = output<string>();
  readonly cancelled = output<void>();

  private readonly guardians = inject(GuardiansApi);
  private readonly students = inject(StudentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly relationOptions = RELATION_OPTIONS;

  protected readonly search = this.formBuilder.control('');

  /** Relation and main-contact, filled in on both the "link" and the "create" step. */
  protected readonly link = this.formBuilder.group({
    relation: ['', Validators.required],
    primary: false,
  });

  /** The new person, when the search genuinely found nobody. */
  protected readonly newGuardian = this.formBuilder.group({
    fullName: ['', Validators.required],
    phone: '',
    email: ['', Validators.email],
    occupation: '',
  });

  protected readonly step = signal<Step>('search');
  protected readonly chosen = signal<GuardianSummary | null>(null);

  protected readonly searching = signal(true);
  protected readonly searchFailureCode = signal<string | null>(null);
  protected readonly results = signal<readonly GuardianSummary[]>([]);
  protected readonly totalElements = signal(0);

  protected readonly busy = signal(false);
  protected readonly writeFailureCode = signal<string | null>(null);
  protected readonly writeFieldErrors = signal<Readonly<Record<string, string>>>({});
  /**
   * Set when the guardian record was created but the link that should have followed was refused.
   * A different sentence from an ordinary failure, because the recovery is different.
   */
  protected readonly orphanedGuardian = signal<string | null>(null);
  private readonly attempted = signal(false);
  private readonly revision = signal(0);

  /** Only the newest search may paint; two keystrokes are two requests and they can land in any order. */
  private latestSearch = 0;

  protected readonly searchText = computed(() => {
    this.revision();
    return this.search.getRawValue().trim();
  });

  protected readonly searchForbidden = computed(() => this.searchFailureCode() === ACCESS_DENIED);
  protected readonly searchFailed = computed(
    () => this.searchFailureCode() !== null && this.searchFailureCode() !== ACCESS_DENIED,
  );

  protected readonly resultRows = computed<readonly ResultRow[]>(() => {
    const linked = new Set(this.linkedGuardianIds());
    return this.results().map((guardian) => ({
      id: guardian.id,
      fullName: guardian.fullName,
      phone: guardian.phone?.trim() || null,
      occupation: guardian.occupation?.trim() || null,
      linkedTo: describeLinkCount(guardian.linkedStudentCount),
      alreadyLinked: linked.has(guardian.id),
      chooseButtonId: `guardian-choose-${guardian.id}`,
    }));
  });

  /** How many students the chosen guardian is already on — the sentence that says "same person". */
  protected readonly chosenLinkedTo = computed(() => {
    const guardian = this.chosen();
    return guardian ? describeLinkCount(guardian.linkedStudentCount) : '';
  });

  /** True once a search has run and come back with nothing. The moment "create" is the right answer. */
  protected readonly foundNobody = computed(
    () => !this.searching() && this.searchFailureCode() === null && this.results().length === 0,
  );

  /** "Showing the first 25 of 118" — so nobody concludes a guardian is absent when they are page 3. */
  protected readonly moreThanShown = computed(() =>
    this.totalElements() > this.results().length
      ? `Showing the first ${this.results().length} of ${this.totalElements()}. Type more of the name to narrow this.`
      : null,
  );

  protected readonly relationError = computed(() => {
    this.revision();
    this.attempted();
    const control = this.link.controls.relation;
    if (!control.touched && !this.attempted()) {
      return null;
    }
    return control.hasError('required') ? 'Choose what this person is to the student.' : null;
  });

  protected readonly nameError = computed(() => {
    this.revision();
    this.attempted();
    const fromServer = this.writeFieldErrors()['fullName'];
    if (fromServer) {
      return fromServer;
    }
    const control = this.newGuardian.controls.fullName;
    if (!control.touched && !this.attempted()) {
      return null;
    }
    return control.hasError('required') ? "Enter the guardian's name." : null;
  });

  protected readonly emailError = computed(() => {
    this.revision();
    this.attempted();
    const fromServer = this.writeFieldErrors()['email'];
    if (fromServer) {
      return fromServer;
    }
    const control = this.newGuardian.controls.email;
    if (!control.touched && !this.attempted()) {
      return null;
    }
    return control.hasError('email') ? 'That does not look like an email address.' : null;
  });

  protected readonly phoneError = computed(() => {
    this.revision();
    return this.writeFieldErrors()['phone'] ?? null;
  });

  protected readonly writeFailure = computed(() => {
    const code = this.writeFailureCode();
    if (code === null) {
      return null;
    }
    switch (code) {
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case GUARDIAN_ALREADY_LINKED:
        return {
          title: 'This guardian is already linked to this student',
          detail: 'They are on the list above. Nothing was added a second time.',
        };
      case CONFLICT:
        return {
          title: 'Could not attach this guardian',
          detail:
            'They may already be attached to this student. Close this and reload the record to ' +
            'see who is attached now.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change guardians',
          detail: 'Ask your principal to add "Manage guardians" to your role.',
        };
      default:
        return {
          title: 'Could not attach this guardian',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  constructor() {
    // The list is populated before a key is pressed. This is the point of the screen: if it opened
    // empty saying "type to search", the first instinct would be to reach for "add a new one",
    // which is the outcome ADR-0020 §5 is trying to avoid.
    this.runSearch('');

    this.search.valueChanges
      .pipe(
        // Not `debounceTime` alone: an empty box after a cleared search should still re-list
        // everybody, so this deliberately does not skip the empty value.
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.revision.update((count) => count + 1));

    this.search.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.runSearch(value));

    this.link.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));

    this.newGuardian.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));

    afterNextRender(() => this.focus('#guardian-search'), { injector: this.injector });
  }

  // ── Step one: find them ──────────────────────────────────────────────────────────────────

  protected retrySearch(): void {
    this.runSearch(this.search.getRawValue());
  }

  protected choose(row: ResultRow): void {
    if (row.alreadyLinked || this.busy()) {
      return;
    }
    const guardian = this.results().find((candidate) => candidate.id === row.id);
    if (!guardian) {
      return;
    }
    this.resetWrite();
    this.link.reset({ relation: '', primary: false }, { emitEvent: false });
    this.chosen.set(guardian);
    this.step.set('link');
    this.focusAfterRender('#guardian-relation');
  }

  /** Leaves the search and offers a blank person, with whatever was typed already in the name. */
  protected startCreate(): void {
    this.resetWrite();
    this.link.reset({ relation: '', primary: false }, { emitEvent: false });
    this.newGuardian.reset(
      { fullName: this.searchText(), phone: '', email: '', occupation: '' },
      { emitEvent: false },
    );
    this.chosen.set(null);
    this.step.set('create');
    this.focusAfterRender('#guardian-new-name');
  }

  protected backToSearch(): void {
    if (this.busy()) {
      return;
    }
    this.resetWrite();
    this.chosen.set(null);
    this.step.set('search');
    this.focusAfterRender('#guardian-search');
  }

  protected cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  // ── Step two: say what they are to this child ────────────────────────────────────────────

  /** Links a guardian who already exists. One request, and it is the ordinary path. */
  protected linkChosen(): void {
    const guardian = this.chosen();
    if (this.busy() || !guardian) {
      return;
    }
    this.attempted.set(true);
    this.link.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.link.invalid) {
      this.focus('#guardian-relation');
      return;
    }

    const { relation, primary } = this.link.getRawValue();
    this.startWrite();
    this.students
      .linkGuardian(this.studentId(), {
        guardianId: guardian.id,
        relation: relation as GuardianRelation,
        primary,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.linked.emit(guardian.fullName);
        },
        error: (error: unknown) => this.failWrite(error),
      });
  }

  /**
   * Creates the person, then links them. Two requests, because there is no endpoint that does both.
   *
   * `switchMap` rather than a nested subscribe so the second request is part of one stream and
   * cannot outlive the component. If the second half fails, `orphanedGuardian` records that the
   * person now exists — retrying from the top would create a second copy of them, which is the one
   * outcome this whole screen is built to avoid.
   */
  protected createAndLink(): void {
    if (this.busy()) {
      return;
    }
    this.attempted.set(true);
    this.newGuardian.markAllAsTouched();
    this.link.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.newGuardian.invalid) {
      this.focus('#guardian-new-name');
      return;
    }
    if (this.link.invalid) {
      this.focus('#guardian-new-relation');
      return;
    }

    const person = this.newGuardian.getRawValue();
    const { relation, primary } = this.link.getRawValue();
    let created: GuardianSummary | null = null;

    this.startWrite();
    this.guardians
      .create({
        fullName: person.fullName.trim(),
        phone: person.phone.trim(),
        email: person.email.trim(),
        occupation: person.occupation.trim(),
      })
      .pipe(
        switchMap((guardian) => {
          created = guardian;
          return this.students.linkGuardian(this.studentId(), {
            guardianId: guardian.id,
            relation: relation as GuardianRelation,
            primary,
          });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.linked.emit(person.fullName.trim());
        },
        error: (error: unknown) => {
          if (created !== null) {
            this.orphanedGuardian.set(created.fullName);
          }
          this.failWrite(error);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private runSearch(q: string): void {
    const request = ++this.latestSearch;
    this.searching.set(true);
    this.searchFailureCode.set(null);

    this.guardians
      .search({ page: 0, size: GUARDIAN_PAGE_SIZE, q: q.trim() || null })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestSearch) {
            return;
          }
          this.results.set(result.content);
          this.totalElements.set(result.totalElements);
          this.searching.set(false);
        },
        error: (error: unknown) => {
          if (request !== this.latestSearch) {
            return;
          }
          this.results.set([]);
          this.totalElements.set(0);
          this.searching.set(false);
          this.searchFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private startWrite(): void {
    this.busy.set(true);
    this.writeFailureCode.set(null);
    this.writeFieldErrors.set({});
    this.orphanedGuardian.set(null);
  }

  private failWrite(error: unknown): void {
    this.busy.set(false);
    this.writeFailureCode.set(apiErrorCode(error));
    this.writeFieldErrors.set(apiErrorDetails(error));
  }

  private resetWrite(): void {
    this.attempted.set(false);
    this.busy.set(false);
    this.writeFailureCode.set(null);
    this.writeFieldErrors.set({});
    this.orphanedGuardian.set(null);
  }

  private focus(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.focus(selector), { injector: this.injector });
  }
}

/** "Linked to 4 students", "Not linked to anyone yet". The number is the point of the row. */
function describeLinkCount(count: number): string {
  if (!count || count < 1) {
    return 'Not linked to any student yet';
  }
  return count === 1 ? 'Linked to 1 student' : `Linked to ${count} students`;
}
