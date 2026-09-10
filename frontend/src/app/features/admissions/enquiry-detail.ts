import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdmissionApi } from '../../core/api/admission-api';
import { apiErrorCode } from '../../core/api/api-error';
import { EnquiryDetailResponse, EnquiryStatus, UserSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge, BadgeTone } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { formatDay, today } from '../../shared/formatting/day';
import {
  ACCESS_DENIED,
  COUNSELLOR_NOT_ACTIVE,
  ENQUIRY_CLOSED,
  FOLLOW_UP_RESULT_OPTIONS,
  NEXT_FOLLOW_UP_DATE_REQUIRED,
  NOT_FOUND,
  SOURCE_LABELS,
  STATUS_CANNOT_REOPEN_TO_NEW,
  STATUS_LABELS,
  STATUS_TONES,
  labelFor,
} from './admissions-shared';

/** Sentences for the codes worth telling apart from a bare "could not save" (AGENTS.md's own note on this). */
const REASSIGN_MESSAGES: Readonly<Record<string, string>> = {
  [COUNSELLOR_NOT_ACTIVE]: 'That account is not active and cannot be assigned an enquiry.',
  [NOT_FOUND]: 'That account could not be found.',
};

const FOLLOW_UP_MESSAGES: Readonly<Record<string, string>> = {
  [ENQUIRY_CLOSED]:
    'This enquiry has already converted or been marked lost — nothing more to follow up.',
  [STATUS_CANNOT_REOPEN_TO_NEW]: 'A follow-up cannot set an enquiry back to New.',
  [NEXT_FOLLOW_UP_DATE_REQUIRED]:
    'Give a next follow-up date, or close the enquiry as Converted or Lost.',
};

/** One follow-up entry, with everything the template needs already decided. */
interface FollowUpRow {
  readonly id: string;
  readonly note: string;
  readonly recordedByName: string;
  readonly recordedAt: string;
  readonly resultingStatusLabel: string | null;
  readonly nextFollowUpDate: string | null;
}

/**
 * One enquiry's record: who they are, who owns their follow-up, and everything said to them so
 * far (FR-016, FR-017).
 *
 * ## Why this screen exists, not just a list
 *
 * The list answers "who has enquired"; this answers "what happened, and what happens next" for one
 * family — the follow-up history and the form to add to it, which is the whole reason enquiry
 * management is not a capture form (see `docs/requirements/08-phase-2-scope.md` §"Admissions").
 *
 * ## Reassigning and logging a follow-up are separate actions
 *
 * `POST /assign` only ever changes who owns the enquiry; `POST /follow-ups` is the only place its
 * status and next follow-up date move. Folding them into one form would let a reassignment silently
 * carry a status change nobody asked for.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008: the server hides the menu item and the endpoint enforces the permission. A 404 for an
 * id not at this school is explained rather than reported as a fault.
 */
@Component({
  selector: 'cb-enquiry-detail',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Card, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './enquiry-detail.html',
  styleUrl: './enquiry-detail.scss',
})
export class EnquiryDetail {
  /** Bound from the route by `withComponentInputBinding`. A UUID, and nothing about the child. */
  readonly id = input.required<string>();

