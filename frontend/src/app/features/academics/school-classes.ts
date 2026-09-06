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
import { NgTemplateOutlet } from '@angular/common';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { SchoolClass, Section } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED, DUPLICATE_SECTION_NAME } from './academics-shared';

/**
 * A name another row already has.
 *
 * The uniqueness constraint does not account for `active`, so a class that stopped running still
 * holds its name — which makes this the error a school hits when it deactivates "Class 5" and
 * later tries to add it back instead of switching it on again. That is exactly the confusion a
 * hidden inactive row would produce, and it is why this screen never hides one.
 */
const DUPLICATE_NAME = 'ACAD_004';

/** Which of the four editors is open, and on what. Only ever one at a time. */
type Editor =
  | { readonly kind: 'new-class' }
  | { readonly kind: 'class'; readonly id: string }
  | { readonly kind: 'new-section'; readonly classId: string }
  | { readonly kind: 'section'; readonly id: string; readonly classId: string }
  | null;

interface SectionRow {
  readonly id: string;
  readonly name: string;
  readonly active: boolean;
  readonly renameButtonId: string;
  readonly activeButtonId: string;
  /** "Section A of Class 5" — an action's accessible name has to work out of context. */
  readonly qualifiedName: string;
}

interface ClassRow {
  readonly id: string;
  readonly name: string;
  readonly active: boolean;
  /** Its place in the ladder as shown, counting from one. Not `sequence`, which is the server's. */
  readonly position: number;
  readonly isFirst: boolean;
  readonly isLast: boolean;
  readonly sections: readonly SectionRow[];
  readonly activeSectionCount: number;
  readonly moveUpId: string;
  readonly moveDownId: string;
  readonly renameButtonId: string;
  readonly activeButtonId: string;
  readonly addSectionButtonId: string;
}

/**
 * The school's ladder of classes, and the sections inside each one (ADR-0019).
 *
 * ## Reordering is buttons, not a drag surface
 *
 * Drag-and-drop is the obvious answer to "put these in order" and it is the wrong one here.
 * A drag target has to be held and moved precisely, which is hard on a phone and impossible from a
 * keyboard without building a second, parallel interaction anyway; screen-reader support for it is
 * something almost every implementation gets wrong, and the CDK's own drag-drop needs bespoke
 * keyboard handling and live announcements bolted on before it is usable without a mouse. Move up
 * and move down are one tap, one key, and one sentence to announce — and this is a list a school
 * puts in order once and then barely touches, so the ceiling on how fast reordering can be is not
 * a ceiling anybody is pressed against. If a school ever has to reorder forty rungs at a time,
 * drag becomes worth its cost **in addition to** these buttons, never instead of them.
 *
 * ## Every id, every time
 *
 * `PUT /api/academics/classes/order` takes the whole ladder, and the server refuses a partial list.
 * That is not fussiness: a client that sent only the rows it happened to be showing would have the
 * server renumber the survivors and close the gap, and the school would silently lose a rung. So
 * this screen never filters the ladder it holds — inactive classes are **shown in place**, marked,
 * rather than hidden behind a toggle. Showing them is what makes the order the user sees the same
 * order that gets sent, and it is also the honest way to present a decision that is reversible.
 *
 * ## Optimistic, and reconciled
 *
 * A move repaints immediately and then renders whatever the server sends back, not the guess. If
 * the call fails the ladder goes back to where it was and says so. Two moves in quick succession
 * are sequenced by `latestReorder`: each request carries a complete order, so the last one is the
 * user's intent and any earlier answer that arrives late is dropped rather than repainting an
 * order nobody asked for.
 *
 * ## Nothing here deletes anything
 *
 * There is no delete endpoint and there is not meant to be one (ADR-0019). A class or section that
 * stops running is deactivated and can be brought back; a mistyped one is renamed. So there is no
 * delete affordance on this screen, and deactivating is an ordinary labelled button on the row
 * rather than something hidden inside an edit form.
 */
