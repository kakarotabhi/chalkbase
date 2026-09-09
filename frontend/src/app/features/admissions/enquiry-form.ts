import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CreateEnquiryRequest,
  EnquirySource,
  SchoolClass,
  UserSummary,
} from '../../core/api/models';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { SOURCE_OPTIONS } from './admissions-shared';

/** Which control each server-side field error belongs under. */
const CONTROL_ID: Readonly<Record<string, string>> = {
  childFullName: 'enquiry-child-full-name',
  childDateOfBirth: 'enquiry-child-date-of-birth',
  interestedClassId: 'enquiry-interested-class',
  parentName: 'enquiry-parent-name',
  parentPhone: 'enquiry-parent-phone',
  parentEmail: 'enquiry-parent-email',
  source: 'enquiry-source',
  assignedCounsellorId: 'enquiry-counsellor',
  remarks: 'enquiry-remarks',
};

/**
 * Capturing one enquiry (FR-016).
 *
 * Owns the form and nothing else: no HTTP, no navigation. The parent hands it the class ladder and
 * the counsellor picker's own options, takes `saved` and does the writing, and hands back `saving`,
 * the refusal code and any per-field reasons the server gave — the same shape `StudentForm` uses.
 *
 * `assignedCounsellorId` is required here, not merely offered: an enquiry with nobody assigned to
 * it is the mailbox this feature exists to prevent (see the backend's `Enquiry` class Javadoc), so
 * the form that captures one asks who owns its follow-up in the same breath.
 *
 * Everything typed here is Confidential (ADR-0014): a prospective child's name, a parent's phone
 * number. Nothing in this component may log a value, and the parent must not put one in a URL.
 */
@Component({
  selector: 'cb-enquiry-form',
  imports: [ReactiveFormsModule, Button, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './enquiry-form.html',
  styleUrl: './enquiry-form.scss',
})
export class EnquiryForm {
  readonly classes = input<readonly SchoolClass[]>([]);
  readonly counsellors = input<readonly UserSummary[]>([]);
  readonly saving = input(false);
  /** The `error.code` of the last refused save, or null. Never the message (ADR-0007). */
  readonly failureCode = input<string | null>(null);
  /** Field name to reason, as the server sent them (`error.details`). */
  readonly serverErrors = input<Readonly<Record<string, string>>>({});

  readonly saved = output<CreateEnquiryRequest>();
  readonly cancelled = output<void>();

  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly controlId = CONTROL_ID;
  protected readonly sourceOptions = SOURCE_OPTIONS;

  /** Whether the server refused the save with no per-field reasons — a plain top-of-form banner. */
  protected readonly hasNoFieldErrors = computed(
    () => Object.keys(this.serverErrors()).length === 0,
  );

  protected readonly classOptions = computed<readonly SelectOption[]>(() => {
    const options: SelectOption[] = [{ value: '', label: 'Not yet known' }];
    for (const schoolClass of this.classes()) {
      if (!schoolClass.active) {
        continue;
      }
      options.push({ value: schoolClass.id, label: schoolClass.name });
    }
    return options;
  });

  protected readonly counsellorOptions = computed<readonly SelectOption[]>(() => {
    const options: SelectOption[] = [{ value: '', label: 'Choose a counsellor' }];
    for (const counsellor of this.counsellors()) {
      options.push({ value: counsellor.id, label: counsellor.displayName });
    }
    return options;
  });

  protected readonly form = this.formBuilder.group({
    childFullName: ['', [Validators.required, Validators.maxLength(200)]],
    childDateOfBirth: [''],
    interestedClassId: [''],
    parentName: ['', [Validators.required, Validators.maxLength(200)]],
    parentPhone: ['', [Validators.required, Validators.maxLength(20)]],
    parentEmail: ['', [Validators.email, Validators.maxLength(200)]],
    source: ['WALK_IN' as EnquirySource, Validators.required],
    assignedCounsellorId: ['', Validators.required],
    remarks: ['', Validators.maxLength(1000)],
  });

  protected fieldError(field: keyof typeof CONTROL_ID): string | null {
    const control = this.form.controls[field as keyof typeof this.form.controls];
    if (control.touched && control.hasError('required')) {
      return 'This is required.';
    }
    if (control.touched && control.hasError('email')) {
      return 'Enter a valid email address.';
    }
    const serverError = this.serverErrors()[field];
    return serverError ?? null;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({
      childFullName: value.childFullName.trim(),
      childDateOfBirth: value.childDateOfBirth || undefined,
      interestedClassId: value.interestedClassId || undefined,
      parentName: value.parentName.trim(),
      parentPhone: value.parentPhone.trim(),
      parentEmail: value.parentEmail.trim() || undefined,
      source: value.source,
      remarks: value.remarks.trim() || undefined,
      assignedCounsellorId: value.assignedCounsellorId,
    });
  }

  protected cancel(): void {
    this.cancelled.emit();
  }
}
