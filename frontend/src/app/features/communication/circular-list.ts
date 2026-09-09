import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import {
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode } from '../../core/api/api-error';
import { CommunicationApi } from '../../core/api/communication-api';
import { CircularSummary, CircularTargetRequest, SchoolClass } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { BottomSheet } from '../../shared/components/bottom-sheet/bottom-sheet';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED, circularStatusLabel, circularStatusTone } from './communication-shared';

const WHOLE_CLASS = '';

/** The backend's own limit, restated so a value is refused before the round trip rather than after. */
const TITLE_MAX_LENGTH = 200;

/** One target row in the composer, with the class's own sections and the live preview count. */
interface TargetRow {
  readonly key: number;
  readonly classId: string;
  /** `WHOLE_CLASS` (empty string) means "every active section of `classId`". */
  readonly sectionId: string;
  readonly sectionOptions: readonly SelectOption[];
  readonly previewCount: number | null;
  readonly previewLoading: boolean;
}

/**
 * The circular list, and the composer for a new one (Phase 2, FR-097/FR-100).
 *
 * No client-side permission gate on the list itself (ADR-0008): `GET /api/communication/circulars`
 * enforces `communication:circular:read` on its own, and `forbidden()` renders the same way every
 * other screen's does if the call 403s. Composing is gated separately —
 * {@link CircularList#canManage} hides the "Compose" action and the publish buttons for a caller
 * who can read circulars but not write them.
 *
 * **There is no "edit a draft's targets" flow.** A circular is composed whole, with every target
 * chosen up front — the backend ships no endpoint to change a draft's targets afterwards, so a
 * school that wants a different audience discards the draft and composes another.
 */
@Component({
  selector: 'cb-circular-list',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    Badge,
    BottomSheet,
    Button,
    Card,
    Checkbox,
    FormField,
    Select,
    TextInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './circular-list.html',
  styleUrl: './circular-list.scss',
})
export class CircularList {
  private readonly academics = inject(AcademicsApi);
  private readonly api = inject(CommunicationApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly canManage = permitted(Permissions.COMMUNICATION_MANAGE);

  protected readonly circularStatusLabel = circularStatusLabel;
  protected readonly circularStatusTone = circularStatusTone;

  protected readonly loading = signal(true);
  protected readonly failureCode = signal<string | null>(null);
  protected readonly circulars = signal<readonly CircularSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);

