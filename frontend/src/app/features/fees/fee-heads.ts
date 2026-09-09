import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { FeeApi } from '../../core/api/fee-api';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { FeeConcessionType, FeeHead } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  CAP_PERCENT_NOT_APPLICABLE,
  DUPLICATE_CONCESSION_TYPE_NAME,
  DUPLICATE_FEE_HEAD_NAME,
  FEE_CONCESSION_CATEGORY_OPTIONS,
  FEE_HEAD_CATEGORY_OPTIONS,
  feeConcessionCategoryLabel,
  feeHeadCategoryLabel,
} from './fees-shared';

/** Which of the four editors is open. Only ever one at a time, the same discipline `SchoolClasses` uses. */
type Editor =
  | { readonly kind: 'new-head' }
  | { readonly kind: 'head'; readonly id: string }
  | { readonly kind: 'new-concession' }
  | { readonly kind: 'concession'; readonly id: string }
  | null;

/**
 * The school's catalogue of fee heads and concession types (Phase 0 §4, FR-075, FR-078,
 * ADR-0012).
 *
 * ## Two independent lists, two independent permissions
 *
 * `fee:head:read`/`manage` and `fee:concession_type:read`/`manage` are separate grants —
 * `ADMISSION_COUNSELLOR` holds the first and not the second (`RoleTemplates`'s own Javadoc has the
 * argument). So this screen never shows one all-or-nothing "forbidden" banner: each section loads,
 * fails and is gated on its own permission, the same way a dashboard tile answers for itself.
 *
 * ## Defining a concession type grants nothing
 *
 * The list below is a catalogue — sibling discount, staff-child, management quota, RTE/EWS,
 * scholarship, other. Applying one to an actual student's fee is fee demand's work, once that
 * lane exists (ADR-0012's `fee_ledger_entry` needs a `fee_charge` to attach to, and there is none
 * yet). This screen only ever asks "what kinds of waiver does this school offer", never "who gets
 * one".
 *
 * ## Nothing here deletes
 *
 * A head or a concession type already named by a fee structure item must not point at nothing. A
 * row created by mistake is retired, the same discipline `SchoolClasses` and `Subjects` follow.
 */
