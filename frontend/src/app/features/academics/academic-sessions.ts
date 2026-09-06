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
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { AcademicSession } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { Dialog } from '../../shared/components/dialog/dialog';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED, DUPLICATE_SESSION_NAME, formatDay } from './academics-shared';

/** The backend's own limit, restated so a name is refused before the round trip rather than after. */
const NAME_MAX_LENGTH = 40;

/** Which editor is open: nothing, a new session, or the session with this id. */
type Editing = { readonly kind: 'new' } | { readonly kind: 'edit'; readonly id: string } | null;

/** One session with everything the template needs already decided. */
interface SessionRow {
  readonly id: string;
  readonly name: string;
  readonly range: string;
  readonly current: boolean;
  readonly editButtonId: string;
  readonly makeCurrentButtonId: string;
}

/**
 * The school's academic years (ADR-0019).
 *
 * ## The current session is the point of this screen
 *
 * Everything academic — enrolments, attendance, marks, fees — is recorded against whichever
 * session is current, so "which year am I looking at" is the single most consequential thing here
 * and the one thing a reader must never have to work out. It is said twice on purpose: once as a
 * line under the heading, which is legible without scrolling however many years the school has
 * accumulated, and once on the row itself, which is where someone deciding whether to switch is
 * actually looking. Neither says it in colour alone.
 *
 * ## Making one current asks first
 *
 * The switch changes what the whole school sees, and the API applies it in one transaction that
 * also clears the previous one. A misplaced tap on a phone should not be able to do that silently,
 * so it goes through `cb-dialog` — which, unlike `window.confirm`, can put the consequence in a
 * sentence and the verb on the button. See that component for why the native prompt was not enough.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008 warns against re-deriving authorization in the client, so there is no `canActivate`
 * checking `academics:session:read`. The server leaves the menu item out for anyone without it and
 * the endpoint enforces it independently; typing the URL therefore lands here and gets a 403 this
 * screen explains calmly, exactly as the audit log does.
 */
