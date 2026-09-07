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
import { ComplianceDetail, ComplianceSummary } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED } from './students-shared';

const APAAR_REQUIRES_CONSENT = 'STU_019';

/**
 * A student's UDISE+/board identifiers and statutory categories (FR-029): PEN/UDISE, the board
 * registration number, caste and community, religion, EWS/BPL/RTE category, and an APAAR id.
 *
 * ## Masked by default, revealed by an explicit action
 *
 * Caste, religion, category and the APAAR id are Restricted (ADR-0014) and masked the same way
 * `cb-student-medical` masks its six fields — see that component's own comment, which applies here
 * unchanged. PEN/UDISE and the board registration number are Confidential identifiers, like an
 * admission number, and are always shown in full.
 *
 * ## APAAR is consent-based
 *
 * An APAAR id cannot be typed in without ticking "Consent given" and naming who gave it — the
 * backend refuses the write otherwise (`STU_019`), and this form refuses to submit it too, with the
 * same message, so the refusal is not a surprise a round trip has to explain.
 */
@Component({
  selector: 'cb-student-compliance',
  imports: [ReactiveFormsModule, Button, Checkbox, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-compliance.html',
  styleUrl: './student-compliance.scss',
})
export class StudentCompliance {
  readonly studentId = input.required<string>();
  readonly compliance = input<ComplianceSummary | null>(null);

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
  protected readonly apaarConsentError = signal<string | null>(null);
  protected readonly revealed = signal<ComplianceDetail | null>(null);

  protected readonly form = this.formBuilder.group({
    penUdiseId: ['', Validators.maxLength(40)],
    boardRegistrationNumber: ['', Validators.maxLength(40)],
    casteCategory: ['', Validators.maxLength(100)],
    religion: ['', Validators.maxLength(100)],
    specialCategory: ['', Validators.maxLength(100)],
    apaarId: ['', Validators.maxLength(40)],
    apaarConsentGiven: false,
    apaarConsentGivenBy: ['', Validators.maxLength(200)],
  });

  protected readonly summary = computed(() => this.compliance() ?? EMPTY_COMPLIANCE_SUMMARY);

  protected readonly hasAnyRestricted = computed(() => {
    const s = this.summary();
    return s.hasCasteCategory || s.hasReligion || s.hasSpecialCategory || s.hasApaarId;
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
          title: 'Could not save these details',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  protected valueFor(has: boolean, field: keyof ComplianceDetail): string | null {
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
      .revealCompliance(this.studentId())
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
      this.revealing.set(true);
      this.failureCode.set(null);
      this.students
        .revealCompliance(this.studentId())
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

  private openEditor(detail: ComplianceDetail | null): void {
    const summary = this.summary();
    this.apaarConsentError.set(null);
    this.form.reset(
      {
        penUdiseId: summary.penUdiseId ?? '',
        boardRegistrationNumber: summary.boardRegistrationNumber ?? '',
        casteCategory: detail?.casteCategory ?? '',
        religion: detail?.religion ?? '',
        specialCategory: detail?.specialCategory ?? '',
        apaarId: detail?.apaarId ?? '',
        apaarConsentGiven: summary.apaarConsentGiven,
        apaarConsentGivenBy: summary.apaarConsentGivenBy ?? '',
      },
      { emitEvent: false },
    );
    this.editing.set(true);
    this.focusAfterRender('#compliance-pen-udise');
  }

  protected cancel(): void {
    if (this.busy()) {
      return;
    }
    this.editing.set(false);
    this.focusAfterRender('#compliance-edit');
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    this.form.markAllAsTouched();
    this.apaarConsentError.set(null);
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    const apaarId = v.apaarId.trim();
    if (apaarId && !v.apaarConsentGiven) {
      this.apaarConsentError.set(
        'An APAAR id cannot be saved without consent. Tick "Consent given" and name who gave it, ' +
          'or remove the APAAR id.',
      );
      return;
    }

    this.busy.set(true);
    this.failureCode.set(null);

    this.students
      .saveCompliance(this.studentId(), {
        penUdiseId: v.penUdiseId.trim() || undefined,
        boardRegistrationNumber: v.boardRegistrationNumber.trim() || undefined,
        casteCategory: v.casteCategory.trim() || undefined,
        religion: v.religion.trim() || undefined,
        specialCategory: v.specialCategory.trim() || undefined,
        apaarId: apaarId || undefined,
        apaarConsentGiven: v.apaarConsentGiven,
        apaarConsentGivenBy: v.apaarConsentGivenBy.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editing.set(false);
          this.revealed.set({
            casteCategory: v.casteCategory.trim() || undefined,
            religion: v.religion.trim() || undefined,
            specialCategory: v.specialCategory.trim() || undefined,
            apaarId: apaarId || undefined,
          });
          this.changed.emit('The compliance details were saved.');
          this.focusAfterRender('#compliance-edit');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          if (apiErrorCode(error) === APAAR_REQUIRES_CONSENT) {
            // A business rule, not a per-field validation failure (ADR-0007's `details` map is
            // for bean validation), so this app's own wording rather than the server's `message`.
            this.apaarConsentError.set(
              'An APAAR id cannot be saved without recording that consent was given.',
            );
            return;
          }
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

const EMPTY_COMPLIANCE_SUMMARY: ComplianceSummary = {
  hasCasteCategory: false,
  hasReligion: false,
  hasSpecialCategory: false,
  hasApaarId: false,
  apaarConsentGiven: false,
};
