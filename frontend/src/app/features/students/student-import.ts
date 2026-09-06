import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
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
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AcademicsApi } from '../../core/api/academics-api';
import { apiErrorCode, apiErrorStatus } from '../../core/api/api-error';
import { AcademicSession, ImportError, ImportReport } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { ACCESS_DENIED } from './students-shared';

/**
 * The columns the file must have, in the order the template writes them.
 *
 * The header is required and matched case-insensitively, and the order in the uploaded file does
 * not matter — this order is only what the downloadable template offers, because a school reading
 * left to right expects identity, then the child, then where they sit.
 */
export const IMPORT_COLUMNS = [
  'admission_number',
  'full_name',
  'date_of_birth',
  'gender',
  'status',
  'admitted_on',
  'class',
  'section',
  'roll_number',
] as const;

/** The three the backend fills in for itself when they are absent. */
const OPTIONAL_COLUMNS: ReadonlySet<string> = new Set(['status', 'admitted_on', 'roll_number']);

/** What each column wants, said in one line, for the reference table above the file picker. */
const COLUMN_NOTES: Readonly<Record<string, string>> = {
  admission_number: "The school's own number for the child. Must not already be in use.",
  full_name: 'The whole name, as the school writes it.',
  date_of_birth: 'yyyy-mm-dd, for example 2015-08-24.',
  gender: 'Male, Female or Other.',
  status: 'Active unless you say otherwise.',
  admitted_on: 'yyyy-mm-dd. The day the child joined.',
  class: 'The name of a class you have already set up, for example Class 5.',
  section: 'The name of a section in that class, for example A.',
  roll_number: 'Only if the school has already given one out.',
};

/** The file offered by "Download the template". Header row only — see `templateCsv`. */
const TEMPLATE_FILENAME = 'chalkbase-student-import-template.csv';

/** The file offered by "Download this list". Row numbers and column names, never a cell value. */
const PROBLEMS_FILENAME = 'chalkbase-import-problems.csv';

/** How the list of problems is arranged. Two questions, two orders — see `errorGroups`. */
type Grouping = 'row' | 'column';

/** One heading in the problem list, with the problems under it already in the right order. */
interface ErrorGroup {
  readonly key: string;
  readonly heading: string;
  readonly caption: string;
  readonly items: readonly ImportError[];
}

/** One line of the column reference. */
interface ColumnNote {
  readonly name: string;
  readonly required: boolean;
  readonly note: string;
}

/**
 * Bulk student import (ADR-0021).
 *
 * ## The flow is the design
 *
 * Choose a year and a file, **check the file**, read every problem, then import. The check is a
 * step and not a checkbox, because ADR-0021 §1 made it a separate endpoint that writes nothing:
 * one flag with a default is what writes six hundred unread rows, and there is no flag to get
 * wrong here. Import is unreachable until a check has come back clean **for the file that is
 * chosen right now** — pick a different file, or a different year, and the previous answer is
 * discarded rather than left to authorise an upload it never saw.
 *
 * ## Import is offered only when there is nothing wrong, and that is not fussiness
 *
 * The commit is all-or-nothing (ADR-0021 §2). With one bad row, pressing Import is guaranteed to
 * import nothing, so the button is disabled and the sentence beside it says why. Offering it would
 * be offering a failure.
 *
 * ## Every problem, in one list
 *
 * A school with a six-hundred-row file and twenty-seven problems has to fix all twenty-seven in
 * one pass. Showing the first one, or making them scroll a table sideways on a laptop, sends them
 * round the loop twenty-seven times, and that is the "poor import tooling" risk the roadmap names.
 * So the list is complete, ordered by row so it can be worked top to bottom against the
 * spreadsheet, and re-groupable by column for the case where one column is wrong two hundred times
 * because the whole sheet is in dd/mm/yyyy.
 *
 * ## What this screen knows about the file: its name and its size
 *
 * Nothing else, ever. The file holds hundreds of children's names and dates of birth — Confidential
 * under ADR-0014 — so it is never read here, never previewed, never put in a signal, never in the
 * URL and never in browser storage. It is handed to `FormData` from the `<input>` the user chose it
 * with and that is the whole of this app's involvement. The server does not store it either
 * (ADR-0021 §6), and the problems that come back name a row and a column and never quote a cell,
 * which is what makes the downloadable problem list safe to write.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008 warns against re-deriving authorization in the client, so there is no `canActivate`
 * checking `student:student:manage`. The server leaves the menu item out for anyone without it and
 * both endpoints enforce it independently; typing the URL lands here and gets a 403 this screen
 * explains calmly, exactly as the student list does.
 */
