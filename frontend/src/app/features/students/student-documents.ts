import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { apiErrorCode, apiErrorStatus } from '../../core/api/api-error';
import { DocumentsApi } from '../../core/api/documents-api';
import { DocumentSummary, DocumentType, VerificationStatus } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Badge, BadgeTone } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Card } from '../../shared/components/card/card';
import { Dialog } from '../../shared/components/dialog/dialog';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  DOCUMENT_TYPE_LABELS,
  DOCUMENT_TYPE_OPTIONS,
  VERIFICATION_STATUS_LABELS,
  VERIFICATION_STATUS_OPTIONS,
  VERIFICATION_STATUS_TONES,
  labelFor,
} from './students-shared';

const UNSUPPORTED_FILE_TYPE = 'DOC_001';
const FILE_EMPTY = 'DOC_002';
const FILE_UNREADABLE = 'DOC_003';
const EXPIRY_BEFORE_ISSUE = 'DOC_005';

/** The day a document was uploaded, said the way the office reads a date. Local, like `formatDay`. */
const UPLOADED_ON = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

/** One row, with everything the template needs already decided. */
interface DocumentRow {
  readonly id: string;
  readonly documentType: DocumentType;
  readonly documentTypeLabel: string;
  readonly verificationStatus: VerificationStatus;
  readonly verificationStatusLabel: string;
  readonly verificationStatusTone: BadgeTone;
  readonly originalFilename: string;
  readonly issueDate: string | null;
  readonly expiryDate: string | null;
  readonly uploadedOn: string;
  readonly sizeLabel: string;
  /** `GET /api/documents/{id}/content` — see `DocumentsApi.contentUrl` for why a plain `<a>`. */
  readonly contentUrl: string;
  readonly editButtonId: string;
}

/**
 * A student's certificates, photo, signature and other documents (FR-013, FR-032, ADR-0025).
 *
 * ## Its own fetch, not a slice of the student record
 *
 * `GET /api/students/{id}` does not carry documents — they are a different module, behind
 * `/api/documents?studentId=`, with their own permission (`document:document:*`) separate from
 * `student:student:*`. So unlike `cb-student-contact` or `cb-student-medical`, this section loads
 * itself from `studentId` alone rather than receiving its slice of an already-fetched record, and a
 * write here never asks `cb-student-detail` to re-read the student — nothing in the student payload
 * would have changed.
 *
 * ## Confidential (ADR-0014), and never in a title, a route or a log
 *
 * A document's type and its original filename — chosen by whoever scanned it, and free to contain
 * a child's name — can be enough on their own to identify a child. Neither ever reaches this app's
 * console, and downloading one goes through `GET /api/documents/{id}/content` (proxied, permission-
 * checked and audited as an export on every request, ADR-0025) rather than any URL the object store
 * itself would answer to.
 *
 * ## Upload has states a form does not
 *
 * A file can still be travelling (`uploading`, with `uploadPercent`), refused by the container for
 * its size before this module ever saw it (`uploadTooLarge`, a bare `413` — see `apiErrorStatus`),
 * or refused by `DocumentService` for what it is (`uploadFailure`, one sentence per `DOC_00x` code)
 * — three states a plain "saving…" spinner does not distinguish and a user cannot act on if they
 * are folded into one.
 *
 * `uploadPercent` stays 0 for the whole request on this deployment: `app.config.ts` runs
 * `HttpClient` on the Fetch backend (`provideHttpClient(withFetch())`), which — per Angular's own
 * `HttpUploadProgressEvent` documentation — does not report upload progress at all, only download.
 * The template turns that into an indeterminate bar rather than one frozen at "0%"; a real
 * percentage would appear on its own if a future change moves this app off `withFetch()`, with
 * nothing here needing to change.
 */
