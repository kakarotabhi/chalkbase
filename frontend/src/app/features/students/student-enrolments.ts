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
import { Observable, forkJoin } from 'rxjs';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { AcademicSession, Enrolment, SchoolClass } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { formatDay } from '../../shared/formatting/day';
import {
  ACCESS_DENIED,
  ALREADY_ENROLLED_THIS_SESSION,
  CONFLICT,
  ROLL_NUMBER_TAKEN,
  classAndSection,
} from './students-shared';

/** One row of the history, with everything the template needs already decided. */
interface EnrolmentRow {
  readonly id: string;
  readonly sessionId: string;
  readonly sessionName: string;
  readonly classId: string;
  readonly sectionId: string;
  readonly placement: string;
  readonly rollNumber: string | null;
  readonly active: boolean;
  readonly enrolledOn: string;
  readonly editButtonId: string;
}

/**
 * A student's enrolments: where they sit this year, and where they have sat before.
 *
 * ## History, not a current value
 *
 * An enrolment is its own record and it is what carries the academic session (ADR-0020 §4), so
 * promotion is a **new row** rather than an edit — next year's enrolment does not overwrite this
 * year's, and a student's history is readable without an audit log. This screen shows all of them,
 * newest first, and adding one never touches an existing one.
 *
 * ## The section is chosen through its class, and the request only carries the section
 *
 * A section belongs to exactly one class (ADR-0019), so `sectionId` is the whole of the placement.
 * The class picker above it exists only to make the section list short enough to read: a school
 * with twelve classes and four sections each is forty-eight options in one dropdown on a phone.
 * Nothing about the class is sent.
 *
 * ## Roll numbers are optional and usually absent at first
 *
 * They are assigned after admission and often after the class list settles (ADR-0020 §4), so the
 * field is optional and an empty one is sent as null rather than as an empty string.
 */
