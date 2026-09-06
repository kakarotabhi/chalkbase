import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
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
import { SaveStudentRequest, StudentDetail } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { today } from '../../shared/formatting/day';
import {
  ACCESS_DENIED,
  CONFLICT,
  DUPLICATE_ADMISSION_NUMBER,
  GENDER_OPTIONS,
  STATUS_OPTIONS,
} from './students-shared';

/** The backend's own limits, restated so a value is refused before the round trip rather than after. */
const ADMISSION_NUMBER_MAX_LENGTH = 40;
const FULL_NAME_MAX_LENGTH = 200;

/** Which control each message belongs under, and which one to focus first when several are wrong. */
const FIELDS = [
  'admissionNumber',
  'fullName',
  'dateOfBirth',
  'gender',
  'status',
  'admittedOn',
] as const;

type Field = (typeof FIELDS)[number];

const REQUIRED: Readonly<Record<Field, string>> = {
  admissionNumber: 'Give the student an admission number.',
  fullName: "Enter the student's name as it appears on their records.",
  dateOfBirth: 'Choose the date of birth.',
  gender: 'Choose one.',
  status: 'Choose a status.',
  // Never reached: `admittedOn` has no required validator, because the column is nullable and a
  // school that does not know the date should not be blocked from recording the child. The entry
  // exists so this map covers every field and nobody has to remember which ones it does not.
  admittedOn: 'Choose the day this student was admitted.',
};

/** The id each control carries, so a message, a label and a focus target cannot disagree. */
const CONTROL_ID: Readonly<Record<Field, string>> = {
  admissionNumber: 'student-admission-number',
  fullName: 'student-full-name',
  dateOfBirth: 'student-date-of-birth',
  gender: 'student-gender',
  status: 'student-status',
  admittedOn: 'student-admitted-on',
};

/**
 * The student record itself — the six fields `POST /api/students` and `PUT /api/students/{id}` take.
 *
 * One component used by both screens that write a student: adding one from the list, and editing
 * one on their own page. The alternative was the same six fields, the same six validators and the
 * same six messages written twice, and the failure mode of that is not duplication but drift — the
 * add form allowing a name the edit form refuses, discovered by a clerk who cannot save a
 * correction to a record they created yesterday.
 *
 * It owns the form and nothing else: no HTTP, no navigation. The parent hands it a student to edit
 * (or null to add one), takes `saved` and does the writing, and hands back `saving`, the refusal
 * code and any per-field reasons the server gave.
 *
 * ## One name field
 *
 * There is no "First name" and no "Last name", and that is a decision rather than an omission
 * (ADR-0020 §1): a great many Indian students have no surname, and a required last-name box makes
 * an office clerk invent one that then goes on a certificate. This form asks for the name as it
 * appears on the records the school will be held to.
 *
 * ## Everything typed here is Confidential (ADR-0014)
 *
 * A name, a date of birth, an admission number. Nothing in this component may log a value, and the
 * parent must not put one in a URL.
 */
