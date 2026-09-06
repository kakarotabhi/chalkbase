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
import { GuardianRelation, StudentGuardian } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Button } from '../../shared/components/button/button';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { Dialog } from '../../shared/components/dialog/dialog';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select } from '../../shared/components/select/select';
import { GuardianAttach } from './guardian-attach';
import {
  ACCESS_DENIED,
  CONFLICT,
  RELATION_LABELS,
  RELATION_OPTIONS,
  labelFor,
} from './students-shared';

/** One linked guardian, with everything the template needs already decided. */
interface GuardianRow {
  readonly linkId: string;
  readonly guardianId: string;
  readonly fullName: string;
  readonly relation: GuardianRelation;
  readonly relationLabel: string;
  readonly phone: string | null;
  readonly email: string | null;
  readonly occupation: string | null;
  readonly primary: boolean;
  readonly editButtonId: string;
  readonly primaryButtonId: string;
  readonly removeButtonId: string;
}

/**
 * The guardians on one student's record.
 *
 * ## What "remove" means here, and what it does not
 *
 * The only delete anywhere on these screens is `DELETE …/guardians/{linkId}`, and it ends a **link**
 * (ADR-0020 §5, §6). The person stays: their other children still point at the same row, and
 * deleting them because one child no longer lists them would blank a phone number on three
 * siblings' records. So the button says "Remove from this student", the confirmation says the
 * record survives, and nothing on this screen offers to delete a guardian outright — because
 * nothing in the API does.
 *
 * ## The main contact is a property of the student, not of the guardian
 *
 * At most one guardian per student is `primary`, and setting a new one clears the old one
 * server-side in the same transaction. Two rows therefore change on one request, which is why this
 * component never patches its own list: it tells the parent something changed and the parent
 * re-reads the student. Patching the row that was asked about would leave two guardians both
 * showing as the main contact until something else refetched — the same lesson as making an
 * academic session current.
 *
 * ## Contact details are not edited here
 *
 * A guardian's name, phone and email belong to the shared person record, so they are edited once on
 * the Guardians screen rather than per child. Offering an inline edit here would read as "correct
 * this for this child", which is not what it would do.
 */
@Component({
  selector: 'cb-student-guardians',
  imports: [ReactiveFormsModule, Button, Checkbox, Dialog, FormField, Select, GuardianAttach],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-guardians.html',
  styleUrl: './student-guardians.scss',
})
export class StudentGuardians {
  readonly studentId = input.required<string>();
  readonly guardians = input.required<readonly StudentGuardian[]>();

  /** Something was written. The parent re-reads the student and says so in its live region. */
  readonly changed = output<string>();