@Component({
  selector: 'cb-student-documents',
  imports: [ReactiveFormsModule, Badge, Button, Card, Dialog, FormField, Select, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-documents.html',
  styleUrl: './student-documents.scss',
})
export class StudentDocuments {
  readonly studentId = input.required<string>();

  /**
   * Said once, to the live region `cb-student-detail` already shows above every section. A write
   * here never asks the parent to re-read the student (unlike `cb-student-contact` and its
   * siblings) — `GET /api/students/{id}` does not carry documents, so nothing in that payload
   * would have changed.
   */
  readonly changed = output<string>();

  private readonly documents = inject(DocumentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  /** Whether this user may upload, edit, verify or delete. `document:document:manage` on the wire. */
  protected readonly canManage = permitted(Permissions.DOCUMENT_MANAGE);

  protected readonly documentTypeOptions = DOCUMENT_TYPE_OPTIONS;
  protected readonly verificationStatusOptions = VERIFICATION_STATUS_OPTIONS;

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly DocumentSummary[]>([]);

  // ── Uploading ────────────────────────────────────────────────────────────────────────────

  protected readonly adding = signal(false);
  protected readonly fileName = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly uploadPercent = signal(0);
  protected readonly uploadFailureCode = signal<string | null>(null);
  protected readonly uploadFailureStatus = signal(0);

  protected readonly uploadForm = this.formBuilder.group({
    documentType: ['', Validators.required],
    issueDate: '',
    expiryDate: '',
  });

  // ── Editing metadata and verification ───────────────────────────────────────────────────────

  protected readonly editingId = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);

  protected readonly editForm = this.formBuilder.group({
    documentType: ['', Validators.required],
    issueDate: '',
    expiryDate: '',
    verificationStatus: ['', Validators.required],
  });

  // ── Deleting ─────────────────────────────────────────────────────────────────────────────

  /** The row waiting on a confirmed removal, or null. */
  protected readonly removing = signal<DocumentRow | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteFailureCode = signal<string | null>(null);

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly view = computed<readonly DocumentRow[]>(() =>
    this.rows().map((document) => ({
      id: document.id,
      documentType: document.documentType,
      documentTypeLabel: labelFor(DOCUMENT_TYPE_LABELS, document.documentType),
      verificationStatus: document.verificationStatus,
      verificationStatusLabel: labelFor(VERIFICATION_STATUS_LABELS, document.verificationStatus),
      verificationStatusTone: VERIFICATION_STATUS_TONES[document.verificationStatus],
      originalFilename: document.originalFilename,
      issueDate: document.issueDate ?? null,
      expiryDate: document.expiryDate ?? null,
      uploadedOn: formatUploadedOn(document.createdAt),
      sizeLabel: formatSize(document.sizeBytes),
      contentUrl: this.documents.contentUrl(document.id),
      editButtonId: `document-edit-${document.id}`,
    })),
  );

  /** The row the edit panel is open on, or null — read off `view()` so the panel always shows the
   * same labels the list does. */
  protected readonly editingRow = computed(() => {
    const id = this.editingId();
    return id ? (this.view().find((row) => row.id === id) ?? null) : null;
  });

  /** A file chosen and not mid-upload — the two things a click needs before it means anything. */
  protected readonly canSubmitUpload = computed(
    () => this.fileName() !== null && !this.uploading(),
  );

  /** The container refused the upload before it reached `DocumentController`. See `apiErrorStatus`. */
  protected readonly uploadTooLarge = computed(() => this.uploadFailureStatus() === 413);

  protected readonly uploadFailure = computed(() => {
    if (this.uploadTooLarge()) {
      return null;
    }
    switch (this.uploadFailureCode()) {
      case null:
        return null;
      case UNSUPPORTED_FILE_TYPE:
        return 'That file is not a PDF, JPEG or PNG. Choose one of those.';
      case FILE_EMPTY:
        return 'That file is empty.';
      case FILE_UNREADABLE:
        return 'That file could not be read. Check your connection and try again.';
      case EXPIRY_BEFORE_ISSUE:
        return 'The expiry date must be after the issue date.';
      case ACCESS_DENIED:
        return 'You do not have permission to upload documents.';
      default:
        return 'Could not upload this document. Nothing has been changed.';
    }
  });

  protected readonly saveFailure = computed(() => {
    switch (this.saveFailureCode()) {
      case null:
        return null;
      case EXPIRY_BEFORE_ISSUE:
        return 'The expiry date must be after the issue date.';
      case ACCESS_DENIED:
        return 'You do not have permission to change this document.';
      default:
        return 'Could not save these changes. Nothing has been changed.';
    }
  });

  protected readonly deleteFailure = computed(() => {
    switch (this.deleteFailureCode()) {
      case null:
        return null;
      case ACCESS_DENIED:
        return 'You do not have permission to delete documents.';
      default:
        return 'Could not delete this document. Check your connection and try again.';
    }
  });

  constructor() {
    // Re-reads when the route id changes, which happens when somebody follows a link from one
    // record to another without this component being torn down.
    effect(() => {
      const studentId = this.studentId();
      this.closeAdd();
      this.editingId.set(null);
      this.removing.set(null);
      this.load(studentId);
    });
  }

  protected reload(): void {
    this.load(this.studentId());
  }

  // ── Uploading ────────────────────────────────────────────────────────────────────────────

  protected startAdd(): void {
    this.uploadFailureCode.set(null);
    this.uploadFailureStatus.set(0);
    this.uploadForm.reset(
      { documentType: '', issueDate: '', expiryDate: '' },
      { emitEvent: false },
    );
    this.fileName.set(null);
    this.adding.set(true);
    this.focusAfterRender('#document-type');
  }

  protected cancelAdd(): void {
    if (this.uploading()) {
      return;
    }
    this.closeAdd();
    this.focusAfterRender('#document-add');
  }

  /**
   * Takes the name off the chosen file and nothing else — the same rule `cb-student-import` follows
   * for the same reason (ADR-0014): the bytes are a scanned certificate or a child's photo, and
   * this screen has no business reading them before `FormData` carries them to the server.
   */
  protected chooseFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.fileName.set(file?.name ?? null);
  }

  protected submitUpload(): void {
    if (this.uploading()) {
      return;
    }
    this.uploadForm.markAllAsTouched();
    if (this.uploadForm.invalid) {
      return;
    }
    const file = this.fileInput()?.nativeElement.files?.[0] ?? null;
    if (!file) {
      return;
    }

    const value = this.uploadForm.getRawValue();
    this.uploading.set(true);
    this.uploadPercent.set(0);
    this.uploadFailureCode.set(null);
    this.uploadFailureStatus.set(0);

    this.documents
      .upload({
        studentId: this.studentId(),
        documentType: value.documentType as DocumentType,
        issueDate: value.issueDate || undefined,
        expiryDate: value.expiryDate || undefined,
        file,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.kind === 'progress') {
            this.uploadPercent.set(event.percent);
            return;
          }
          this.uploading.set(false);
          this.closeAdd();
          // Newest first, matching `DocumentService.list`'s own ordering — prepended rather than
          // re-fetched, so the row appears without a second round trip.
          this.rows.set([event.document, ...this.rows()]);
          this.changed.emit(`${event.document.originalFilename} was uploaded.`);
          this.focusAfterRender('#document-add');
        },
        error: (error: unknown) => {
          this.uploading.set(false);
          this.uploadFailureCode.set(apiErrorCode(error));
          this.uploadFailureStatus.set(apiErrorStatus(error));
        },
      });
  }

  // ── Editing metadata and verification ───────────────────────────────────────────────────────

  protected startEdit(row: DocumentRow): void {
    this.saveFailureCode.set(null);
    this.editForm.reset(
      {
        documentType: row.documentType,
        issueDate: row.issueDate ?? '',
        expiryDate: row.expiryDate ?? '',
        verificationStatus: row.verificationStatus,
      },
      { emitEvent: false },
    );
    this.editingId.set(row.id);
    this.focusAfterRender(`#document-edit-type-${row.id}`);
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    const id = this.editingId();
    this.editingId.set(null);
    this.focusAfterRender(id ? `#document-edit-${id}` : '#document-add');
  }

  protected saveEdit(): void {
    const id = this.editingId();
    if (this.saving() || !id) {
      return;
    }
    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) {
      return;
    }
    const value = this.editForm.getRawValue();

    this.saving.set(true);
    this.saveFailureCode.set(null);

    this.documents
      .update(id, {
        documentType: value.documentType as DocumentType,
        issueDate: value.issueDate || undefined,
        expiryDate: value.expiryDate || undefined,
        verificationStatus: value.verificationStatus as VerificationStatus,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (document) => {
          this.saving.set(false);
          this.editingId.set(null);
          this.rows.set(this.rows().map((row) => (row.id === document.id ? document : row)));
          this.changed.emit(`${document.originalFilename} was saved.`);
          this.focusAfterRender(`#document-edit-${document.id}`);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Deleting ─────────────────────────────────────────────────────────────────────────────

  protected askToRemove(row: DocumentRow): void {
    this.deleteFailureCode.set(null);
    this.removing.set(row);
  }

  protected cancelRemove(): void {
    if (this.deleting()) {
      return;
    }
    const target = this.removing();
    this.removing.set(null);
    this.focusAfterRender(target ? `#document-edit-${target.id}` : '#document-add');
  }

  protected confirmRemove(): void {
    const target = this.removing();
    if (this.deleting() || !target) {
      return;
    }
    this.deleting.set(true);
    this.deleteFailureCode.set(null);

    this.documents
      .delete(target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deleting.set(false);
          this.removing.set(null);
          this.rows.set(this.rows().filter((row) => row.id !== target.id));
          this.changed.emit(`${target.originalFilename} was deleted.`);
          this.focusAfterRender('#document-add');
        },
        error: (error: unknown) => {
          this.deleting.set(false);
          this.deleteFailureCode.set(apiErrorCode(error));
          this.removing.set(null);
        },
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(studentId: string): void {
    this.loading.set(true);
    this.failureCode.set(null);

    this.documents
      .list(studentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (documents) => {
          this.rows.set(documents);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.rows.set([]);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private closeAdd(): void {
    this.adding.set(false);
    this.uploading.set(false);
    this.uploadPercent.set(0);
    this.uploadFailureCode.set(null);
    this.uploadFailureStatus.set(0);
    this.fileName.set(null);
    const input = this.fileInput()?.nativeElement;
    if (input) {
      input.value = '';
    }
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}

/** A timestamp the backend sent that this app cannot parse is shown as it arrived, not as junk. */
function formatUploadedOn(iso: string): string {
  const parsed = new Date(iso);
  return Number.isNaN(parsed.getTime()) ? iso : UPLOADED_ON.format(parsed);
}

/** A file size a person can compare to "the limit", without false precision. */
function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} bytes`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${kb < 10 ? kb.toFixed(1) : Math.round(kb)} KB`;
  }
  return `${(kb / 1024).toFixed(1)} MB`;
}