@Component({
  selector: 'cb-student-form',
  imports: [ReactiveFormsModule, Button, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-form.html',
  styleUrl: './student-form.scss',
})
export class StudentForm {
  /** The student being edited, or null to add one. */
  readonly student = input<StudentDetail | null>(null);
  readonly heading = input('Add a student');
  readonly submitLabel = input('Save student');
  readonly saving = input(false);
  /** The `error.code` of the last refused save, or null. Never the message (ADR-0007). */
  readonly failureCode = input<string | null>(null);
  /** Field name to reason, as the server sent them (`error.details`). */
  readonly serverErrors = input<Readonly<Record<string, string>>>({});

  readonly saved = output<SaveStudentRequest>();
  readonly cancelled = output<void>();

  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly genderOptions = GENDER_OPTIONS;
  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly controlId = CONTROL_ID;

  protected readonly form = this.formBuilder.group({
    admissionNumber: ['', [Validators.required, Validators.maxLength(ADMISSION_NUMBER_MAX_LENGTH)]],
    fullName: ['', [Validators.required, Validators.maxLength(FULL_NAME_MAX_LENGTH)]],
    dateOfBirth: ['', [Validators.required, inThePast]],
    // A new student is active. Every other status is something that happened later, and none of
    // them is a sensible thing to admit somebody as.
    gender: ['', Validators.required],
    status: ['ACTIVE', Validators.required],
    // Not required: the column is nullable, and a record whose admission date nobody wrote down is
    // still a record the school has to be able to keep.
    admittedOn: '',
  });

  /** True once Save has been pressed: before that, only touched fields show a message. */
  private readonly attempted = signal(false);
  /** Bumped on every change so the messages recompute; values come off the controls. */
  private readonly revision = signal(0);
  /** Cleared as soon as the user edits, so a server message never outlives the value it was about. */
  private readonly dismissedServerErrors = signal(false);

  protected readonly failure = computed(() => {
    const code = this.failureCode();
    if (code === null) {
      return null;
    }
    if (Object.keys(this.serverErrors()).length > 0 && !this.dismissedServerErrors()) {
      // A refusal that named fields is a validation failure whatever code it travelled under.
      return {
        title: 'Some of these details were refused',
        detail: 'The fields marked below need correcting.',
      };
    }
    switch (code) {
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case DUPLICATE_ADMISSION_NUMBER:
        return {
          title: 'That admission number is already in use',
          detail:
            'Admission numbers are unique within this school. Search for it on the students list ' +
            'to see which record already has it.',
        };
      case CONFLICT:
        return {
          title: 'These details clash with a record already at this school',
          detail:
            'An admission number is used once per school. Check whether this student is already ' +
            'on the list before adding them again.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change student records',
          detail: 'Ask your principal to add "Manage students" to your role.',
        };
      default:
        return {
          title: 'Could not save this student',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  protected readonly fieldErrors = computed<Readonly<Record<Field, string | null>>>(() => {
    this.revision();
    this.attempted();
    this.serverErrors();
    this.dismissedServerErrors();
    return {
      admissionNumber: this.messageFor('admissionNumber'),
      fullName: this.messageFor('fullName'),
      dateOfBirth: this.messageFor('dateOfBirth'),
      gender: this.messageFor('gender'),
      status: this.messageFor('status'),
      admittedOn: this.messageFor('admittedOn'),
    };
  });

  constructor() {
    // The record arrives after the component does — the detail screen renders the form as soon as
    // the user asks to edit, and the student is already loaded, but an input is still an input.
    effect(() => {
      const student = this.student();
      this.form.reset(
        {
          admissionNumber: student?.admissionNumber ?? '',
          fullName: student?.fullName ?? '',
          dateOfBirth: student?.dateOfBirth ?? '',
          gender: student?.gender ?? '',
          status: student?.status ?? 'ACTIVE',
          admittedOn: student?.admittedOn ?? '',
        },
        { emitEvent: false },
      );
      this.attempted.set(false);
      this.dismissedServerErrors.set(false);
      this.revision.update((count) => count + 1);
    });

    // The whole group goes read-only while the request is in flight, and comes back afterwards.
    effect(() => {
      if (this.saving()) {
        this.form.disable({ emitEvent: false });
      } else {
        this.form.enable({ emitEvent: false });
      }
    });

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
      if (!this.dismissedServerErrors() && Object.keys(this.serverErrors()).length > 0) {
        this.dismissedServerErrors.set(true);
      }
    });

    // The editor appearing is a request to type in it. Without this a keyboard user has to tab
    // from wherever the page happened to leave focus, past everything above the form.
    afterNextRender(
      () =>
        this.host.nativeElement
          .querySelector<HTMLElement>(`#${CONTROL_ID.admissionNumber}`)
          ?.focus(),
      { injector: this.injector },
    );
  }

  protected submit(): void {
    if (this.saving()) {
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
    const request: SaveStudentRequest = {
      admissionNumber: value.admissionNumber.trim(),
      fullName: value.fullName.trim(),
      dateOfBirth: value.dateOfBirth,
      gender: value.gender as SaveStudentRequest['gender'],
      status: value.status as SaveStudentRequest['status'],
    };
    // Left out rather than sent as an empty string: the field is nullable, and `""` is a value —
    // it would overwrite a date somebody had recorded with a blank rather than leaving it alone.
    this.saved.emit(value.admittedOn ? { ...request, admittedOn: value.admittedOn } : request);
  }

  protected cancel(): void {
    if (!this.saving()) {
      this.cancelled.emit();
    }
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private messageFor(name: Field): string | null {
    if (!this.dismissedServerErrors()) {
      const fromServer = this.serverErrors()[name];
      if (fromServer) {
        return fromServer;
      }
    }
    const control = this.form.controls[name];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return REQUIRED[name];
    }
    if (control.hasError('maxlength')) {
      return name === 'admissionNumber'
        ? `An admission number is ${ADMISSION_NUMBER_MAX_LENGTH} characters or fewer.`
        : `A name is ${FULL_NAME_MAX_LENGTH} characters or fewer.`;
    }
    if (control.hasError('notInThePast')) {
      return 'A date of birth has to be in the past.';
    }
    return null;
  }

  private focusFirstInvalid(): void {
    const errors = this.fieldErrors();
    const first = FIELDS.find((name) => errors[name] !== null) ?? FIELDS[0];
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>(`#${CONTROL_ID[first]}`)?.focus(),
      { injector: this.injector },
    );
  }
}

/**
 * The same rule the backend enforces: a date of birth is in the past.
 *
 * Compared as `yyyy-MM-dd` strings, which is the one thing that format is good for, and against a
 * locally computed "today" — converting either side to a `Date` reads a bare date as UTC and can
 * move it a day, which here would mean refusing a birthday that is in fact in the past.
 */
function inThePast(control: AbstractControl): ValidationErrors | null {
  const value = control.value as string;
  if (!value) {
    return null;
  }
  return value < today() ? null : { notInThePast: true };
}