@Component({
  selector: 'cb-student-enrolments',
  imports: [ReactiveFormsModule, Button, Checkbox, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-enrolments.html',
  styleUrl: './student-enrolments.scss',
})
export class StudentEnrolments {
  readonly studentId = input.required<string>();
  readonly enrolments = input.required<readonly Enrolment[]>();

  /** Something was written. The parent re-reads the student and says so in its live region. */
  readonly changed = output<string>();

  private readonly students = inject(StudentsApi);
  private readonly academics = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /**
   * Whether this user may place a child in a class or correct a placement.
   *
   * The same permission as editing the student, deliberately: the backend gates
   * `POST/PUT …/enrolments` on `student:student:manage` because a role that may admit a child but
   * not enrol them cannot finish its own job.
   */
  protected readonly canManageStudents = permitted(Permissions.STUDENT_MANAGE);

  protected readonly form = this.formBuilder.group({
    academicSessionId: ['', Validators.required],
    // A picker, not a field: it narrows the section list and is never sent.
    classId: ['', Validators.required],
    sectionId: ['', Validators.required],
    rollNumber: '',
    active: true,
  });

  protected readonly sessions = signal<readonly AcademicSession[]>([]);
  protected readonly ladder = signal<readonly SchoolClass[]>([]);
  protected readonly structureLoading = signal(true);
  protected readonly structureFailureCode = signal<string | null>(null);

  /** null when closed, 'new' when adding, or the id of the enrolment being corrected. */
  protected readonly editing = signal<'new' | string | null>(null);
  protected readonly busy = signal(false);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly fieldErrors = signal<Readonly<Record<string, string>>>({});
  private readonly attempted = signal(false);
  private readonly revision = signal(0);

  protected readonly adding = computed(() => this.editing() === 'new');
  protected readonly editingId = computed(() => {
    const editing = this.editing();
    return editing === 'new' ? null : editing;
  });

  protected readonly rows = computed<readonly EnrolmentRow[]>(() =>
    // Newest first. The API does not state an order, and "which year is this" is read top-down.
    [...this.enrolments()]
      .sort((a, b) => b.enrolledOn.localeCompare(a.enrolledOn))
      .map((enrolment) => ({
        id: enrolment.id,
        sessionId: enrolment.sessionId,
        // Blank rather than absent when `academics` could not resolve the name: the row type
        // stays a plain string, the edit form can still be patched from it, and the cell renders
        // empty exactly as `placement` already does. See `Enrolment` in models.ts.
        sessionName: enrolment.sessionName ?? '',
        classId: enrolment.classId ?? '',
        sectionId: enrolment.sectionId,
        placement: classAndSection(enrolment.className, enrolment.sectionName),
        rollNumber: enrolment.rollNumber?.trim() || null,
        active: enrolment.active,
        enrolledOn: formatDay(enrolment.enrolledOn),
        editButtonId: `enrolment-edit-${enrolment.id}`,
      })),
  );

  protected readonly sessionOptions = computed<readonly SelectOption[]>(() =>
    this.sessions().map((session) => ({
      value: session.id,
      // Which year the school is on now is the single most consequential thing about a session
      // (ADR-0019), and it decides which one this dropdown should have been left on.
      label: session.current ? `${session.name} (current)` : session.name,
    })),
  );

  protected readonly classOptions = computed<readonly SelectOption[]>(() =>
    this.ladder().map((schoolClass) => ({
      value: schoolClass.id,
      label: schoolClass.active ? schoolClass.name : `${schoolClass.name} · not running`,
    })),
  );

  /** Only the chosen class's sections: forty-eight options in one dropdown is not a choice. */
  protected readonly sectionOptions = computed<readonly SelectOption[]>(() => {
    this.revision();
    const classId = this.form.controls.classId.value;
    const schoolClass = this.ladder().find((candidate) => candidate.id === classId);
    return (schoolClass?.sections ?? []).map((section) => ({
      value: section.id,
      label: section.active ? section.name : `${section.name} · not running`,
    }));
  });

  /** The session an enrolment being corrected belongs to. It is not editable — see the class doc. */
  protected readonly editingSessionName = computed(() => {
    const id = this.editingId();
    return this.rows().find((row) => row.id === id)?.sessionName ?? '';
  });

  protected readonly structureForbidden = computed(
    () => this.structureFailureCode() === ACCESS_DENIED,
  );
  protected readonly structureFailed = computed(
    () => this.structureFailureCode() !== null && this.structureFailureCode() !== ACCESS_DENIED,
  );

  protected readonly errors = computed(() => {
    this.revision();
    this.attempted();
    this.fieldErrors();
    return {
      academicSessionId: this.messageFor('academicSessionId', 'Choose the academic year.'),
      classId: this.messageFor('classId', 'Choose a class.'),
      sectionId: this.messageFor('sectionId', 'Choose a section.'),
      rollNumber: this.fieldErrors()['rollNumber'] ?? null,
    };
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case 'VAL_001':
        return {
          title: 'These details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case ALREADY_ENROLLED_THIS_SESSION:
        return {
          title: 'This student is already enrolled for that academic year',
          detail:
            'A student has one active enrolment per year. To move them to a different section, ' +
            'edit the enrolment listed below rather than adding a second.',
        };
      case ROLL_NUMBER_TAKEN:
        return {
          title: 'Another student in that section already has this roll number',
          detail:
            'Roll numbers are used once per section per year. Leave it blank to set it later.',
        };
      case CONFLICT:
        // The fallback, for a clash the module has not given a code of its own.
        return {
          title: 'This enrolment clashes with one that already exists',
          detail:
            'A student has one active enrolment per academic year, and a roll number is used once ' +
            'in a section. Check the years listed below, and the roll number.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change enrolments',
          detail: 'Ask your principal to add "Manage students" to your role.',
        };
      default:
        return {
          title: 'Could not save this enrolment',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  constructor() {
    this.loadStructure();

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
    });

    // A section belongs to one class, so a section chosen under the previous class is no longer a
    // valid answer. Cleared rather than left to be sent — the server would refuse it, but only
    // after the user had pressed Save believing they had chosen correctly.
    this.form.controls.classId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const sections = this.form.controls.sectionId;
        if (sections.value !== '') {
          sections.setValue('', { emitEvent: false });
        }
      });
  }

  protected retryStructure(): void {
    this.loadStructure();
  }

  // ── Adding ───────────────────────────────────────────────────────────────────────────────

  protected startAdd(): void {
    this.reset();
    // The current session, because that is the year everything academic is recorded against and
    // therefore the one an enrolment is nearly always for.
    const current = this.sessions().find((session) => session.current);
    this.form.reset(
      {
        academicSessionId: current?.id ?? '',
        classId: '',
        sectionId: '',
        rollNumber: '',
        active: true,
      },
      { emitEvent: false },
    );
    this.editing.set('new');
    this.focusAfterRender('#enrolment-session');
  }

  protected startEdit(row: EnrolmentRow): void {
    this.reset();
    this.form.reset(
      {
        academicSessionId: row.sessionId,
        classId: row.classId,
        sectionId: row.sectionId,
        rollNumber: row.rollNumber ?? '',
        active: row.active,
      },
      { emitEvent: false },
    );
    this.editing.set(row.id);
    this.focusAfterRender('#enrolment-class');
  }

  protected cancel(): void {
    if (this.busy()) {
      return;
    }
    const id = this.editingId();
    this.editing.set(null);
    this.focusAfterRender(id ? `#enrolment-edit-${id}` : '#enrolment-add');
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const value = this.form.getRawValue();
    // An empty roll number is omitted, not sent as "": the column is nullable because a roll
    // number is assigned later, and an empty string is a value rather than the absence of one.
    // Omitted rather than null because that is what the contract describes — the request record
    // carries no `@NotNull`, so an absent field and an explicit null both arrive as null.
    const rollNumber = value.rollNumber.trim() || undefined;
    const editingId = this.editingId();

    this.busy.set(true);
    this.failureCode.set(null);
    this.fieldErrors.set({});
    this.form.disable({ emitEvent: false });

    // Typed as the widest of the two rather than left to inference: `add` answers with the new
    // enrolment and `update` answers with nothing, and neither is read here — the parent re-reads
    // the whole record either way.
    const call: Observable<unknown> = editingId
      ? this.students.updateEnrolment(this.studentId(), editingId, {
          sectionId: value.sectionId,
          rollNumber,
          active: value.active,
        })
      : this.students.addEnrolment(this.studentId(), {
          academicSessionId: value.academicSessionId,
          sectionId: value.sectionId,
          rollNumber,
        });

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.busy.set(false);
        this.form.enable({ emitEvent: false });
        this.editing.set(null);
        this.changed.emit(editingId ? 'The enrolment was saved.' : 'The enrolment was added.');
        this.focusAfterRender(editingId ? `#enrolment-edit-${editingId}` : '#enrolment-add');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.form.enable({ emitEvent: false });
        this.failureCode.set(apiErrorCode(error));
        this.fieldErrors.set(apiErrorDetails(error));
      },
    });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  /**
   * The sessions and the ladder, in one round trip.
   *
   * `forkJoin` rather than two subscriptions: the form cannot be used until both have arrived, and
   * two independent "loading" states would let somebody choose a class from a ladder while the
   * session list was still empty.
   */
  private loadStructure(): void {
    this.structureLoading.set(true);
    this.structureFailureCode.set(null);

    forkJoin({ sessions: this.academics.sessions(), classes: this.academics.classes() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ sessions, classes }) => {
          this.sessions.set(sessions);
          this.ladder.set(classes);
          this.structureLoading.set(false);
        },
        error: (error: unknown) => {
          this.sessions.set([]);
          this.ladder.set([]);
          this.structureLoading.set(false);
          this.structureFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private reset(): void {
    this.attempted.set(false);
    this.failureCode.set(null);
    this.fieldErrors.set({});
    this.form.enable({ emitEvent: false });
    this.form.markAsUntouched();
  }

  private messageFor(
    name: 'academicSessionId' | 'classId' | 'sectionId',
    required: string,
  ): string | null {
    // `classId` is a picker rather than a field — it is never sent, so the server has nothing to
    // say about it and a message keyed on it could only have come from somewhere else.
    if (name !== 'classId') {
      const fromServer = this.fieldErrors()[name];
      if (fromServer) {
        return fromServer;
      }
    }
    const control = this.form.controls[name];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    return control.hasError('required') ? required : null;
  }

  private focusFirstInvalid(): void {
    const errors = this.errors();
    if (errors.academicSessionId) {
      this.focusAfterRender('#enrolment-session');
    } else if (errors.classId) {
      this.focusAfterRender('#enrolment-class');
    } else {
      this.focusAfterRender('#enrolment-section');
    }
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
