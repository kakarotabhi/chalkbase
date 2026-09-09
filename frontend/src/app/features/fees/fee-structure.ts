import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode } from '../../core/api/api-error';
import { FeeApi } from '../../core/api/fee-api';
import {
  AcademicSession,
  FeeHead,
  FeeStructure,
  SaveFeeStructureRequest,
  SchoolClass,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import {
  ACCESS_DENIED,
  CANNOT_COPY_SESSION_INTO_ITSELF,
  DEVELOPMENT_FEE_EXCEEDS_CAP,
  DUPLICATE_HEAD_IN_STRUCTURE,
  INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT,
  INSTALLMENT_FREQUENCY_OPTIONS,
  STRUCTURE_SESSION_CLOSED,
  formatRupees,
  installmentFrequencyLabel,
} from './fees-shared';

let nextDraftKey = 0;

interface InstallmentDraft {
  readonly key: number;
  readonly dueDate: string;
  readonly amount: string;
}

interface ItemDraft {
  readonly key: number;
  readonly feeHeadId: string;
  readonly amount: string;
  readonly frequency: string;
  readonly installments: readonly InstallmentDraft[];
}

interface ClassRow {
  readonly id: string;
  readonly name: string;
  readonly active: boolean;
  readonly structure: FeeStructure | null;
  readonly total: string;
}

/**
 * The school's fee structure, per class, for one academic session (ADR-0012 rule 6, ADR-0033).
 *
 * ## An edit is a new version, never a rewrite
 *
 * "Save" here always sends the complete item list to `PUT /api/fees/structures/{session}/{class}`,
 * which the backend turns into a brand new version — see `FeeApi.saveStructure` and
 * `FeeStructureService`'s own Javadoc. This screen never claims to "edit" a structure; it loads
 * the current one, lets the school change it, and resubmits the whole thing. A session that has
 * already run its course refuses a second version (`FEE_009`), and this screen shows that refusal
 * as a real answer rather than a generic failure.
 *
 * ## The new-session question
 *
 * A school starting a new session either retypes last year's structure or copies it forward and
 * adjusts what changed. Copying is offered as an explicit, named action — never automatic — and it
 * never overwrites a class the school has already set up in the destination session; see
 * `FeeApi.copyStructure` and ADR-0033 for the argument.
 *
 * ## Plain signals, not a `FormArray`
 *
 * The item/installment editor is a variable-length, two-level list — the same shape
 * `AttendanceMark`'s roster editor is, and it follows that screen's choice: draft rows live in a
 * signal and are replaced immutably on every change, rather than a nested reactive `FormArray`.
 * Validation here is a readiness check that disables Save, not per-field reactive validators; the
 * server's own error codes (`FEE_004` .. `FEE_009`) are what a mistake that slips through is
 * explained by.
 */
@Component({
  selector: 'cb-fee-structure',
  imports: [ReactiveFormsModule, RouterLink, Badge, Button, Card, FormField, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fee-structure.html',
  styleUrl: './fee-structure.scss',
})
export class FeeStructurePage {
  private readonly academics = inject(AcademicsApi);
  private readonly fees = inject(FeeApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly frequencyOptions = INSTALLMENT_FREQUENCY_OPTIONS;
  protected readonly frequencyLabel = installmentFrequencyLabel;
  protected readonly formatRupees = formatRupees;

  protected readonly canManage = permitted(Permissions.FEE_STRUCTURE_MANAGE);

  protected readonly loading = signal(true);
  protected readonly loadFailureCode = signal<string | null>(null);
  protected readonly forbidden = computed(() => this.loadFailureCode() === ACCESS_DENIED);
  protected readonly loadFailed = computed(
    () => this.loadFailureCode() !== null && !this.forbidden(),
  );

  protected readonly sessions = signal<readonly AcademicSession[]>([]);
  protected readonly selectedSessionId = signal('');
  /** What `cb-select` binds to. Kept in sync with {@link selectedSessionId}, the signal everything else reads. */
  protected readonly sessionControl = new FormControl('', { nonNullable: true });
  protected readonly classes = signal<readonly SchoolClass[]>([]);
  protected readonly feeHeads = signal<readonly FeeHead[]>([]);
  protected readonly structures = signal<readonly FeeStructure[]>([]);

  protected readonly sessionOptions = computed<readonly SelectOption[]>(() =>
    this.sessions().map((session) => ({
      value: session.id,
      label: session.current ? `${session.name} (current)` : session.name,
    })),
  );

  protected readonly headOptions = computed<readonly SelectOption[]>(() =>
    this.feeHeads().map((head) => ({
      value: head.id,
      label: head.active ? head.name : `${head.name} (not offered)`,
    })),
  );

  protected readonly rows = computed<readonly ClassRow[]>(() => {
    const byClassId = new Map(
      this.structures().map((structure) => [structure.schoolClassId, structure]),
    );
    return this.classes().map((schoolClass) => {
      const structure = byClassId.get(schoolClass.id) ?? null;
      const total = structure
        ? structure.items.reduce((sum, item) => sum + Number(item.amount), 0)
        : 0;
      return {
        id: schoolClass.id,
        name: schoolClass.name,
        active: schoolClass.active,
        structure,
        total: formatRupees(total),
      };
    });
  });

  // ── The item/installment editor ─────────────────────────────────────────────────────────

  protected readonly editingClassId = signal<string | null>(null);
  protected readonly draftItems = signal<readonly ItemDraft[]>([]);
  protected readonly saving = signal(false);
  protected readonly writeFailureCode = signal<string | null>(null);
  protected readonly announcement = signal('');

  protected readonly editingClassName = computed(
    () => this.rows().find((row) => row.id === this.editingClassId())?.name ?? '',
  );

  /** A save is only offered once every item and every installment holds something plausible. */
  protected readonly draftIsReady = computed(() => {
    const items = this.draftItems();
    if (items.length === 0) {
      return false;
    }
    return items.every(
      (item) =>
        item.feeHeadId !== '' &&
        Number(item.amount) > 0 &&
        item.installments.length > 0 &&
        item.installments.every(
          (installment) => installment.dueDate !== '' && Number(installment.amount) > 0,
        ),
    );
  });

  protected readonly writeFailure = computed(() => {
    switch (this.writeFailureCode()) {
      case null:
        return null;
      case STRUCTURE_SESSION_CLOSED:
        return {
          title: 'This academic session has already run',
          detail:
            'Its fee structure was filed and cannot be changed again. Only its very first ' +
            'version may ever be recorded after the fact — this one already has one.',
        };
      case DEVELOPMENT_FEE_EXCEEDS_CAP:
        return {
          title: 'The development fee exceeds the cap set on that head',
          detail: 'Lower the amount, or raise the cap on the fee head itself.',
        };
      case DUPLICATE_HEAD_IN_STRUCTURE:
        return {
          title: 'The same fee head appears twice',
          detail: 'Each head may only appear once in a class’s structure.',
        };
      case INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT:
        return {
          title: "An item's installments do not add up to its amount",
          detail: 'Check the due dates and amounts under the fee head they belong to.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change this',
          detail: 'Ask your principal to add "Manage fee structures" to your role.',
        };
      default:
        return {
          title: 'Could not save this structure',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  // ── Copy from a previous session ─────────────────────────────────────────────────────────

  protected readonly copyFromSessionId = signal('');
  /** What `cb-select` binds to for the "copy from" choice. */
  protected readonly copyFromControl = new FormControl('', { nonNullable: true });
  protected readonly copying = signal(false);
  protected readonly copyFailureCode = signal<string | null>(null);
  protected readonly copySummary = signal<string | null>(null);

  protected readonly copyFromOptions = computed<readonly SelectOption[]>(() =>
    this.sessions()
      .filter((session) => session.id !== this.selectedSessionId())
      .map((session) => ({ value: session.id, label: session.name })),
  );

  protected readonly copyFailure = computed(() => {
    switch (this.copyFailureCode()) {
      case null:
        return null;
      case CANNOT_COPY_SESSION_INTO_ITSELF:
        return 'Choose a different session to copy from.';
      case ACCESS_DENIED:
        return 'You do not have permission to do this.';
      default:
        return 'Could not copy the structure. Check your connection and try again.';
    }
  });

  constructor() {
    this.loadShell();

    this.sessionControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((sessionId) => this.onSessionChange(sessionId));
    this.copyFromControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((sessionId) => this.copyFromSessionId.set(sessionId));
  }

  protected reload(): void {
    this.loadShell();
  }

  protected onSessionChange(sessionId: string): void {
    this.selectedSessionId.set(sessionId);
    this.editingClassId.set(null);
    this.copySummary.set(null);
    this.copyFromControl.setValue('', { emitEvent: false });
    this.copyFromSessionId.set('');
    this.loadStructures();
  }

  // ── The editor ───────────────────────────────────────────────────────────────────────────

  protected startEdit(row: ClassRow): void {
    this.editingClassId.set(row.id);
    this.writeFailureCode.set(null);
    this.announcement.set('');
    this.draftItems.set(
      row.structure
        ? row.structure.items.map((item) => ({
            key: nextDraftKey++,
            feeHeadId: item.feeHeadId,
            amount: String(item.amount),
            frequency: item.frequency,
            installments: item.installments.map((installment) => ({
              key: nextDraftKey++,
              dueDate: installment.dueDate,
              amount: String(installment.amount),
            })),
          }))
        : [],
    );
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    this.editingClassId.set(null);
    this.draftItems.set([]);
  }

  protected addItem(): void {
    const usedHeadIds = new Set(this.draftItems().map((item) => item.feeHeadId));
    const nextHead = this.feeHeads().find((head) => !usedHeadIds.has(head.id));
    this.draftItems.update((items) => [
      ...items,
      {
        key: nextDraftKey++,
        feeHeadId: nextHead?.id ?? '',
        amount: '',
        frequency: 'ANNUAL',
        installments: [],
      },
    ]);
  }

  protected removeItem(key: number): void {
    this.draftItems.update((items) => items.filter((item) => item.key !== key));
  }

  protected updateItem(key: number, patch: Partial<ItemDraft>): void {
    this.draftItems.update((items) =>
      items.map((item) => (item.key === key ? { ...item, ...patch } : item)),
    );
  }

  protected addInstallment(itemKey: number): void {
    this.draftItems.update((items) =>
      items.map((item) =>
        item.key === itemKey
          ? {
              ...item,
              installments: [
                ...item.installments,
                { key: nextDraftKey++, dueDate: '', amount: '' },
              ],
            }
          : item,
      ),
    );
  }

  protected removeInstallment(itemKey: number, installmentKey: number): void {
    this.draftItems.update((items) =>
      items.map((item) =>
        item.key === itemKey
          ? {
              ...item,
              installments: item.installments.filter(
                (installment) => installment.key !== installmentKey,
              ),
            }
          : item,
      ),
    );
  }

  protected updateInstallment(
    itemKey: number,
    installmentKey: number,
    patch: Partial<InstallmentDraft>,
  ): void {
    this.draftItems.update((items) =>
      items.map((item) =>
        item.key === itemKey
          ? {
              ...item,
              installments: item.installments.map((installment) =>
                installment.key === installmentKey ? { ...installment, ...patch } : installment,
              ),
            }
          : item,
      ),
    );
  }

  protected save(): void {
    const classId = this.editingClassId();
    const sessionId = this.selectedSessionId();
    if (!classId || !sessionId || this.saving() || !this.draftIsReady()) {
      return;
    }

    const request: SaveFeeStructureRequest = {
      items: this.draftItems().map((item) => ({
        feeHeadId: item.feeHeadId,
        amount: Number(item.amount),
        frequency: item.frequency as SaveFeeStructureRequest['items'][number]['frequency'],
        installments: item.installments.map((installment) => ({
          dueDate: installment.dueDate,
          amount: Number(installment.amount),
        })),
      })),
    };

    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.announcement.set('');

    this.fees
      .saveStructure(sessionId, classId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (structure) => {
          this.saving.set(false);
          this.editingClassId.set(null);
          this.draftItems.set([]);
          this.announcement.set(
            `${structure.schoolClassName}'s structure saved as version ${structure.version}.`,
          );
          this.loadStructures();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.writeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Copy from a previous session ─────────────────────────────────────────────────────────

  protected copyFromPrevious(): void {
    const fromSessionId = this.copyFromSessionId();
    const toSessionId = this.selectedSessionId();
    if (!fromSessionId || !toSessionId || this.copying()) {
      return;
    }

    this.copying.set(true);
    this.copyFailureCode.set(null);
    this.copySummary.set(null);

    this.fees
      .copyStructure({ fromSessionId, toSessionId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.copying.set(false);
          const copiedCount = result.copied.length;
          const skippedCount = result.skippedClassNames.length;
          this.copySummary.set(
            skippedCount === 0
              ? `Copied ${copiedCount} ${copiedCount === 1 ? 'class' : 'classes'}.`
              : `Copied ${copiedCount} ${copiedCount === 1 ? 'class' : 'classes'}. ` +
                  `${skippedCount} already had a structure here and were left as they were: ` +
                  `${result.skippedClassNames.join(', ')}.`,
          );
          this.loadStructures();
        },
        error: (error: unknown) => {
          this.copying.set(false);
          this.copyFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private loadShell(): void {
    this.loading.set(true);
    this.loadFailureCode.set(null);

    this.academics
      .sessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          this.sessions.set(sessions);
          const current = sessions.find((session) => session.current) ?? sessions[0];
          this.selectedSessionId.set(current?.id ?? '');
          this.sessionControl.setValue(current?.id ?? '', { emitEvent: false });
          this.loadClassesAndHeads();
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private loadClassesAndHeads(): void {
    this.academics
      .classes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (classes) => {
          this.classes.set(classes);
          this.fees
            .heads()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (heads) => {
                this.feeHeads.set(heads);
                this.loadStructures();
              },
              error: (error: unknown) => {
                this.loading.set(false);
                this.loadFailureCode.set(apiErrorCode(error));
              },
            });
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private loadStructures(): void {
    const sessionId = this.selectedSessionId();
    if (!sessionId) {
      this.structures.set([]);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.fees
      .structures(sessionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (structures) => {
          this.structures.set(structures);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.structures.set([]);
          this.loading.set(false);
          this.loadFailureCode.set(apiErrorCode(error));
        },
      });
  }
}