  private readonly students = inject(StudentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly relationOptions = RELATION_OPTIONS;

  protected readonly form = this.formBuilder.group({
    relation: ['', Validators.required],
    primary: false,
  });

  protected readonly attaching = signal(false);
  /** The link being edited in place, or null. */
  protected readonly editingLinkId = signal<string | null>(null);
  /** The link waiting on a confirmed removal, or null. */
  protected readonly removing = signal<StudentGuardian | null>(null);

  protected readonly busy = signal(false);
  protected readonly failureCode = signal<string | null>(null);

  protected readonly rows = computed<readonly GuardianRow[]>(() =>
    // Main contact first: it is the row somebody looking for a number wants, and it saves them
    // reading five to find the one that matters.
    [...this.guardians()]
      .sort((a, b) => Number(b.primary) - Number(a.primary))
      .map((guardian) => ({
        linkId: guardian.linkId,
        guardianId: guardian.guardianId,
        fullName: guardian.fullName,
        relation: guardian.relation,
        relationLabel: labelFor(RELATION_LABELS, guardian.relation),
        phone: guardian.phone?.trim() || null,
        email: guardian.email?.trim() || null,
        occupation: guardian.occupation?.trim() || null,
        primary: guardian.primary,
        editButtonId: `guardian-edit-${guardian.linkId}`,
        primaryButtonId: `guardian-make-primary-${guardian.linkId}`,
        removeButtonId: `guardian-remove-${guardian.linkId}`,
      })),
  );

  protected readonly linkedGuardianIds = computed(() =>
    this.guardians().map((guardian) => guardian.guardianId),
  );

  /** True when somebody is on the record but nobody is the main contact. Worth saying out loud. */
  protected readonly noPrimary = computed(
    () => this.guardians().length > 0 && !this.guardians().some((guardian) => guardian.primary),
  );

  protected readonly relationError = computed(() => {
    this.revision();
    const control = this.form.controls.relation;
    if (!control.touched) {
      return null;
    }
    return control.hasError('required') ? 'Choose what this person is to the student.' : null;
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case CONFLICT:
        return {
          title: 'That change was refused',
          detail: 'Reload this record to see how it stands now.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change guardians',
          detail: 'Ask your principal to add "Manage guardians" to your role.',
        };
      default:
        return {
          title: 'Could not save that change',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  private readonly revision = signal(0);

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.revision.update((count) => count + 1));
  }

  // ── Attaching ────────────────────────────────────────────────────────────────────────────

  protected startAttach(): void {
    this.failureCode.set(null);
    this.editingLinkId.set(null);
    this.attaching.set(true);
  }

  protected cancelAttach(): void {
    this.attaching.set(false);
    this.focusAfterRender('#guardian-attach-open');
  }

  protected onAttached(name: string): void {
    this.attaching.set(false);
    this.changed.emit(`${name} was attached to this student.`);
    this.focusAfterRender('#guardian-attach-open');
  }

  // ── Editing a link ───────────────────────────────────────────────────────────────────────

  protected startEdit(row: GuardianRow): void {
    this.failureCode.set(null);
    this.attaching.set(false);
    this.form.reset({ relation: row.relation, primary: row.primary }, { emitEvent: false });
    this.form.enable({ emitEvent: false });
    this.editingLinkId.set(row.linkId);
    this.focusAfterRender('#guardian-link-relation');
  }

  protected cancelEdit(): void {
    if (this.busy()) {
      return;
    }
    const linkId = this.editingLinkId();
    this.editingLinkId.set(null);
    this.focusAfterRender(linkId ? `#guardian-edit-${linkId}` : '#guardian-attach-open');
  }

  protected saveEdit(row: GuardianRow): void {
    if (this.busy()) {
      return;
    }
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      this.focus('#guardian-link-relation');
      return;
    }
    const { relation, primary } = this.form.getRawValue();
    this.write(
      row,
      { relation: relation as GuardianRelation, primary },
      `${row.fullName} was saved.`,
      `#guardian-edit-${row.linkId}`,
    );
  }

  /**
   * One click, because "who do we ring first" is a decision somebody makes constantly and putting
   * it behind an editor would be three interactions for a single boolean. The relation goes back
   * unchanged: the endpoint replaces the link, so leaving it out would blank it.
   */
  protected makePrimary(row: GuardianRow): void {
    if (this.busy() || row.primary) {
      return;
    }
    this.write(
      row,
      { relation: row.relation, primary: true },
      `${row.fullName} is now the main contact for this student.`,
      `#guardian-edit-${row.linkId}`,
    );
  }

  // ── Removing a link ──────────────────────────────────────────────────────────────────────

  protected askToRemove(row: GuardianRow): void {
    const guardian = this.guardians().find((candidate) => candidate.linkId === row.linkId);
    if (guardian && !this.busy()) {
      this.failureCode.set(null);
      this.removing.set(guardian);
    }
  }

  protected cancelRemove(): void {
    const target = this.removing();
    if (this.busy() || !target) {
      return;
    }
    this.removing.set(null);
    // A dialog emits and never closes itself, and returning focus is the caller's job.
    this.focusAfterRender(`#guardian-remove-${target.linkId}`);
  }

  protected confirmRemove(): void {
    const target = this.removing();
    if (this.busy() || !target) {
      return;
    }
    this.busy.set(true);
    this.failureCode.set(null);

    this.students
      .detachGuardian(this.studentId(), target.linkId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.removing.set(null);
          this.changed.emit(
            `${target.fullName} was removed from this student. Their guardian record is unchanged.`,
          );
          this.focusAfterRender('#guardian-attach-open');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.removing.set(null);
          this.failureCode.set(apiErrorCode(error));
          this.focusAfterRender(`#guardian-remove-${target.linkId}`);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private write(
    row: GuardianRow,
    request: { relation: GuardianRelation; primary: boolean },
    announcement: string,
    focusSelector: string,
  ): void {
    this.busy.set(true);
    this.failureCode.set(null);
    this.form.disable({ emitEvent: false });

    this.students
      .updateGuardianLink(this.studentId(), row.linkId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.form.enable({ emitEvent: false });
          this.editingLinkId.set(null);
          // Never patched here: setting a new main contact clears the old one server-side, so two
          // rows changed and only a re-read shows both correctly.
          this.changed.emit(announcement);
          this.focusAfterRender(focusSelector);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.form.enable({ emitEvent: false });
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private focus(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus();
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.focus(selector), { injector: this.injector });
  }
}
