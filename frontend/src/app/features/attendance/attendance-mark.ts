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
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode } from '../../core/api/api-error';
import { AttendanceApi } from '../../core/api/attendance-api';
import { AttendanceStatus, SectionAttendanceView } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { BottomSheet } from '../../shared/components/bottom-sheet/bottom-sheet';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  STATUS_OPTIONS,
  classAndSection,
  statusLabel,
  statusTone,
  todayIsoDate,
} from './attendance-shared';

/** One row of the roster, with the teacher's still-unsaved choice alongside what the server holds. */
interface RosterRow {
  readonly studentId: string;
  readonly admissionNumber: string;
  readonly fullName: string;
  readonly rollNumber: string | null;
  readonly markId: string | null;
  /** The teacher's current choice for this save. Null until "Mark all present" or a tap sets one. */
  readonly draftStatus: AttendanceStatus | null;
  readonly draftRemarks: string;
  readonly showRemarks: boolean;
  readonly savedStatus: AttendanceStatus | null;
  readonly savedRemarks: string | null;
  readonly correctionRequested: boolean;
  /**
   * True when an approved leave request (FR-047) covers this student for this date. Shown as a
   * note on the card whether or not the student is already marked; also what {@link applyView}
   * uses to default an unmarked student's {@link draftStatus} to `EXCUSED_LEAVE` — see the
   * ADR-0030 amendment for why the connection is made here, at mark time, rather than by the
   * approval itself.
   */
  readonly approvedLeave: boolean;
}

/**
 * The daily marking screen — a class teacher's morning register, built for a phone first.
 *
 * ## Cards at every width, not only below the wide breakpoint
 *
 * ADR-0010's usual pattern is cards below the wide size class and a table above it. This screen
 * departs from that deliberately: every row carries an interactive six-way status control and an
 * optional note, which does not compress into a table cell the way a name and a badge do, and this
 * is the first screen in the product whose primary user is a teacher standing in front of a class
 * on a 360px phone. A wider window gets the same cards, simply more of them per row's worth of
 * space, rather than a second layout to keep in step with the first.
 *
 * ## Marking thirty students fast
 *
 * "Mark all present, then change the three who are not" is the whole design: one tap sets every
 * undecided student to present, and the six-way control on each card is one tap to flip. Nothing is
 * sent to the server until **Save**, so a teacher can work through the list at their own pace and
 * lose nothing by being interrupted partway through.
 */