@Component({
  selector: 'cb-fee-heads',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    Badge,
    Button,
    Card,
    Checkbox,
    FormField,
    Select,
    TextInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './fee-heads.html',
  styleUrl: './fee-heads.scss',
})
export class FeeHeads {
  private readonly api = inject(FeeApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly headCategoryOptions = FEE_HEAD_CATEGORY_OPTIONS;
  protected readonly concessionCategoryOptions = FEE_CONCESSION_CATEGORY_OPTIONS;
  protected readonly categoryLabel = feeHeadCategoryLabel;
  protected readonly concessionLabel = feeConcessionCategoryLabel;

  protected readonly canManageHeads = permitted(Permissions.FEE_HEAD_MANAGE);
  protected readonly canManageConcessions = permitted(Permissions.FEE_CONCESSION_TYPE_MANAGE);

  protected readonly headForm = this.formBuilder.group({
    name: ['', Validators.required],
    category: ['TUITION', Validators.required],
    capPercentOfTuition: [''],
  });

  protected readonly concessionForm = this.formBuilder.group({
    name: ['', Validators.required],
    category: ['SIBLING', Validators.required],
    description: [''],
    requiresApproval: [true],
  });

  // ── Fee heads ────────────────────────────────────────────────────────────────────────────

  protected readonly headsLoading = signal(true);
  protected readonly headsLoadFailureCode = signal<string | null>(null);
  protected readonly heads = signal<readonly FeeHead[]>([]);
  protected readonly headsForbidden = computed(() => this.headsLoadFailureCode() === ACCESS_DENIED);
  protected readonly headsLoadFailed = computed(
    () => this.headsLoadFailureCode() !== null && !this.headsForbidden(),
  );

  // ── Concession types ─────────────────────────────────────────────────────────────────────

  protected readonly concessionsLoading = signal(true);
  protected readonly concessionsLoadFailureCode = signal<string | null>(null);
  protected readonly concessionTypes = signal<readonly FeeConcessionType[]>([]);
  protected readonly concessionsForbidden = computed(
    () => this.concessionsLoadFailureCode() === ACCESS_DENIED,
  );
  protected readonly concessionsLoadFailed = computed(
    () => this.concessionsLoadFailureCode() !== null && !this.concessionsForbidden(),
  );

  // ── Shared editor state ──────────────────────────────────────────────────────────────────

  protected readonly editor = signal<Editor>(null);
  protected readonly saving = signal(false);
  protected readonly writeFailureCode = signal<string | null>(null);
  private readonly attempted = signal(false);
  private readonly serverErrors = signal<Readonly<Record<string, string>>>({});
  protected readonly announcement = signal('');

  protected readonly addingHead = computed(() => this.editor()?.kind === 'new-head');
  protected readonly addingConcession = computed(() => this.editor()?.kind === 'new-concession');

  protected readonly showCap = computed(() => {
    this.formValue();
    return this.headForm.controls.category.value === 'ANNUAL_DEVELOPMENT';
  });

  protected readonly writeFailure = computed(() => {
    switch (this.writeFailureCode()) {
      case null:
        return null;
      case DUPLICATE_FEE_HEAD_NAME:
        return {
          title: 'A fee head with that name already exists',
          detail:
            'A head kept its name even while retired — check the list below, including any switched off.',
        };
      case DUPLICATE_CONCESSION_TYPE_NAME:
        return {
          title: 'A concession type with that name already exists',
          detail: 'The same is true here: a retired one still holds its name.',
        };
      case CAP_PERCENT_NOT_APPLICABLE:
        return {
          title: 'A cap on tuition only applies to an annual/development fee head',
          detail: 'Choose that category first, or clear the cap.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change this',
          detail: 'Ask your principal to add the matching "Manage" permission to your role.',
        };
      case 'VAL_001':
        return { title: 'That was refused', detail: 'The message under the field says why.' };
      default:
        return {
          title: 'Could not save that change',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  private readonly formValue = signal(0);

  constructor() {
    this.loadHeads();
    this.loadConcessionTypes();

    this.headForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formValue.update((count) => count + 1);
      this.serverErrors.set({});
    });
    this.concessionForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.formValue.update((count) => count + 1);
      this.serverErrors.set({});
    });
  }

  protected reloadHeads(): void {
    this.loadHeads();
  }

  protected reloadConcessions(): void {
    this.loadConcessionTypes();
  }

  // ── Fee head editor ──────────────────────────────────────────────────────────────────────

  protected startAddHead(): void {
    this.openEditor({ kind: 'new-head' });
    this.headForm.reset(
      { name: '', category: 'TUITION', capPercentOfTuition: '' },
      { emitEvent: false },
    );
    this.focusAfterRender('#head-name');
  }

  protected startEditHead(head: FeeHead): void {
    this.openEditor({ kind: 'head', id: head.id });
    this.headForm.reset(
      {
        name: head.name,
        category: head.category,
        capPercentOfTuition:
          head.capPercentOfTuition !== undefined ? String(head.capPercentOfTuition) : '',
      },
      { emitEvent: false },
    );
    this.focusAfterRender('#head-name');
  }

  protected saveHead(): void {
    if (this.saving()) {
      return;
    }
    const editor = this.editor();
    if (!editor || (editor.kind !== 'new-head' && editor.kind !== 'head')) {
      return;
    }
    this.attempted.set(true);
    this.headForm.markAllAsTouched();
    if (this.headForm.invalid) {
      return;
    }

    const value = this.headForm.getRawValue();
    const capValue = value.capPercentOfTuition.trim();
    const existing = editor.kind === 'head' ? this.headById(editor.id) : null;
    const request = {
      name: value.name.trim(),
      category: value.category as FeeHead['category'],
      capPercentOfTuition:
        value.category === 'ANNUAL_DEVELOPMENT' && capValue ? Number(capValue) : undefined,
      active: existing?.active ?? true,
    };

    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.announcement.set('');

    const call =
      editor.kind === 'new-head'
        ? this.api.createHead(request)
        : this.api.updateHead(editor.id, request);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (head) => {
        this.saving.set(false);
        this.closeEditor();
        this.announcement.set(
          editor.kind === 'new-head' ? `${head.name} added.` : `${head.name} saved.`,
        );
        this.loadHeads();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.writeFailureCode.set(apiErrorCode(error));
        this.serverErrors.set(apiErrorDetails(error));
      },
    });
  }

  protected toggleHead(head: FeeHead): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.api
      .updateHead(head.id, {
        name: head.name,
        category: head.category,
        capPercentOfTuition: head.capPercentOfTuition,
        active: !head.active,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.announcement.set(
            head.active ? `${head.name} is no longer offered.` : `${head.name} is offered again.`,
          );
          this.loadHeads();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.writeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Concession type editor ───────────────────────────────────────────────────────────────

  protected startAddConcession(): void {
    this.openEditor({ kind: 'new-concession' });
    this.concessionForm.reset(
      { name: '', category: 'SIBLING', description: '', requiresApproval: true },
      { emitEvent: false },
    );
    this.focusAfterRender('#concession-name');
  }

  protected startEditConcession(type: FeeConcessionType): void {
    this.openEditor({ kind: 'concession', id: type.id });
    this.concessionForm.reset(
      {
        name: type.name,
        category: type.category,
        description: type.description ?? '',
        requiresApproval: type.requiresApproval,
      },
      { emitEvent: false },
    );
    this.focusAfterRender('#concession-name');
  }

  protected saveConcession(): void {
    if (this.saving()) {
      return;
    }
    const editor = this.editor();
    if (!editor || (editor.kind !== 'new-concession' && editor.kind !== 'concession')) {
      return;
    }
    this.attempted.set(true);
    this.concessionForm.markAllAsTouched();
    if (this.concessionForm.invalid) {
      return;
    }

    const value = this.concessionForm.getRawValue();
    const existing = editor.kind === 'concession' ? this.concessionById(editor.id) : null;
    const request = {
      name: value.name.trim(),
      category: value.category as FeeConcessionType['category'],
      description: value.description?.trim() || undefined,
      requiresApproval: value.requiresApproval,
      active: existing?.active ?? true,
    };

    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.announcement.set('');

    const call =
      editor.kind === 'new-concession'
        ? this.api.createConcessionType(request)
        : this.api.updateConcessionType(editor.id, request);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (type) => {
        this.saving.set(false);
        this.closeEditor();
        this.announcement.set(
          editor.kind === 'new-concession' ? `${type.name} added.` : `${type.name} saved.`,
        );
        this.loadConcessionTypes();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.writeFailureCode.set(apiErrorCode(error));
        this.serverErrors.set(apiErrorDetails(error));
      },
    });
  }

  protected toggleConcession(type: FeeConcessionType): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.writeFailureCode.set(null);
    this.api
      .updateConcessionType(type.id, {
        name: type.name,
        category: type.category,
        description: type.description,
        requiresApproval: type.requiresApproval,
        active: !type.active,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.announcement.set(
            type.active ? `${type.name} is no longer offered.` : `${type.name} is offered again.`,
          );
          this.loadConcessionTypes();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.writeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Shared editor plumbing ───────────────────────────────────────────────────────────────

  protected editingHead(id: string): boolean {
    const editor = this.editor();
    return editor?.kind === 'head' && editor.id === id;
  }

  protected editingConcession(id: string): boolean {
    const editor = this.editor();
    return editor?.kind === 'concession' && editor.id === id;
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    this.closeEditor();
  }

  private openEditor(editor: NonNullable<Editor>): void {
    this.attempted.set(false);
    this.serverErrors.set({});
    this.writeFailureCode.set(null);
    this.announcement.set('');
    this.editor.set(editor);
  }

  private closeEditor(): void {
    this.editor.set(null);
    this.attempted.set(false);
    this.serverErrors.set({});
  }

  private headById(id: string): FeeHead | undefined {
    return this.heads().find((head) => head.id === id);
  }

  private concessionById(id: string): FeeConcessionType | undefined {
    return this.concessionTypes().find((type) => type.id === id);
  }

  private loadHeads(): void {
    this.headsLoading.set(true);
    this.headsLoadFailureCode.set(null);
    this.api
      .heads()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (heads) => {
          this.heads.set(heads);
          this.headsLoading.set(false);
        },
        error: (error: unknown) => {
          this.heads.set([]);
          this.headsLoading.set(false);
          this.headsLoadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private loadConcessionTypes(): void {
    this.concessionsLoading.set(true);
    this.concessionsLoadFailureCode.set(null);
    this.api
      .concessionTypes()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (types) => {
          this.concessionTypes.set(types);
          this.concessionsLoading.set(false);
        },
        error: (error: unknown) => {
          this.concessionTypes.set([]);
          this.concessionsLoading.set(false);
          this.concessionsLoadFailureCode.set(apiErrorCode(error));
        },
      });
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