@Component({
  selector: 'cb-academic-sessions',
  imports: [ReactiveFormsModule, NgTemplateOutlet, Button, Dialog, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './academic-sessions.html',
  styleUrl: './academic-sessions.scss',
})
export class AcademicSessions {
  private readonly api = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly form = this.formBuilder.group(
    {
      name: ['', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
      startsOn: ['', Validators.required],
      endsOn: ['', Validators.required],
    },
    { validators: endsAfterStart },
  );

  /**
   * Whether to offer adding a year, editing one, or moving the school into one.
   *
   * All three endpoints are gated on `academics:session:manage`, so all three controls share one
   * check. Someone with the read alone still sees the list and which year is current — which is
   * the whole of what the read entitles them to.
   */
  protected readonly canManageSessions = permitted(Permissions.SESSION_MANAGE);

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly loadFailureCode = signal<string | null>(null);
  protected readonly sessions = signal<readonly AcademicSession[]>([]);

  protected readonly editing = signal<Editing>(null);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  private readonly attempted = signal(false);
  private readonly serverErrors = signal<Readonly<Record<string, string>>>({});

  /** The session waiting on a confirmed "make this current", or null. */
  protected readonly confirming = signal<AcademicSession | null>(null);
  protected readonly switching = signal(false);
  protected readonly switchFailureCode = signal<string | null>(null);
  /** What just happened, for the status line. Cleared by the next thing the user does. */
  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.loadFailureCode() === ACCESS_DENIED);
  protected readonly loadFailed = computed(
    () => this.loadFailureCode() !== null && this.loadFailureCode() !== ACCESS_DENIED,
  );

  protected readonly view = computed<readonly SessionRow[]>(() =>
    this.sessions().map((session) => ({
      id: session.id,
      name: session.name,
      range: `${formatDay(session.startsOn)} – ${formatDay(session.endsOn)}`,
      current: session.current,
      editButtonId: `session-edit-${session.id}`,
      makeCurrentButtonId: `session-current-${session.id}`,
    })),
  );

  protected readonly current = computed(() => this.view().find((row) => row.current) ?? null);

  protected readonly addingNew = computed(() => this.editing()?.kind === 'new');

  protected readonly editingId = computed(() => {
    const editing = this.editing();
    return editing?.kind === 'edit' ? editing.id : null;
  });

  /** The heading over the editor, so the form says which of the two things it is doing. */
  protected readonly editorHeading = computed(() =>
    this.addingNew() ? 'Add an academic session' : 'Edit this session',
  );

  protected readonly fieldErrors = computed(() => {
    // Read so the messages recompute as the form changes; the values come off the controls.
    this.formValue();
    this.attempted();
    this.serverErrors();
    return {
      name: this.messageFor('name'),
      startsOn: this.messageFor('startsOn'),
      endsOn: this.messageFor('endsOn'),
    };
  });

  protected readonly saveFailure = computed(() => {
    // A failure that named fields is a validation failure whatever code it travelled under. The
    // "ends before it starts" rule comes back as a 422 keyed on `endsOn` rather than as a
    // `VAL_001` — deliberately named after a field this form has rather than after the rule — and
    // branching on the code alone would have shown it as "check your connection".
    if (this.saveFailureCode() !== null && Object.keys(this.serverErrors()).length > 0) {
      return {
        title: 'Some of these details were refused',
        detail: 'The fields marked below need correcting.',
      };
    }
    switch (this.saveFailureCode()) {
      case null:
        return null;
      case 'VAL_001':
        return {
          title: 'These dates were refused',
          detail: 'The fields marked below need correcting.',
        };
      case DUPLICATE_SESSION_NAME:
        return {
          title: 'A session with that name already exists',
          detail:
            'Each academic year is named once. If you meant to change the year already on this ' +
            'list, edit it rather than adding a second with the same name.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change academic sessions',
          detail: 'Ask your principal to add "Manage academic sessions" to your role.',
        };
      default:
        return {
          title: 'Could not save the session',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  protected readonly switchFailure = computed(() => {
    switch (this.switchFailureCode()) {
      case null:
        return null;
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change the current session',
          detail: 'Ask your principal to add "Manage academic sessions" to your role.',
        };
      default:
        return {
          title: 'Could not change the current session',
          detail:
            'The school is still on the session it was on. Check your connection and try again.',
        };
    }
  });

  /** Tracks the form so `fieldErrors` recomputes; the values themselves come off the controls. */
  private readonly formValue = signal(0);

  constructor() {
    this.load();

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formValue.update((count) => count + 1);
      if (Object.keys(this.serverErrors()).length > 0) {
        this.serverErrors.set({});
      }
    });
  }

  protected reload(): void {
    this.load();
  }

  // ── The editor ───────────────────────────────────────────────────────────────────────────

  protected startCreate(): void {
    this.resetEditor();
    this.editing.set({ kind: 'new' });
    this.focusAfterRender('#session-name');
  }

  protected startEdit(row: SessionRow): void {
    const session = this.sessions().find((candidate) => candidate.id === row.id);
    if (!session) {
      return;
    }
    this.resetEditor();
    this.form.setValue(
      { name: session.name, startsOn: session.startsOn, endsOn: session.endsOn },
      { emitEvent: false },
    );
    this.editing.set({ kind: 'edit', id: session.id });
    this.focusAfterRender('#session-name');
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    const wasEditing = this.editingId();
    this.resetEditor();
    this.editing.set(null);
    // Back to the control that opened the editor, so a keyboard user is not returned to the top
    // of the document with no idea where the row they were on went.
    this.focusAfterRender(wasEditing ? `#session-edit-${wasEditing}` : '#session-add');
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const editing = this.editing();
    if (!editing) {
      return;
    }

    const value = this.form.getRawValue();
    const request = { name: value.name.trim(), startsOn: value.startsOn, endsOn: value.endsOn };

    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.announcement.set('');
    this.form.disable({ emitEvent: false });

    const call =
      editing.kind === 'new'
        ? this.api.createSession(request)
        : this.api.updateSession(editing.id, request);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (session) => {
        this.finishSaving();
        this.resetEditor();
        this.editing.set(null);
        this.announcement.set(
          editing.kind === 'new' ? `${session.name} added.` : `${session.name} saved.`,
        );
        // The list comes back from the server rather than being spliced here: it is ordered newest
        // first, and only the server knows where a session the user just dated belongs in it.
        this.refresh(() => this.focusAfterRender(`#session-edit-${session.id}`));
      },
      error: (error: unknown) => {
        this.finishSaving();
        this.saveFailureCode.set(apiErrorCode(error));
        this.serverErrors.set(this.knownFieldErrors(error));
      },
    });
  }

  // ── Making one current ───────────────────────────────────────────────────────────────────

  protected askToMakeCurrent(row: SessionRow): void {
    const session = this.sessions().find((candidate) => candidate.id === row.id);
    if (session && !session.current) {
      this.switchFailureCode.set(null);
      this.announcement.set('');
      this.confirming.set(session);
    }
  }

  protected cancelMakeCurrent(): void {
    const target = this.confirming();
    if (this.switching() || !target) {
      return;
    }
    this.confirming.set(null);
    // A dialog emits and never closes itself, and returning focus is the caller's job — so the
    // button that opened it gets focus back rather than the document.
    this.focusAfterRender(`#session-current-${target.id}`);
  }

  protected confirmMakeCurrent(): void {
    const target = this.confirming();
    if (this.switching() || !target) {
      return;
    }

    this.switching.set(true);
    this.switchFailureCode.set(null);

    this.api
      .makeSessionCurrent(target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          this.switching.set(false);
          this.confirming.set(null);
          this.announcement.set(`${target.name} is now the current session.`);
          // Two rows changed, not one — this session became current and the previous one stopped
          // being current, in the same transaction — so the endpoint answers with the whole list
          // and that list is what gets rendered. Patching the one row named in the request would
          // leave two sessions both showing as current until something else refetched.
          this.sessions.set(sessions);
          this.focusAfterRender(`#session-edit-${target.id}`);
        },
        error: (error: unknown) => {
          this.switching.set(false);
          this.confirming.set(null);
          this.switchFailureCode.set(apiErrorCode(error));
          this.focusAfterRender(`#session-current-${target.id}`);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    this.loading.set(true);
    this.loadFailureCode.set(null);

    this.api
      .sessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          this.sessions.set(sessions);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.sessions.set([]);
          this.loading.set(false);
          this.loadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  /**
   * Re-read the list after a write, without blanking the screen.
   *
   * A full `load()` would drop the rows and show "Loading…" for the length of a request the user
   * did not ask for, which reads as the screen having lost what they just did. A failure here is
   * left as it is on purpose: the write succeeded, so the rows on screen are stale rather than
   * wrong, and turning a successful save into an error banner would be a lie.
   */
  private refresh(then?: () => void): void {
    this.api
      .sessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          this.sessions.set(sessions);
          then?.();
        },
        error: () => then?.(),
      });
  }

  private resetEditor(): void {
    this.form.reset({ name: '', startsOn: '', endsOn: '' }, { emitEvent: false });
    this.form.enable({ emitEvent: false });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.attempted.set(false);
    this.serverErrors.set({});
    this.saveFailureCode.set(null);
  }

  private finishSaving(): void {
    this.saving.set(false);
    this.form.enable({ emitEvent: false });
  }

  private knownFieldErrors(error: unknown): Readonly<Record<string, string>> {
    const known: Record<string, string> = {};
    for (const [field, message] of Object.entries(apiErrorDetails(error))) {
      if (Object.prototype.hasOwnProperty.call(this.form.controls, field)) {
        known[field] = message;
      }
    }
    return known;
  }

  private messageFor(name: 'name' | 'startsOn' | 'endsOn'): string | null {
    const fromServer = this.serverErrors()[name];
    if (fromServer) {
      return fromServer;
    }
    const control = this.form.controls[name];
    const shown = control.touched || this.attempted();
    if (!shown) {
      return null;
    }
    if (control.hasError('required')) {
      return REQUIRED[name];
    }
    if (control.hasError('maxlength')) {
      return `A session name is ${NAME_MAX_LENGTH} characters or fewer.`;
    }
    // The range is a property of the pair, so it is reported against the end date — the field
    // someone changes to fix it.
    if (name === 'endsOn' && this.form.hasError('endsBeforeStart')) {
      return 'A session has to end after it starts.';
    }
    return null;
  }

  private focusFirstInvalid(): void {
    const first = (['name', 'startsOn', 'endsOn'] as const).find(
      (name) => this.fieldErrors()[name] !== null,
    );
    this.focusAfterRender(first ? `#session-${FIELD_SUFFIX[first]}` : '#session-name');
  }

  /**
   * Move focus once the DOM has caught up with the signal that changed.
   *
   * The app is zoneless, so setting a signal does not update the DOM before the next line runs;
   * querying for the element here would find the one that is about to be replaced, or nothing at
   * all. `afterNextRender` is the framework's own answer to "after this paint".
   */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}

/** The id suffix each control uses, so a message and a focus target cannot disagree. */
const FIELD_SUFFIX = { name: 'name', startsOn: 'starts', endsOn: 'ends' } as const;

const REQUIRED: Readonly<Record<'name' | 'startsOn' | 'endsOn', string>> = {
  name: 'Give the session a name, for example 2026–27.',
  startsOn: 'Choose the day the session starts.',
  endsOn: 'Choose the day the session ends.',
};

/**
 * The same rule the backend enforces: a session ends after it starts.
 *
 * `yyyy-MM-dd` compares correctly as a string, which is the one thing that format is good for —
 * and it avoids parsing a bare date into a `Date`, which reads it as UTC and can move it a day.
 */
function endsAfterStart(group: AbstractControl): ValidationErrors | null {
  const startsOn = group.get('startsOn')?.value as string;
  const endsOn = group.get('endsOn')?.value as string;
  if (!startsOn || !endsOn) {
    return null;
  }
  return endsOn > startsOn ? null : { endsBeforeStart: true };
}