@Component({
  selector: 'cb-school-classes',
  imports: [ReactiveFormsModule, NgTemplateOutlet, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './school-classes.html',
  styleUrl: './school-classes.scss',
})
export class SchoolClasses {
  private readonly api = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /**
   * One control for all four editors.
   *
   * There is no client-side length rule on a class or a section name, deliberately: the contract
   * states one for a session name and not for these, and guessing a limit the server does not have
   * would refuse a name a school is entitled to use. The server's own message arrives in
   * `error.details.name` and is shown under the field.
   */
  protected readonly form = this.formBuilder.group({
    name: ['', Validators.required],
  });

  /**
   * Whether to offer any of the ladder's write controls — add, rename, reorder, stop running.
   *
   * One check for all of them because the backend gates every write on this module's single
   * `academics:class:manage`; splitting reorder from rename here would invent a distinction the
   * server does not make.
   */
  protected readonly canManageClasses = permitted(Permissions.CLASS_MANAGE);

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly loadFailureCode = signal<string | null>(null);
  protected readonly classes = signal<readonly SchoolClass[]>([]);

  protected readonly editor = signal<Editor>(null);
  protected readonly saving = signal(false);
  protected readonly writeFailureCode = signal<string | null>(null);
  private readonly attempted = signal(false);
  private readonly serverError = signal<string | null>(null);
  /** The name a write was refused for, so a clash can be explained against the row that holds it. */
  private readonly clashName = signal<string | null>(null);

  /** What just happened, said once, in the live region. */
  protected readonly announcement = signal('');

  /**
   * Only the newest reorder is allowed to paint. Each request carries the complete ladder, so a
   * late answer to an earlier move describes an order the user has already moved on from.
   */
  private latestReorder = 0;

  protected readonly forbidden = computed(() => this.loadFailureCode() === ACCESS_DENIED);
  protected readonly loadFailed = computed(
    () => this.loadFailureCode() !== null && this.loadFailureCode() !== ACCESS_DENIED,
  );

  protected readonly view = computed<readonly ClassRow[]>(() => {
    const ladder = this.classes();
    return ladder.map((schoolClass, index) => ({
      id: schoolClass.id,
      name: schoolClass.name,
      active: schoolClass.active,
      position: index + 1,
      isFirst: index === 0,
      isLast: index === ladder.length - 1,
      sections: schoolClass.sections.map((section) => ({
        id: section.id,
        name: section.name,
        active: section.active,
        renameButtonId: `section-rename-${section.id}`,
        activeButtonId: `section-active-${section.id}`,
        qualifiedName: `${section.name} of ${schoolClass.name}`,
      })),
      activeSectionCount: schoolClass.sections.filter((section) => section.active).length,
      moveUpId: `class-up-${schoolClass.id}`,
      moveDownId: `class-down-${schoolClass.id}`,
      renameButtonId: `class-rename-${schoolClass.id}`,
      activeButtonId: `class-active-${schoolClass.id}`,
      addSectionButtonId: `class-add-section-${schoolClass.id}`,
    }));
  });

  protected readonly addingClass = computed(() => this.editor()?.kind === 'new-class');

  protected readonly nameError = computed(() => {
    this.formValue();
    this.attempted();
    const fromServer = this.serverError();
    if (fromServer) {
      return fromServer;
    }
    const control = this.form.controls.name;
    if ((control.touched || this.attempted()) && control.hasError('required')) {
      const editor = this.editor();
      return editor?.kind === 'new-section' || editor?.kind === 'section'
        ? 'Give the section a name, for example A.'
        : 'Give the class a name, for example Class 5.';
    }
    return null;
  });

  /** What the name field is called. The heading above it says which of the four actions is open. */
  protected readonly editorLabel = computed(() => {
    const kind = this.editor()?.kind;
    return kind === 'new-section' || kind === 'section' ? 'Section name' : 'Class name';
  });

  /** The heading over the open editor, so the form says which of four things it is doing. */
  protected readonly editorHeading = computed(() => {
    switch (this.editor()?.kind) {
      case 'new-class':
        return 'Add a class';
      case 'class':
        return 'Rename this class';
      case 'new-section':
        return 'Add a section';
      case 'section':
        return 'Rename this section';
      default:
        return '';
    }
  });

  /**
   * A duplicate name, said against the row that already holds it.
   *
   * Worth the trouble because the confusing case is the common one: a class keeps its name after
   * it stops running, so "Class 5 already exists" is baffling until someone notices Class 5 is
   * still there with the light off. Naming its position turns a dead end into an instruction.
   */
  private readonly nameClash = computed(() => {
    const wanted = (this.clashName() ?? '').trim().toLowerCase();
    const existing = this.view().find((row) => row.name.trim().toLowerCase() === wanted);

    if (existing && !existing.active) {
      return {
        title: `${existing.name} is already in the ladder, switched off`,
        detail:
          `It is at position ${existing.position} and kept its name when it stopped running. ` +
          'Start it running again rather than adding a second one.',
      };
    }
    if (existing) {
      return {
        title: `${existing.name} is already in the ladder`,
        detail: `It is at position ${existing.position}, and two rungs cannot share a name.`,
      };
    }
    return {
      title: 'That name is already taken',
      detail:
        'Something here already has it — possibly a row that has stopped running, because ' +
        'switching one off does not free its name.',
    };
  });

  protected readonly writeFailure = computed(() => {
    if (this.writeFailureCode() === DUPLICATE_NAME) {
      return this.nameClash();
    }
    if (this.writeFailureCode() === DUPLICATE_SECTION_NAME) {
      // Deliberately not routed through `nameClash`, which searches the ladder: a section name only
      // has to be unique inside its own class, so "A" existing under another class is irrelevant
      // and pointing at it would send the user to the wrong row.
      return {
        title: 'This class already has a section with that name',
        detail:
          'Section names only have to be unique within their own class, so another class may ' +
          'still have one. Check the sections listed under this class, including any that have ' +
          'stopped running — those keep their names too.',
      };
    }
    switch (this.writeFailureCode()) {
      case null:
        return null;
      case 'VAL_001':
        return {
          title: 'That name was refused',
          detail: 'The message under the field says why.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change classes and sections',
          detail: 'Ask your principal to add "Manage classes" to your role.',
        };
      default:
        return {
          title: 'Could not save that change',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  /** Tracks the form so `nameError` recomputes; the value itself comes off the control. */
  private readonly formValue = signal(0);

  constructor() {
    this.load();

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formValue.update((count) => count + 1);
      if (this.serverError() !== null) {
        this.serverError.set(null);
      }
    });
  }

  protected reload(): void {
    this.load();
  }

  /** Whether the editor open right now belongs to this class row, or to a section inside it. */
  protected editingClass(id: string): boolean {
    const editor = this.editor();
    return editor?.kind === 'class' && editor.id === id;
  }

  protected addingSectionTo(classId: string): boolean {
    const editor = this.editor();
    return editor?.kind === 'new-section' && editor.classId === classId;
  }

  protected editingSection(id: string): boolean {
    const editor = this.editor();
    return editor?.kind === 'section' && editor.id === id;
  }

  // ── Editors ──────────────────────────────────────────────────────────────────────────────

  protected startAddClass(): void {
    this.openEditor({ kind: 'new-class' }, '');
  }

  protected startRenameClass(row: ClassRow): void {
    this.openEditor({ kind: 'class', id: row.id }, row.name);
  }

  protected startAddSection(row: ClassRow): void {
    this.openEditor({ kind: 'new-section', classId: row.id }, '');
  }

  protected startRenameSection(row: ClassRow, section: SectionRow): void {
    this.openEditor({ kind: 'section', id: section.id, classId: row.id }, section.name);
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    const editor = this.editor();
    this.closeEditor();
    // Back to whatever opened it, so a keyboard user is not returned to the top of the document
    // with no idea where the row they were on went.
    this.focusAfterRender(triggerOf(editor));
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    const editor = this.editor();
    if (!editor) {
      return;
    }

    this.attempted.set(true);
    this.form.controls.name.markAsTouched();
    if (this.form.invalid) {
      this.focusAfterRender('#class-name');
      return;
    }

    const name = this.form.getRawValue().name.trim();
    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.announcement.set('');
    this.form.disable({ emitEvent: false });

    const call = (() => {
      switch (editor.kind) {
        case 'new-class':
          return this.api.createClass({ name });
        case 'class':
          return this.api.updateClass(editor.id, {
            name,
            // Renaming must not switch a class off or on as a side effect, so its current state
            // goes back with the new name.
            active: this.classById(editor.id)?.active ?? true,
          });
        case 'new-section':
          return this.api.createSection(editor.classId, { name });
        case 'section':
          return this.api.updateSection(editor.id, {
            name,
            active: this.sectionById(editor.classId, editor.id)?.active ?? true,
          });
      }
    })();

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.finishSaving();
        const wasEditor = editor;
        this.closeEditor();
        this.announcement.set(savedMessage(wasEditor, name));
        this.refresh(() => this.focusAfterRender(landingOf(wasEditor)));
      },
      error: (error: unknown) => {
        this.finishSaving();
        this.clashName.set(name);
        this.writeFailureCode.set(apiErrorCode(error));
        this.serverError.set(apiErrorDetails(error)['name'] ?? null);
      },
    });
  }

  // ── Active and inactive ──────────────────────────────────────────────────────────────────

  protected toggleClass(row: ClassRow): void {
    if (this.saving()) {
      return;
    }
    const next = !row.active;
    this.saving.set(true);
    this.writeFailureCode.set(null);

    this.api
      .updateClass(row.id, { name: row.name, active: next })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.announcement.set(
            next
              ? `${row.name} is running again.`
              : `${row.name} is no longer running. It stays in the ladder and can be brought back.`,
          );
          this.refresh(() => this.focusAfterRender(`#${row.activeButtonId}`));
        },
        error: (error: unknown) => this.failWrite(error, `#${row.activeButtonId}`),
      });
  }

  protected toggleSection(row: ClassRow, section: SectionRow): void {
    if (this.saving()) {
      return;
    }
    const next = !section.active;
    this.saving.set(true);
    this.writeFailureCode.set(null);

    this.api
      .updateSection(section.id, { name: section.name, active: next })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.announcement.set(
            next
              ? `${section.qualifiedName} is running again.`
              : `${section.qualifiedName} is no longer running. It can be brought back.`,
          );
          this.refresh(() => this.focusAfterRender(`#${section.activeButtonId}`));
        },
        error: (error: unknown) => this.failWrite(error, `#${section.activeButtonId}`),
      });
  }

  // ── Reordering ───────────────────────────────────────────────────────────────────────────

  protected moveUp(row: ClassRow): void {
    this.move(row, -1);
  }

  protected moveDown(row: ClassRow): void {
    this.move(row, 1);
  }

  private move(row: ClassRow, delta: -1 | 1): void {
    const before = this.classes();
    const from = before.findIndex((candidate) => candidate.id === row.id);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= before.length) {
      return;
    }

    const next = [...before];
    [next[from], next[to]] = [next[to], next[from]];

    // Optimistic: the row moves under the finger that moved it, and the server's answer replaces
    // this a moment later.
    this.classes.set(next);
    this.writeFailureCode.set(null);
    this.announcement.set(
      `${row.name} moved ${delta < 0 ? 'up' : 'down'} to position ${to + 1} of ${next.length}.`,
    );

    const request = ++this.latestReorder;

    this.api
      // Every id, in the new order — the inactive ones included. A partial list is refused, and
      // that refusal is the server protecting the ladder from a client that filtered it.
      .reorderClasses(next.map((schoolClass) => schoolClass.id))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ladder) => {
          if (request !== this.latestReorder) {
            return;
          }
          // What the server says the order is, not what this screen guessed it would be.
          this.classes.set(ladder);
          this.focusMove(row.id, delta);
        },
        error: (error: unknown) => {
          if (request !== this.latestReorder) {
            return;
          }
          this.classes.set(before);
          this.writeFailureCode.set(apiErrorCode(error));
          this.announcement.set(`${row.name} could not be moved. The ladder is as it was.`);
          this.focusMove(row.id, delta);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    this.loading.set(true);
    this.loadFailureCode.set(null);

    this.api
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ladder) => {
          this.classes.set(ladder);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.classes.set([]);
          this.loading.set(false);
          this.loadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  /**
   * Re-read the ladder after a write, without blanking the screen.
   *
   * Always a refetch rather than a local splice, and the reason is section ordering: sections come
   * back ordered by name, so renaming "C" to "A" moves it, and a screen that patched the row in
   * place would show the school an order the server does not have. A failure here is deliberately
   * left alone — the write succeeded, so the rows are stale rather than wrong, and turning a
   * successful save into an error banner would be a lie.
   */
  private refresh(then?: () => void): void {
    this.api
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ladder) => {
          this.classes.set(ladder);
          then?.();
        },
        error: () => then?.(),
      });
  }

  private openEditor(editor: NonNullable<Editor>, name: string): void {
    this.form.reset({ name }, { emitEvent: false });
    this.form.enable({ emitEvent: false });
    this.form.markAsUntouched();
    this.attempted.set(false);
    this.serverError.set(null);
    this.clashName.set(null);
    this.writeFailureCode.set(null);
    this.announcement.set('');
    this.editor.set(editor);
    this.focusAfterRender('#class-name');
  }

  private closeEditor(): void {
    this.editor.set(null);
    this.attempted.set(false);
    this.serverError.set(null);
    this.form.reset({ name: '' }, { emitEvent: false });
  }

  private finishSaving(): void {
    this.saving.set(false);
    this.form.enable({ emitEvent: false });
  }

  private failWrite(error: unknown, focus: string): void {
    this.saving.set(false);
    this.writeFailureCode.set(apiErrorCode(error));
    this.focusAfterRender(focus);
  }

  private classById(id: string): SchoolClass | undefined {
    return this.classes().find((candidate) => candidate.id === id);
  }

  private sectionById(classId: string, id: string): Section | undefined {
    return this.classById(classId)?.sections.find((candidate) => candidate.id === id);
  }

  /**
   * Focus the move button that was pressed — or its opposite, when the row has just reached an end
   * of the ladder and the button that got it there is now disabled. A keyboard user who moves a
   * class to the top must not be dropped back at the start of the document for it.
   */
  private focusMove(id: string, delta: -1 | 1): void {
    afterNextRender(
      () => {
        const root = this.host.nativeElement;
        const pressed = root.querySelector<HTMLButtonElement>(
          delta < 0 ? `#class-up-${id}` : `#class-down-${id}`,
        );
        const opposite = root.querySelector<HTMLButtonElement>(
          delta < 0 ? `#class-down-${id}` : `#class-up-${id}`,
        );
        (pressed && !pressed.disabled ? pressed : opposite)?.focus();
      },
      { injector: this.injector },
    );
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

/** The control that opened an editor, so Cancel can hand focus back to it. */
function triggerOf(editor: Editor): string {
  switch (editor?.kind) {
    case 'class':
      return `#class-rename-${editor.id}`;
    case 'new-section':
      return `#class-add-section-${editor.classId}`;
    case 'section':
      return `#section-rename-${editor.id}`;
    default:
      return '#class-add';
  }
}

/**
 * Where focus goes after a save. Not always the trigger: a newly created class or section has no
 * row of its own to return to yet, so focus goes back to the control that would create another —
 * which is also where someone adding three sections in a row wants to be.
 */
function landingOf(editor: NonNullable<Editor>): string {
  switch (editor.kind) {
    case 'new-class':
      return '#class-add';
    case 'class':
      return `#class-rename-${editor.id}`;
    case 'new-section':
      return `#class-add-section-${editor.classId}`;
    case 'section':
      return `#section-rename-${editor.id}`;
  }
}

function savedMessage(editor: NonNullable<Editor>, name: string): string {
  switch (editor.kind) {
    case 'new-class':
      return `${name} added at the end of the ladder. Move it into place with the arrows.`;
    case 'class':
      return `Class renamed to ${name}.`;
    case 'new-section':
      return `Section ${name} added.`;
    case 'section':
      return `Section renamed to ${name}.`;
  }
}