@Component({
  selector: 'cb-student-import',
  imports: [NgTemplateOutlet, ReactiveFormsModule, RouterLink, Button, FormField, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-import.html',
  styleUrl: './student-import.scss',
})
export class StudentImport {
  private readonly students = inject(StudentsApi);
  private readonly academics = inject(AcademicsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  protected readonly form = this.formBuilder.group({ sessionId: '' });

  protected readonly sessionsLoading = signal(true);
  protected readonly sessionsFailureCode = signal<string | null>(null);
  protected readonly sessions = signal<readonly AcademicSession[]>([]);

  /** The chosen file's name and size. **The only two things this screen ever learns about it.** */
  protected readonly fileName = signal<string | null>(null);
  protected readonly fileSize = signal<string | null>(null);

  protected readonly checking = signal(false);
  protected readonly checkFailureCode = signal<string | null>(null);
  protected readonly checkFailureStatus = signal(0);
  /** What `validate` answered about the file that is chosen right now, or null. */
  protected readonly report = signal<ImportReport | null>(null);

  protected readonly importing = signal(false);
  protected readonly importFailureCode = signal<string | null>(null);
  protected readonly importFailureStatus = signal(0);
  /** Set only once rows have actually been written. Ends the flow. */
  protected readonly imported = signal<ImportReport | null>(null);
  /** The year they landed in, remembered at the moment of the write rather than read back off a
   * control the user is free to change afterwards. */
  protected readonly importedInto = signal('');

  protected readonly grouping = signal<Grouping>('row');

  /**
   * The exact file the last clean check was about.
   *
   * A handle, not contents — the same object the `<input>` is already holding, never read. It
   * exists so that `runImport` can refuse a file the server has not seen, by identity rather than
   * by trusting that a `change` event fired. ADR-0021 §1 is only worth anything if the check
   * cannot be skipped, and "we cleared a flag when we think the file changed" is not that.
   */
  private validatedFile: File | null = null;

  /** "27 problems", "1 row". A function the template calls, because the counts it pluralises
   * come from four different places and a pipe for each would be four more files. */
  protected readonly count = count;

  protected readonly columnNotes: readonly ColumnNote[] = IMPORT_COLUMNS.map((name) => ({
    name,
    required: !OPTIONAL_COLUMNS.has(name),
    note: COLUMN_NOTES[name],
  }));

  protected readonly sessionOptions = computed<readonly SelectOption[]>(() =>
    this.sessions().map((session) => ({
      value: session.id,
      label: session.current ? `${session.name} (current)` : session.name,
    })),
  );

  protected readonly sessionsForbidden = computed(
    () => this.sessionsFailureCode() === ACCESS_DENIED,
  );
  protected readonly sessionsFailed = computed(
    () => this.sessionsFailureCode() !== null && !this.sessionsForbidden(),
  );
  /** No year, nothing to import into. ADR-0021 says the school sets its year up first. */
  protected readonly noSessions = computed(
    () =>
      !this.sessionsLoading() &&
      this.sessionsFailureCode() === null &&
      this.sessions().length === 0,
  );

  /** A 403 from either endpoint. The screen has nothing else to offer, so it says so and stops. */
  protected readonly forbidden = computed(
    () => this.checkFailureCode() === ACCESS_DENIED || this.importFailureCode() === ACCESS_DENIED,
  );

  /** The container refused the upload before it reached a controller. See `apiErrorStatus`. */
  protected readonly checkTooLarge = computed(() => this.checkFailureStatus() === 413);
  protected readonly importTooLarge = computed(() => this.importFailureStatus() === 413);

  protected readonly checkFailed = computed(
    () => this.checkFailureCode() !== null && !this.forbidden() && !this.checkTooLarge(),
  );
  protected readonly importFailed = computed(
    () => this.importFailureCode() !== null && !this.forbidden() && !this.importTooLarge(),
  );

  protected readonly hasFile = computed(() => this.fileName() !== null);
  protected readonly busy = computed(() => this.checking() || this.importing());

  protected readonly canCheck = computed(
    () => this.hasFile() && this.form.controls.sessionId.value !== '' && !this.busy(),
  );

  /**
   * How many problems the file has — the true total, not the length of the list we were sent.
   *
   * The server caps `errors` at 200 so a pathological file cannot make the response unusable.
   * Counting the array instead would tell a school with 1,800 problems that it has 200, and send
   * them round the fix-and-re-upload loop believing they were nearly done.
   */
  protected readonly errorCount = computed(() => this.report()?.errorCount ?? 0);

  /** True when the server had more problems than it was willing to send. */
  protected readonly errorsTruncated = computed(() => {
    const report = this.report();
    return report !== null && report.errorCount > report.errors.length;
  });

  /** How many of them are actually on the page, for the "showing N of M" line. */
  protected readonly errorsShown = computed(() => this.report()?.errors.length ?? 0);

  /** How many spreadsheet rows are involved — "27 problems in 19 rows" is the useful sentence. */
  protected readonly affectedRowCount = computed(
    () => new Set((this.report()?.errors ?? []).map((error) => error.row)).size,
  );

  /** A file that would import: checked, nothing wrong with it, and it has rows in it. */
  protected readonly clean = computed(() => {
    const report = this.report();
    return report !== null && report.errorCount === 0 && report.totalRows > 0;
  });

  /** A header row and nothing under it. Not an error, and not something to import either. */
  protected readonly emptyFile = computed(() => {
    const report = this.report();
    return report !== null && report.totalRows === 0 && report.errorCount === 0;
  });

  protected readonly canImport = computed(() => this.clean() && !this.busy());

  /**
   * Every problem, under a heading, in the order somebody fixing the file would want them.
   *
   * By row is the default because that is how a spreadsheet is read: open it, go down the rows,
   * fix each one, save. By column answers the other question — one column wrong two hundred times
   * is one mistake (the sheet is in dd/mm/yyyy, or the class names say "Class V"), and those
   * groups are ordered by size so the systemic one is first.
   */
  protected readonly errorGroups = computed<readonly ErrorGroup[]>(() => {
    const errors = this.report()?.errors ?? [];
    if (errors.length === 0) {
      return [];
    }
    return this.grouping() === 'row' ? groupByRow(errors) : groupByColumn(errors);
  });

  constructor() {
    this.loadSessions();

    // A different year can turn a clean file into a failing one — the classes it names might not
    // run that year, and an admission number free in one year is not free in another. So a check
    // belongs to the year it was made under, and changing the year throws it away.
    this.form.controls.sessionId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.discardResult());
  }

  // ── Choosing ─────────────────────────────────────────────────────────────────────────────

  /**
   * Takes the name and the size off the chosen file and nothing else.
   *
   * Not `readAsText`, not a preview, not a row count worked out in the browser. Everything past
   * the name is Confidential (ADR-0014), and the server is the thing that parses it.
   */
  protected chooseFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.fileName.set(file?.name ?? null);
    this.fileSize.set(file ? formatSize(file.size) : null);
    // Whatever was true of the last file is not true of this one.
    this.discardResult();
  }

  // ── Step two: check ──────────────────────────────────────────────────────────────────────

  protected check(): void {
    const file = this.currentFile();
    const sessionId = this.form.controls.sessionId.value;
    if (!file || !sessionId || this.busy()) {
      return;
    }

    this.checking.set(true);
    this.discardResult();

    this.students
      .validateImport(file, sessionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (report) => {
          this.checking.set(false);
          this.report.set(report);
          // Remembered by identity, so nothing but this exact file can be imported on the strength
          // of this answer.
          this.validatedFile = report.errorCount === 0 ? file : null;
          this.focusResult();
        },
        error: (error: unknown) => {
          this.checking.set(false);
          this.checkFailureCode.set(apiErrorCode(error));
          this.checkFailureStatus.set(apiErrorStatus(error));
          this.focusResult();
        },
      });
  }

  // ── Step four: import ────────────────────────────────────────────────────────────────────

  /**
   * Commits the file — and refuses if anything about it has moved since the check.
   *
   * The guard is identity, not a boolean: `canImport` is what greys the button out, and this is
   * what makes the rule true rather than merely displayed.
   */
  protected runImport(): void {
    const file = this.currentFile();
    const sessionId = this.form.controls.sessionId.value;
    if (!this.canImport() || !sessionId || !file || file !== this.validatedFile) {
      // The choice moved out from under a result that is no longer about it. Start again.
      this.discardResult();
      return;
    }

    this.importing.set(true);
    this.importFailureCode.set(null);
    this.importFailureStatus.set(0);

    this.students
      .runImport(file, sessionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.importing.set(false);
          this.importedInto.set(
            this.sessions().find((session) => session.id === sessionId)?.name ?? '',
          );
          this.imported.set(result);
          this.focusResult();
        },
        error: (error: unknown) => {
          this.importing.set(false);
          this.importFailureCode.set(apiErrorCode(error));
          this.importFailureStatus.set(apiErrorStatus(error));
          // Nothing was written — that is what all-or-nothing buys — but the file may no longer be
          // importable for a reason the check did not see, so it goes back through the check.
          this.report.set(null);
          this.validatedFile = null;
          this.focusResult();
        },
      });
  }

  /** Back to an empty flow, for the school importing last year's leavers next. */
  protected startAgain(): void {
    this.imported.set(null);
    this.fileName.set(null);
    this.fileSize.set(null);
    this.discardResult();
    // The `<input>` keeps its file otherwise, and the screen would say "no file chosen" beside a
    // control that still holds one.
    afterNextRender(
      () => {
        const input = this.fileInput()?.nativeElement;
        if (input) {
          input.value = '';
          input.focus();
        }
      },
      { injector: this.injector },
    );
  }

  protected setGrouping(grouping: Grouping): void {
    this.grouping.set(grouping);
  }

  protected reloadSessions(): void {
    this.loadSessions();
  }

  // ── Downloads ────────────────────────────────────────────────────────────────────────────

  /**
   * A CSV with the header row and nothing under it.
   *
   * Written here from `IMPORT_COLUMNS` rather than fetched, because the commonest way an import
   * fails is a column called "DOB" or "Name of student", and a file that already has the right
   * header cannot make that mistake. No example row: a sample child left in the sheet would be
   * imported as a real one, and a fixture of a fake child is still a child's row in a school's
   * register.
   */
  protected downloadTemplate(): void {
    this.download(TEMPLATE_FILENAME, `${IMPORT_COLUMNS.join(',')}\r\n`);
  }

  /**
   * The problem list as a file, to read beside the spreadsheet.
   *
   * Safe to write precisely because of ADR-0021 §6: a problem is a row number, a column name and a
   * sentence, and never the cell. There is nothing in this file that identifies a child to anybody
   * who does not already have the spreadsheet it describes.
   */
  protected downloadProblems(): void {
    const rows = (this.report()?.errors ?? []).map(
      (error) => `${error.row},${csvCell(error.column)},${csvCell(error.message)}`,
    );
    this.download(PROBLEMS_FILENAME, [`row,column,problem`, ...rows].join('\r\n') + '\r\n');
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  /**
   * The file as the `<input>` holds it now.
   *
   * Read from the DOM at the moment of the request rather than kept in a signal, so there is one
   * source of truth for what is chosen and no way for this app to be holding a file the user
   * thinks they replaced.
   */
  private currentFile(): File | null {
    return this.fileInput()?.nativeElement.files?.[0] ?? null;
  }

  /** Everything that was only true of a particular file, under a particular year. */
  private discardResult(): void {
    this.report.set(null);
    this.validatedFile = null;
    this.checkFailureCode.set(null);
    this.checkFailureStatus.set(0);
    this.importFailureCode.set(null);
    this.importFailureStatus.set(0);
  }

  private loadSessions(): void {
    this.sessionsLoading.set(true);
    this.sessionsFailureCode.set(null);

    this.academics
      .sessions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          this.sessions.set(sessions);
          this.sessionsLoading.set(false);
          // The current year is what a school importing its register means, so it is chosen — but
          // it is a select and not a fixed label, because the other real case is setting next year
          // up in March.
          const current = sessions.find((session) => session.current) ?? sessions[0];
          if (current && this.form.controls.sessionId.value === '') {
            this.form.controls.sessionId.setValue(current.id);
          }
        },
        error: (error: unknown) => {
          this.sessions.set([]);
          this.sessionsLoading.set(false);
          this.sessionsFailureCode.set(apiErrorCode(error));
        },
      });
  }

  /**
   * Puts focus on the answer once it has painted.
   *
   * The result appears below the button that was pressed, which a screen-reader user and a
   * keyboard user would otherwise have to go and find. The app is zoneless, so the element does
   * not exist until after the next render.
   */
  private focusResult(): void {
    afterNextRender(
      () => this.host.nativeElement.querySelector<HTMLElement>('#import-result')?.focus(),
      { injector: this.injector },
    );
  }

  /**
   * Hands the browser a file this app just made.
   *
   * An object URL rather than a `data:` URI: a data URI puts the whole payload in an attribute,
   * where a long problem list would be a very large string sitting in the DOM. Revoked on the next
   * tick, once the browser has taken it.
   */
  private download(filename: string, csv: string): void {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = this.document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

/** Ascending, because that is the direction a spreadsheet is fixed in. */
function groupByRow(errors: readonly ImportError[]): readonly ErrorGroup[] {
  const byRow = new Map<number, ImportError[]>();
  for (const error of errors) {
    const bucket = byRow.get(error.row);
    if (bucket) {
      bucket.push(error);
    } else {
      byRow.set(error.row, [error]);
    }
  }
  return [...byRow.entries()]
    .sort(([a], [b]) => a - b)
    .map(([row, items]) => ({
      key: `row-${row}`,
      heading: `Row ${row}`,
      caption: count(items.length, 'problem'),
      items: [...items].sort((a, b) => a.column.localeCompare(b.column)),
    }));
}

/** Biggest group first: one column wrong two hundred times is one mistake, and it is the headline. */
function groupByColumn(errors: readonly ImportError[]): readonly ErrorGroup[] {
  const byColumn = new Map<string, ImportError[]>();
  for (const error of errors) {
    const bucket = byColumn.get(error.column);
    if (bucket) {
      bucket.push(error);
    } else {
      byColumn.set(error.column, [error]);
    }
  }
  return [...byColumn.entries()]
    .sort(([nameA, a], [nameB, b]) => b.length - a.length || nameA.localeCompare(nameB))
    .map(([column, items]) => ({
      key: `column-${column}`,
      heading: column,
      caption: count(new Set(items.map((item) => item.row)).size, 'row'),
      items: [...items].sort((a, b) => a.row - b.row),
    }));
}

/** "1 problem", "27 problems". */
export function count(n: number, noun: string): string {
  return `${n.toLocaleString('en-IN')} ${noun}${n === 1 ? '' : 's'}`;
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

/**
 * One CSV cell, quoted if it has to be.
 *
 * A backend message is a sentence and sentences contain commas. Nothing here is a cell value from
 * the uploaded file — see `downloadProblems` — but it is still text this app did not write.
 */
function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}
