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
import { apiErrorCode } from '../../core/api/api-error';
import { MedicalDetail, MedicalSummary } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED } from './students-shared';

/** Which control each message belongs under. */
const FIELDS = [
  'bloodGroup',
  'cwsnStatus',
  'disabilityDetails',
  'allergies',
  'chronicConditions',
  'medication',
  'emergencyContactName',
  'emergencyContactPhone',
  'emergencyContactRelation',
] as const;

type Field = (typeof FIELDS)[number];

/** The backend's own limits, restated so a value is refused before the round trip rather than after. */
const MAX_LENGTH_MESSAGE: Readonly<Record<Field, string>> = {
  bloodGroup: 'A blood group is 40 characters or fewer.',
  cwsnStatus: 'A CWSN / disability status is 100 characters or fewer.',
  disabilityDetails: 'Disability details are 2000 characters or fewer.',
  allergies: 'A list of allergies is 2000 characters or fewer.',
  chronicConditions: 'Chronic conditions are 2000 characters or fewer.',
  medication: 'A list of medication is 2000 characters or fewer.',
  emergencyContactName: 'A name is 200 characters or fewer.',
  emergencyContactPhone: 'A phone number is 20 characters or fewer.',
  emergencyContactRelation: 'A relation is 60 characters or fewer.',
};

/**
 * A student's health record (FR-034): CWSN/disability, allergies, chronic conditions, medication,
 * blood group, and an emergency contact.
 *
 * ## Masked by default, revealed by an explicit action (ADR-0014, ADR-0022)
 *
 * `medical` (an input) is always the **masked** shape — `MedicalSummary`, whose six health fields
 * are `hasX: boolean`, never the value. Nothing on this screen ever holds a real blood group or
 * disability status until somebody who holds "Reveal restricted student data" presses **Reveal**,
 * which calls `GET …/medical/restricted` and is recorded on the server as a read of Restricted
 * data. Editing goes through the same door: the edit form needs the real values to pre-fill
 * without silently blanking whichever one nobody retyped, so **Edit** reveals first if nothing has
 * been revealed yet.
 *
 * `revealed` is a component-local signal and nothing more. It is not persisted, not cached beyond
 * this component instance, and is dropped the moment the record reloads (a fresh `medical` input)
 * or the component is destroyed — leaving it on screen for the rest of a long session is the
 * masking working as intended, not a bug to fix by remembering less.
 */