  protected readonly publishingId = signal<string | null>(null);
  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );
  protected readonly hasPrevious = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());

  // ── The composer ─────────────────────────────────────────────────────────────────────────

  protected readonly composeOpen = signal(false);
  protected readonly composeForm = this.formBuilder.group({
    title: ['', [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH)]],
    body: ['', Validators.required],
    requiresAcknowledgement: [false],
  });
  protected readonly classOptions = signal<readonly SelectOption[]>([]);
  protected readonly ladderFailed = signal(false);
  protected readonly targetRows = signal<readonly TargetRow[]>([]);
  protected readonly composeSaving = signal(false);
  protected readonly composeFailureCode = signal<string | null>(null);

  /** True once "Save as draft" has been pressed: before that, only touched fields show a message. */
  private readonly composeAttempted = signal(false);
  /** Bumped on every change so the messages recompute; values come off the controls. */
  private readonly composeRevision = signal(0);

  /**
   * Only `title` and `body` are wired up here. Each target row's class/section is a plain
   * `ngModel`, not a validated control — there is no empty-but-required state to report because
   * `hasAtLeastOneTarget` already disables "Save as draft" and says why beside it.
   */
  protected readonly composeFieldErrors = computed<
    Readonly<{ title: string | null; body: string | null }>
  >(() => {
    this.composeRevision();
    this.composeAttempted();
    return {
      title: this.composeMessageFor('title'),
      body: this.composeMessageFor('body'),
    };
  });

  protected readonly hasAtLeastOneTarget = computed(() =>
    this.targetRows().some((row) => row.classId !== ''),
  );

  private schoolClasses: readonly SchoolClass[] = [];
  private nextRowKey = 0;
  private latestRequest = 0;

  constructor() {
    this.load();
    this.loadLadder();
    this.composeForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.composeRevision.update((count) => count + 1);
    });
  }

  protected reload(): void {
    this.load();
  }

  protected previousPage(): void {
    if (!this.hasPrevious()) {
      return;
    }
    this.page.update((current) => current - 1);
    this.load();
  }

  protected nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update((current) => current + 1);
    this.load();
  }

  protected openCompose(): void {
    this.composeFailureCode.set(null);
    this.composeAttempted.set(false);
    this.composeForm.reset({ title: '', body: '', requiresAcknowledgement: false });
    this.targetRows.set([]);
    this.addTarget();
    this.composeOpen.set(true);
  }

  protected closeCompose(): void {
    if (this.composeSaving()) {
      return;
    }
    this.composeOpen.set(false);
  }

  protected addTarget(): void {
    this.targetRows.update((rows) => [
      ...rows,
      {
        key: this.nextRowKey++,
        classId: '',
        sectionId: WHOLE_CLASS,
        sectionOptions: [{ value: WHOLE_CLASS, label: 'Whole class' }],
        previewCount: null,
        previewLoading: false,
      },
    ]);
  }

  protected removeTarget(key: number): void {
    this.targetRows.update((rows) => rows.filter((row) => row.key !== key));
  }

  protected setTargetClass(key: number, classId: string): void {
    const schoolClass = this.schoolClasses.find((candidate) => candidate.id === classId);
    const sectionOptions: SelectOption[] = [{ value: WHOLE_CLASS, label: 'Whole class' }];
    for (const section of schoolClass?.sections ?? []) {
      if (section.active) {
        sectionOptions.push({ value: section.id, label: section.name });
      }
    }
    this.targetRows.update((rows) =>
      rows.map((row) =>
        row.key === key
          ? { ...row, classId, sectionId: WHOLE_CLASS, sectionOptions, previewCount: null }
          : row,
      ),
    );
    this.loadPreview(key, classId, WHOLE_CLASS);
  }

  protected setTargetSection(key: number, sectionId: string): void {
    const row = this.targetRows().find((candidate) => candidate.key === key);
    if (!row) {
      return;
    }
    this.targetRows.update((rows) =>
      rows.map((candidate) => (candidate.key === key ? { ...candidate, sectionId } : candidate)),
    );
    this.loadPreview(key, row.classId, sectionId);
  }

  protected submitCompose(): void {
    if (this.composeSaving()) {
      return;
    }
    this.composeAttempted.set(true);
    this.composeForm.markAllAsTouched();
    this.composeRevision.update((count) => count + 1);
    if (this.composeForm.invalid || !this.hasAtLeastOneTarget()) {
      return;
    }

    const targets: CircularTargetRequest[] = this.targetRows()
      .filter((row) => row.classId !== '')
      .map((row) => ({
        classId: row.classId,
        sectionId: row.sectionId !== WHOLE_CLASS ? row.sectionId : undefined,
      }));
    const value = this.composeForm.getRawValue();

    this.composeSaving.set(true);
    this.composeFailureCode.set(null);
    this.api
      .create({
        title: value.title.trim(),
        body: value.body.trim(),
        requiresAcknowledgement: value.requiresAcknowledgement,
        targets,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.composeSaving.set(false);
          this.composeOpen.set(false);
          this.announcement.set('The circular was saved as a draft.');
          this.page.set(0);
          this.load();
        },
        error: (error: unknown) => {
          this.composeSaving.set(false);
          this.composeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  protected publish(circularId: string): void {
    if (this.publishingId()) {
      return;
    }
    this.publishingId.set(circularId);
    this.api
      .publish(circularId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.publishingId.set(null);
          this.announcement.set(
            `"${detail.title}" was published to ${detail.recipientCount} student(s).`,
          );
          this.load();
        },
        error: (error: unknown) => {
          this.publishingId.set(null);
          this.announcement.set(`Could not publish this circular (${apiErrorCode(error)}).`);
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
          this.schoolClasses = classes;
          const options: SelectOption[] = [{ value: '', label: 'Choose a class' }];
          for (const schoolClass of classes) {
            if (schoolClass.active) {
              options.push({ value: schoolClass.id, label: schoolClass.name });
            }
          }
          this.classOptions.set(options);
          this.ladderFailed.set(false);
        },
        error: () => {
          this.classOptions.set([{ value: '', label: 'Choose a class' }]);
          this.ladderFailed.set(true);
        },
      });
  }

  private loadPreview(key: number, classId: string, sectionId: string): void {
    if (!classId) {
      return;
    }
    this.targetRows.update((rows) =>
      rows.map((row) => (row.key === key ? { ...row, previewLoading: true } : row)),
    );
    this.api
      .targetPreview(classId, sectionId !== WHOLE_CLASS ? sectionId : undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.targetRows.update((rows) =>
            rows.map((row) =>
              row.key === key ? { ...row, previewCount: result.count, previewLoading: false } : row,
            ),
          );
        },
        error: () => {
          this.targetRows.update((rows) =>
            rows.map((row) => (row.key === key ? { ...row, previewLoading: false } : row)),
          );
        },
      });
  }

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .list(this.page())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.circulars.set(result.content);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.circulars.set([]);
          this.totalPages.set(0);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private composeMessageFor(name: 'title' | 'body'): string | null {
    const control = this.composeForm.controls[name];
    if (!control.touched && !this.composeAttempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return name === 'title'
        ? 'Give this circular a title.'
        : 'Write the circular before saving it.';
    }
    if (name === 'title' && control.hasError('maxlength')) {
      return `A title is ${TITLE_MAX_LENGTH} characters or fewer.`;
    }
    return null;
  }
}