  private readonly admissions = inject(AdmissionApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly canManage = permitted(Permissions.ADMISSION_ENQUIRY_MANAGE);

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly enquiry = signal<EnquiryDetailResponse | null>(null);

  protected readonly counsellors = signal<readonly UserSummary[]>([]);
  protected readonly counsellorsFailed = signal(false);

  protected readonly reassigning = signal(false);
  protected readonly reassignForm = this.formBuilder.group({
    counsellorId: ['', Validators.required],
  });
  protected readonly reassignSaving = signal(false);
  protected readonly reassignFailureCode = signal<string | null>(null);
  /** True once "Save" has been pressed on the reassign form. */
  private readonly reassignAttempted = signal(false);
  /** Bumped on every change so `reassignFieldError` recomputes. */
  private readonly reassignRevision = signal(0);

  protected readonly followUpResultOptions = FOLLOW_UP_RESULT_OPTIONS;
  protected readonly followUpForm = this.formBuilder.group({
    note: ['', [Validators.required, Validators.maxLength(1000)]],
    nextFollowUpDate: [today()],
    resultingStatus: [''],
  });
  protected readonly followUpSaving = signal(false);
  protected readonly followUpFailureCode = signal<string | null>(null);
  /** True once "Log follow-up" has been pressed. */
  private readonly followUpAttempted = signal(false);
  /** Bumped on every change so `followUpFieldErrors` recomputes. */
  private readonly followUpRevision = signal(0);

  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly missing = computed(() => this.failureCode() === NOT_FOUND);
  protected readonly failed = computed(() => {
    const code = this.failureCode();
    return code !== null && code !== ACCESS_DENIED && code !== NOT_FOUND;
  });

  protected readonly closed = computed(() => {
    const status = this.enquiry()?.status;
    return status === 'CONVERTED' || status === 'LOST';
  });

  /** A sentence for the reassign form's refusal code, or a generic one for anything else. */
  protected readonly reassignErrorMessage = computed(() => {
    const code = this.reassignFailureCode();
    if (!code) {
      return null;
    }
    return REASSIGN_MESSAGES[code] ?? 'Could not reassign this enquiry. Try again in a moment.';
  });

  /** A sentence for the follow-up form's refusal code (`AdmissionErrorCode`), or a generic one. */
  protected readonly followUpErrorMessage = computed(() => {
    const code = this.followUpFailureCode();
    if (!code) {
      return null;
    }
    return (
      FOLLOW_UP_MESSAGES[code] ??
      'Could not log this follow-up. Check the fields above and try again.'
    );
  });

  protected readonly reassignFieldError = computed<string | null>(() => {
    this.reassignRevision();
    this.reassignAttempted();
    const control = this.reassignForm.controls.counsellorId;
    if (!control.touched && !this.reassignAttempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Choose who this enquiry is assigned to.';
    }
    return null;
  });

  /**
   * `resultingStatus` is not wired up here — it carries no validator. `nextFollowUpDate` is a
   * cross-field rule rather than one Angular validates on its own control (`AdmissionErrorCode`'s
   * own `NEXT_FOLLOW_UP_DATE_REQUIRED`, the same shape `StudentCompliance`'s APAAR consent check
   * uses): a date is required unless this follow-up is closing the enquiry.
   */
  protected readonly followUpFieldErrors = computed<
    Readonly<{ note: string | null; nextFollowUpDate: string | null }>
  >(() => {
    this.followUpRevision();
    this.followUpAttempted();
    return {
      note: this.followUpNoteError(),
      nextFollowUpDate: this.nextFollowUpDateError(),
    };
  });

  protected readonly counsellorOptions = computed<readonly SelectOption[]>(() =>
    this.counsellors().map((counsellor) => ({
      value: counsellor.id,
      label: counsellor.displayName,
    })),
  );

  protected readonly view = computed(() => {
    const enquiry = this.enquiry();
    if (!enquiry) {
      return null;
    }
    return {
      childFullName: enquiry.childFullName,
      childDateOfBirth: enquiry.childDateOfBirth ? formatDay(enquiry.childDateOfBirth) : null,
      interestedClassName: enquiry.interestedClassName ?? null,
      parentName: enquiry.parentName,
      parentPhone: enquiry.parentPhone,
      parentEmail: enquiry.parentEmail ?? null,
      source: labelFor(SOURCE_LABELS, enquiry.source),
      status: enquiry.status,
      statusLabel: labelFor(STATUS_LABELS, enquiry.status),
      statusTone: STATUS_TONES[enquiry.status] as BadgeTone,
      assignedCounsellorName: enquiry.assignedCounsellorName,
      remarks: enquiry.remarks ?? null,
      nextFollowUpDate: enquiry.nextFollowUpDate ? formatDay(enquiry.nextFollowUpDate) : null,
      capturedByName: enquiry.capturedByName,
      createdAt: formatDay(enquiry.createdAt.slice(0, 10)),
    };
  });

  protected readonly followUps = computed<readonly FollowUpRow[]>(() =>
    (this.enquiry()?.followUps ?? []).map((entry) => ({
      id: entry.id,
      note: entry.note,
      recordedByName: entry.recordedByName,
      recordedAt: formatDay(entry.recordedAt.slice(0, 10)),
      resultingStatusLabel: entry.resultingStatus
        ? labelFor(STATUS_LABELS, entry.resultingStatus)
        : null,
      nextFollowUpDate: entry.nextFollowUpDate ? formatDay(entry.nextFollowUpDate) : null,
    })),
  );

  constructor() {
    // Re-reads when the route id changes, which happens when somebody follows a link from one
    // enquiry to another without the component being torn down — the same reasoning
    // `student-detail.ts` gives for the identical pattern. Reading `this.id()` inside an effect
    // rather than directly in the constructor body also means it never runs before a caller (a
    // spec included) has had a chance to set the input.
    effect(() => {
      const id = this.id();
      this.load(id);
    });
    this.loadCounsellors();
    this.reassignForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.reassignRevision.update((count) => count + 1);
    });
    this.followUpForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.followUpRevision.update((count) => count + 1);
    });
  }

  protected reload(): void {
    this.load(this.id());
  }

  // ── Reassigning ──────────────────────────────────────────────────────────────────────────

  protected startReassign(): void {
    const current = this.enquiry();
    this.reassignFailureCode.set(null);
    this.reassignAttempted.set(false);
    this.reassignForm.reset(
      { counsellorId: current?.assignedCounsellorId ?? '' },
      { emitEvent: false },
    );
    this.reassigning.set(true);
  }

  protected cancelReassign(): void {
    if (this.reassignSaving()) {
      return;
    }
    this.reassigning.set(false);
  }

  protected submitReassign(): void {
    this.reassignAttempted.set(true);
    this.reassignForm.markAllAsTouched();
    this.reassignRevision.update((count) => count + 1);
    if (this.reassignForm.invalid || this.reassignSaving()) {
      return;
    }
    const counsellorId = this.reassignForm.getRawValue().counsellorId;

    this.reassignSaving.set(true);
    this.reassignFailureCode.set(null);
    this.admissions
      .assign(this.id(), { counsellorId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.reassignSaving.set(false);
          this.reassigning.set(false);
          this.enquiry.set(updated);
          this.announcement.set(`Reassigned to ${updated.assignedCounsellorName}.`);
        },
        error: (error: unknown) => {
          this.reassignSaving.set(false);
          this.reassignFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Logging a follow-up ──────────────────────────────────────────────────────────────────

  protected submitFollowUp(): void {
    this.followUpAttempted.set(true);
    this.followUpForm.markAllAsTouched();
    this.followUpRevision.update((count) => count + 1);
    if (this.followUpForm.invalid || this.followUpSaving() || this.nextFollowUpDateError()) {
      return;
    }
    const value = this.followUpForm.getRawValue();

    this.followUpSaving.set(true);
    this.followUpFailureCode.set(null);
    this.admissions
      .logFollowUp(this.id(), {
        note: value.note.trim(),
        nextFollowUpDate: value.nextFollowUpDate || undefined,
        resultingStatus: (value.resultingStatus || undefined) as EnquiryStatus | undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.followUpSaving.set(false);
          this.followUpAttempted.set(false);
          this.enquiry.set(updated);
          this.followUpForm.reset({ note: '', nextFollowUpDate: today(), resultingStatus: '' });
          this.announcement.set('Follow-up logged.');
        },
        error: (error: unknown) => {
          this.followUpSaving.set(false);
          this.followUpFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(id: string): void {
    this.loading.set(true);
    this.failureCode.set(null);
    this.admissions
      .enquiry(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enquiry) => {
          this.loading.set(false);
          this.enquiry.set(enquiry);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.enquiry.set(null);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  /** A failure here is not a failure of this screen: the record still reads, only reassignment is unavailable. */
  private loadCounsellors(): void {
    this.admissions
      .counsellors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (counsellors) => this.counsellors.set(counsellors),
        error: () => this.counsellorsFailed.set(true),
      });
  }

  private followUpNoteError(): string | null {
    const control = this.followUpForm.controls.note;
    if (!control.touched && !this.followUpAttempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Say what happened in this follow-up.';
    }
    if (control.hasError('maxlength')) {
      return 'What happened is 1000 characters or fewer.';
    }
    return null;
  }

  /** The rule the backend enforces as `NEXT_FOLLOW_UP_DATE_REQUIRED` — mirrored here so it shows
   * under the field it is about, rather than only after a round trip. */
  private nextFollowUpDateError(): string | null {
    if (!this.followUpAttempted()) {
      return null;
    }
    const value = this.followUpForm.getRawValue();
    const closing = value.resultingStatus === 'CONVERTED' || value.resultingStatus === 'LOST';
    if (!closing && !value.nextFollowUpDate) {
      return FOLLOW_UP_MESSAGES[NEXT_FOLLOW_UP_DATE_REQUIRED];
    }
    return null;
  }
}

export { ENQUIRY_CLOSED, NEXT_FOLLOW_UP_DATE_REQUIRED };
