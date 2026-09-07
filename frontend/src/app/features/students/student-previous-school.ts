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
import { PreviousSchoolDetail } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { formatDay } from '../../shared/formatting/day';
import { ACCESS_DENIED } from './students-shared';

/**
 * Where a student came from, and the transfer certificate that admitted them (FR-033).
 *
 * Confidential, not masked (ADR-0014) — it identifies where a child came from, not what they are.
 * A fresh admission has nothing here, and that is a real state: `previousSchool` is absent on
 * `StudentDetail` rather than a row of empty fields.
 */
@Component({
  selector: 'cb-student-previous-school',
  imports: [ReactiveFormsModule, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-previous-school.html',
  styleUrl: './student-previous-school.scss',
})
export class StudentPreviousSchool {
  readonly studentId = input.required<string>();
  readonly record = input<PreviousSchoolDetail | null>(null);

  readonly changed = output<string>();

  private readonly students = inject(StudentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly canManage = permitted(Permissions.STUDENT_MANAGE);

  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly failureCode = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    previousSchoolName: ['', Validators.maxLength(200)],
    previousSchoolBoard: ['', Validators.maxLength(60)],
    transferCertificateNumber: ['', Validators.maxLength(60)],
    transferCertificateIssuedOn: '',
    reasonForLeaving: ['', Validators.maxLength(4000)],
  });

  protected readonly view = computed(() => {
    const record = this.record();
    return {
      previousSchoolName: record?.previousSchoolName?.trim() || null,
      previousSchoolBoard: record?.previousSchoolBoard?.trim() || null,
      transferCertificateNumber: record?.transferCertificateNumber?.trim() || null,
      transferCertificateIssuedOn: record?.transferCertificateIssuedOn
        ? formatDay(record.transferCertificateIssuedOn)
        : null,
      reasonForLeaving: record?.reasonForLeaving?.trim() || null,
    };
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change this section',
          detail: 'Ask your principal to add "Manage students" to your role.',
        };
      default:
        return {
          title: 'Could not save these details',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  protected startEdit(): void {
    this.failureCode.set(null);
    const record = this.record();
    this.form.reset(
      {
        previousSchoolName: record?.previousSchoolName ?? '',
        previousSchoolBoard: record?.previousSchoolBoard ?? '',
        transferCertificateNumber: record?.transferCertificateNumber ?? '',
        transferCertificateIssuedOn: record?.transferCertificateIssuedOn ?? '',
        reasonForLeaving: record?.reasonForLeaving ?? '',
      },
      { emitEvent: false },
    );
    this.editing.set(true);
    this.focusAfterRender('#previous-school-name');
  }

  protected cancel(): void {
    if (this.busy()) {
      return;
    }
    this.editing.set(false);
    this.focusAfterRender('#previous-school-edit');
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.failureCode.set(null);
    const value = this.form.getRawValue();

    this.students
      .savePreviousSchool(this.studentId(), {
        previousSchoolName: value.previousSchoolName.trim() || undefined,
        previousSchoolBoard: value.previousSchoolBoard.trim() || undefined,
        transferCertificateNumber: value.transferCertificateNumber.trim() || undefined,
        transferCertificateIssuedOn: value.transferCertificateIssuedOn || undefined,
        reasonForLeaving: value.reasonForLeaving.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editing.set(false);
          this.changed.emit('Previous school details were saved.');
          this.focusAfterRender('#previous-school-edit');
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
}