@Component({
  selector: 'cb-student-medical',
  imports: [ReactiveFormsModule, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-medical.html',
  styleUrl: './student-medical.scss',
})
export class StudentMedical {
  readonly studentId = input.required<string>();
  readonly medical = input<MedicalSummary | null>(null);

  readonly changed = output<string>();

  private readonly students = inject(StudentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly canManage = permitted(Permissions.STUDENT_MANAGE);
  protected readonly canReveal = permitted(Permissions.STUDENT_REVEAL_RESTRICTED);

  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly revealing = signal(false);
  protected readonly failureCode = signal<string | null>(null);
  /** The real values, once revealed. Cleared whenever the record reloads. */
  protected readonly revealed = signal<MedicalDetail | null>(null);

  protected readonly form = this.formBuilder.group({
    bloodGroup: ['', Validators.maxLength(40)],
    cwsnStatus: ['', Validators.maxLength(100)],
    disabilityDetails: ['', Validators.maxLength(2000)],
    allergies: ['', Validators.maxLength(2000)],
    chronicConditions: ['', Validators.maxLength(2000)],
    medication: ['', Validators.maxLength(2000)],
    emergencyContactName: ['', Validators.maxLength(200)],
    emergencyContactPhone: ['', Validators.maxLength(20)],
    emergencyContactRelation: ['', Validators.maxLength(60)],
  });

  /** True once Save has been pressed: before that, only touched fields show a message. */
  private readonly attempted = signal(false);
  /** Bumped on every change so the messages recompute; values come off the controls. */
  private readonly revision = signal(0);

  protected readonly fieldErrors = computed<Readonly<Record<Field, string | null>>>(() => {
    this.revision();
    this.attempted();
    const errors: Record<string, string | null> = {};
    for (const field of FIELDS) {
      errors[field] = this.messageFor(field);
    }
    return errors as Readonly<Record<Field, string | null>>;
  });

  protected readonly summary = computed(() => this.medical() ?? EMPTY_MEDICAL_SUMMARY);

  protected readonly hasAnyRestricted = computed(() => {
    const s = this.summary();
    return (
      s.hasBloodGroup ||
      s.hasCwsnStatus ||
      s.hasDisabilityDetails ||
      s.hasAllergies ||
      s.hasChronicConditions ||
      s.hasMedication
    );
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to see or change this section',
          detail:
            'Revealing it needs "Reveal restricted student data"; changing it needs ' +
            '"Manage students". Ask your principal.',
        };
      default:
        return {
          title: 'Something went wrong with the health record',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
    });
  }

  /** Whether a caller may see the real value of a given field: revealed, and it was actually recorded. */
  protected valueFor(has: boolean, field: keyof MedicalDetail): string | null {
    if (!has) {
      return null;
    }
    const revealed = this.revealed();
    return revealed ? revealed[field]?.trim() || null : null;
  }

  protected reveal(): void {
    if (this.revealing() || this.revealed()) {
      return;
    }
    this.revealing.set(true);
    this.failureCode.set(null);
    this.students
      .revealMedical(this.studentId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.revealing.set(false);
          this.revealed.set(detail);
        },
        error: (error: unknown) => {
          this.revealing.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  protected startEdit(): void {
    if (this.hasAnyRestricted() && !this.revealed()) {
      // The form has to be pre-filled with the real values, or saving it would silently blank
      // whichever field nobody retyped — see the class comment.
      this.revealing.set(true);
      this.failureCode.set(null);
      this.students
        .revealMedical(this.studentId())
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (detail) => {
            this.revealing.set(false);
            this.revealed.set(detail);
            this.openEditor(detail);
          },
          error: (error: unknown) => {
            this.revealing.set(false);
            this.failureCode.set(apiErrorCode(error));
          },
        });
      return;
    }
    this.openEditor(this.revealed());
  }

  private openEditor(detail: MedicalDetail | null): void {
    this.attempted.set(false);
    const summary = this.summary();
    this.form.reset(
      {
        bloodGroup: detail?.bloodGroup ?? '',
        cwsnStatus: detail?.cwsnStatus ?? '',
        disabilityDetails: detail?.disabilityDetails ?? '',
        allergies: detail?.allergies ?? '',
        chronicConditions: detail?.chronicConditions ?? '',
        medication: detail?.medication ?? '',
        emergencyContactName: summary.emergencyContactName ?? '',
        emergencyContactPhone: summary.emergencyContactPhone ?? '',
        emergencyContactRelation: summary.emergencyContactRelation ?? '',
      },
      { emitEvent: false },
    );
    this.editing.set(true);
    this.focusAfterRender('#medical-blood-group');
  }

  protected cancel(): void {
    if (this.busy()) {
      return;
    }
    this.editing.set(false);
    this.focusAfterRender('#medical-edit');
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.failureCode.set(null);
    const v = this.form.getRawValue();

    this.students
      .saveMedical(this.studentId(), {
        bloodGroup: v.bloodGroup.trim() || undefined,
        cwsnStatus: v.cwsnStatus.trim() || undefined,
        disabilityDetails: v.disabilityDetails.trim() || undefined,
        allergies: v.allergies.trim() || undefined,
        chronicConditions: v.chronicConditions.trim() || undefined,
        medication: v.medication.trim() || undefined,
        emergencyContactName: v.emergencyContactName.trim() || undefined,
        emergencyContactPhone: v.emergencyContactPhone.trim() || undefined,
        emergencyContactRelation: v.emergencyContactRelation.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editing.set(false);
          // The values just written are exactly what was typed, so keep them revealed rather than
          // forcing a second audited read to show what was just shown a moment ago.
          this.revealed.set({
            bloodGroup: v.bloodGroup.trim() || undefined,
            cwsnStatus: v.cwsnStatus.trim() || undefined,
            disabilityDetails: v.disabilityDetails.trim() || undefined,
            allergies: v.allergies.trim() || undefined,
            chronicConditions: v.chronicConditions.trim() || undefined,
            medication: v.medication.trim() || undefined,
          });
          this.changed.emit('The health record was saved.');
          this.focusAfterRender('#medical-edit');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }

  private messageFor(field: Field): string | null {
    const control = this.form.controls[field];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (control.hasError('maxlength')) {
      return MAX_LENGTH_MESSAGE[field];
    }
    return null;
  }
}

const EMPTY_MEDICAL_SUMMARY: MedicalSummary = {
  hasBloodGroup: false,
  hasCwsnStatus: false,
  hasDisabilityDetails: false,
  hasAllergies: false,
  hasChronicConditions: false,
  hasMedication: false,
};
