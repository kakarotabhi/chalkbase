import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode } from '../../core/api/api-error';
import { AttendanceApi } from '../../core/api/attendance-api';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED, classAndSection, todayIsoDate } from './attendance-shared';

/** What each `ATT_0xx` code this screen can hit reads as, next to the field it is actually about. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  ATT_001: 'This student is not on that section’s roster any more. Reload and try again.',
  ATT_008: 'This school has not set a current academic session yet.',
  ATT_009: 'The last day cannot be before the first day.',
  ATT_010:
    'A leave request must start today or later. For a day that already happened, mark it and ' +
    'request a correction instead.',
};

/**
 * Filing a leave request (FR-047) for a student on a section's roster, in advance of the dates it
 * covers.
 *
 * The student picker is built from {@link AttendanceApi.sectionAttendance}, the same roster the
 * marking screen reads — there is no separate "roster of a section" endpoint on this module's
 * surface, and asking for one just to list names in a dropdown would be a second way to read data
 * `attendance:mark:read`/`attendance:leave:read` already governs, for the same rows.
 */
@Component({
  selector: 'cb-leave-request-form',
  imports: [ReactiveFormsModule, RouterLink, Button, Card, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './leave-request-form.html',
  styleUrl: './leave-request-form.scss',
})
export class LeaveRequestForm {
  private readonly academics = inject(AcademicsApi);
  private readonly api = inject(AttendanceApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  protected readonly form = this.formBuilder.group({
    sectionId: ['', Validators.required],
    studentId: ['', Validators.required],
    startDate: [todayIsoDate(), Validators.required],
    endDate: [todayIsoDate(), Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly today = todayIsoDate();

  protected readonly sectionOptions = signal<readonly SelectOption[]>([
    { value: '', label: 'Choose a section' },
  ]);
  protected readonly ladderFailed = signal(false);

  protected readonly studentOptions = signal<readonly SelectOption[]>([
    { value: '', label: 'Choose a student' },
  ]);
  protected readonly rosterLoading = signal(false);
  protected readonly rosterFailed = signal(false);

  protected readonly saving = signal(false);
  protected readonly failureCode = signal<string | null>(null);

  /**
   * There is no client-side gate on this route (ADR-0008): a caller without
   * `attendance:leave:request` reaches this form the same way anyone with it does, and finds out
   * on submission, the same as every other write in this app. `PERM_001` gets its own banner
   * because "you may not do this" is a different fact from "something about the dates was wrong".
   */
  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);

  protected readonly failureMessage = computed(() => {
    const code = this.failureCode();
    if (!code || code === ACCESS_DENIED) {
      return null;
    }
    return (
      ERROR_MESSAGES[code] ?? 'Could not send this request. Check your connection and try again.'
    );
  });

  constructor() {
    this.loadLadder();

    this.form.controls.sectionId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((sectionId) => {
        this.form.controls.studentId.setValue('');
        this.loadRoster(sectionId);
      });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();

    this.saving.set(true);
    this.failureCode.set(null);
    this.api
      .requestLeave(value.sectionId, {
        studentId: value.studentId,
        startDate: value.startDate,
        endDate: value.endDate,
        reason: value.reason.trim(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.router.navigate(['/attendance/leave', created.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private loadLadder(): void {
    this.academics
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (classes) => {
          const options: SelectOption[] = [{ value: '', label: 'Choose a section' }];
          for (const schoolClass of classes) {
            if (!schoolClass.active) {
              continue;
            }
            for (const section of schoolClass.sections) {
              if (!section.active) {
                continue;
              }
              options.push({
                value: section.id,
                label: classAndSection(schoolClass.name, section.name),
              });
            }
          }
          this.sectionOptions.set(options);
          this.ladderFailed.set(false);
        },
        error: () => {
          this.sectionOptions.set([{ value: '', label: 'Choose a section' }]);
          this.ladderFailed.set(true);
        },
      });
  }

  private loadRoster(sectionId: string): void {
    if (!sectionId) {
      this.studentOptions.set([{ value: '', label: 'Choose a student' }]);
      return;
    }
    this.rosterLoading.set(true);
    this.rosterFailed.set(false);
    this.api
      .sectionAttendance(sectionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (view) => {
          this.rosterLoading.set(false);
          const options: SelectOption[] = [{ value: '', label: 'Choose a student' }];
          for (const entry of view.entries) {
            options.push({
              value: entry.studentId,
              label: entry.rollNumber ? `${entry.rollNumber} · ${entry.fullName}` : entry.fullName,
            });
          }
          this.studentOptions.set(options);
        },
        error: () => {
          this.rosterLoading.set(false);
          this.rosterFailed.set(true);
          this.studentOptions.set([{ value: '', label: 'Choose a student' }]);
        },
      });
  }
}