@Component({
  selector: 'cb-attendance-mark',
  imports: [ReactiveFormsModule, Badge, Button, Card, FormField, Select, TextInput, BottomSheet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './attendance-mark.html',
  styleUrl: './attendance-mark.scss',
})
export class AttendanceMark {
  private readonly academics = inject(AcademicsApi);
  private readonly api = inject(AttendanceApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Gates only the editing affordances — the "mark all present" toolbar, the status buttons, the
   * save action, and the correction-request action. There is no client-side gate on the screen
   * itself: ADR-0008 puts authorization on the server, and a caller with no `attendance:mark:read`
   * at all finds out the same way every other screen says so — the section load 403s and
   * `forbidden()` renders, once they have actually picked a section.
   */
  protected readonly canManage = permitted(Permissions.ATTENDANCE_MANAGE);

  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  /** For the correction sheet's `cb-select`, a stable reference so it does not "change" every cycle. */
  protected readonly correctionStatusOptions: readonly SelectOption[] = STATUS_OPTIONS.map(
    (option) => ({
      value: option.value,
      label: option.fullLabel,
    }),
  );

  protected readonly sectionControl = this.formBuilder.control('');
  protected readonly dateControl = this.formBuilder.control(todayIsoDate(), Validators.required);

  protected readonly sectionOptions = signal<readonly SelectOption[]>([
    { value: '', label: 'Choose a section' },
  ]);
  protected readonly ladderFailed = signal(false);

  protected readonly loading = signal(false);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly view = signal<SectionAttendanceView | null>(null);
  protected readonly rows = signal<readonly RosterRow[]>([]);

  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  protected readonly announcement = signal('');

  protected readonly correctionTarget = signal<RosterRow | null>(null);
  protected readonly correctionForm = this.formBuilder.group({
    requestedStatus: ['ABSENT' as AttendanceStatus, Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly correctionSaving = signal(false);
  protected readonly correctionFailureCode = signal<string | null>(null);

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );
  protected readonly locked = computed(() => this.view()?.locked ?? false);

  /**
   * Mirrors {@link sectionControl}'s value as a signal.
   *
   * A `FormControl`'s own `.value` is a plain getter, not a signal — reading it inside a
   * `computed()` establishes no dependency, so {@link sectionChosen} would compute once and never
   * again. This is kept in step by the same `valueChanges` subscription that already triggers
   * {@link load}.
   */
  private readonly selectedSectionId = signal('');

  protected readonly sectionChosen = computed(() => this.selectedSectionId() !== '');

  /** Whether {@link save} would actually send anything — a status set, and changed since it was saved. */
  protected readonly hasUnsavedMarks = computed(() =>
    this.rows().some((row) => {
      if (row.draftStatus === null) {
        return false;
      }
      return (
        row.draftStatus !== row.savedStatus || row.draftRemarks.trim() !== (row.savedRemarks ?? '')
      );
    }),
  );

  private latestRequest = 0;

  constructor() {
    this.loadLadder();

    this.sectionControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.selectedSectionId.set(value);
        this.load();
      });
    this.dateControl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.load();
    });
  }

  protected reload(): void {
    this.load();
  }

  /** Sets every student without a saved status yet to present. Does not touch anyone already decided. */
  protected markAllPresent(): void {
    this.rows.update((rows) =>
      rows.map((row) => (row.draftStatus === null ? { ...row, draftStatus: 'PRESENT' } : row)),
    );
  }

  protected setStatus(studentId: string, status: AttendanceStatus): void {
    this.rows.update((rows) =>
      rows.map((row) => (row.studentId === studentId ? { ...row, draftStatus: status } : row)),
    );
  }

  protected toggleRemarks(studentId: string): void {
    this.rows.update((rows) =>
      rows.map((row) =>
        row.studentId === studentId ? { ...row, showRemarks: !row.showRemarks } : row,
      ),
    );
  }

  protected setRemarks(studentId: string, remarks: string): void {
    this.rows.update((rows) =>
      rows.map((row) => (row.studentId === studentId ? { ...row, draftRemarks: remarks } : row)),
    );
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    const entries = this.rows()
      .filter((row) => row.draftStatus !== null)
      .map((row) => ({
        studentId: row.studentId,
        status: row.draftStatus as AttendanceStatus,
        remarks: row.draftRemarks.trim() || undefined,
      }));
    if (entries.length === 0) {
      this.announcement.set('Mark at least one student before saving.');
      return;
    }

    const sectionId = this.sectionControl.value;
    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.announcement.set('');

    this.api
      .mark(sectionId, { attendanceDate: this.dateControl.value, entries })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.applyView(result);
          this.announcement.set(`Saved attendance for ${entries.length} student(s).`);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Corrections ──────────────────────────────────────────────────────────────────────────

  protected openCorrection(row: RosterRow): void {
    this.correctionFailureCode.set(null);
    this.correctionForm.reset(
      { requestedStatus: row.savedStatus ?? 'PRESENT', reason: '' },
      { emitEvent: false },
    );
    this.correctionTarget.set(row);
  }

  protected closeCorrection(): void {
    if (this.correctionSaving()) {
      return;
    }
    this.correctionTarget.set(null);
  }

  protected submitCorrection(): void {
    const target = this.correctionTarget();
    if (!target || !target.markId || this.correctionSaving()) {
      return;
    }
    this.correctionForm.markAllAsTouched();
    if (this.correctionForm.invalid) {
      return;
    }
    const value = this.correctionForm.getRawValue();

    this.correctionSaving.set(true);
    this.correctionFailureCode.set(null);
    this.api
      .requestCorrection(target.markId, {
        requestedStatus: value.requestedStatus,
        reason: value.reason.trim(),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.correctionSaving.set(false);
          this.correctionTarget.set(null);
          this.rows.update((rows) =>
            rows.map((row) =>
              row.studentId === target.studentId ? { ...row, correctionRequested: true } : row,
            ),
          );
          this.announcement.set(`Correction requested for ${target.fullName}.`);
        },
        error: (error: unknown) => {
          this.correctionSaving.set(false);
          this.correctionFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

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

  private load(): void {
    const sectionId = this.sectionControl.value;
    if (!sectionId || this.dateControl.invalid) {
      this.view.set(null);
      this.rows.set([]);
      return;
    }

    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .sectionAttendance(sectionId, this.dateControl.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.loading.set(false);
          this.applyView(result);
        },
        error: (error: unknown) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.loading.set(false);
          this.view.set(null);
          this.rows.set([]);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private applyView(result: SectionAttendanceView): void {
    this.view.set(result);
    this.rows.set(
      result.entries.map((entry) => {
        const approvedLeave = !!entry.approvedLeave;
        return {
          studentId: entry.studentId,
          admissionNumber: entry.admissionNumber,
          fullName: entry.fullName,
          rollNumber: entry.rollNumber ?? null,
          markId: entry.markId ?? null,
          // An unmarked student with an approved leave request defaults to Excused leave rather
          // than staying blank — still a draft, still overridable by a tap, never sent until Save.
          draftStatus: entry.status ?? (approvedLeave ? 'EXCUSED_LEAVE' : null),
          draftRemarks: entry.remarks ?? '',
          showRemarks: !!entry.remarks,
          savedStatus: entry.status ?? null,
          savedRemarks: entry.remarks ?? null,
          correctionRequested: false,
          approvedLeave,
        };
      }),
    );
  }
}
