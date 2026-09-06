import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AcademicSession, ImportError, ImportReport } from '../../core/api/models';
import { StudentImport } from './student-import';

const SESSIONS = '/api/academics/sessions';
const VALIDATE = '/api/students/import/validate';
const IMPORT = '/api/students/import';

const sessions: AcademicSession[] = [
  { id: 's-2026', name: '2026–27', startsOn: '2026-04-01', endsOn: '2027-03-31', current: true },
  { id: 's-2025', name: '2025–26', startsOn: '2025-04-01', endsOn: '2026-03-31', current: false },
];

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.' },
});

/**
 * A report, as the contract shapes one.
 *
 * `errorCount` defaults to the length of `errors`, which is what the server sends whenever it has
 * not had to cap the list — so a test that does not care about truncation gets the honest shape for
 * free, and a test that does care states both numbers explicitly.
 */
const report = (over: Partial<ImportReport> = {}): ImportReport => {
  const errors = over.errors ?? [];
  return {
    totalRows: 3,
    validRows: 3,
    imported: 0,
    errorCount: errors.length,
    errors,
    ...over,
  };
};

/**
 * A problem, as the contract shapes one.
 *
 * **No cell value anywhere in this fixture**, because there is none in the real payload either
 * (ADR-0021 §6): a message names the column and what is wrong with it and never quotes the child's
 * name or date of birth. A fixture that carried one would be testing a response the server does
 * not send, and would put invented children's data in the repository for no reason.
 */
const problem = (
  row: number,
  column: string,
  message = 'Not a date in yyyy-mm-dd.',
): ImportError => ({ row, column, message });

describe('StudentImport', () => {
  let fixture: ComponentFixture<StudentImport>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement | undefined;

  const fileInput = () => element().querySelector('#import-file') as HTMLInputElement;

  /**
   * Puts a file on the `<input>` the way a browser would.
   *
   * `input.files` is read-only and jsdom has no picker, so the FileList is defined onto the
   * element. That is deliberate rather than a shortcut: the component reads the file off the DOM
   * at the moment it uploads, so a test that stuffed a signal instead would be testing something
   * the screen does not do.
   */
  const choose = (name: string, bytes = 'admission_number\n1\n') => {
    const file = new File([bytes], name, { type: 'text/csv' });
    const input = fileInput();
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: { 0: file, length: 1, item: (i: number) => (i === 0 ? file : null) },
    });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    return file;
  };

  /** On the screen with the academic years loaded and the current one already chosen. */
  const arrive = (years: AcademicSession[] = sessions) => {
    fixture = TestBed.createComponent(StudentImport);
    fixture.detectChanges();
    httpMock.expectOne({ url: SESSIONS, method: 'GET' }).flush(envelope(years));
    fixture.detectChanges();
  };

  const check = () => {
    button('Check the file')!.click();
    fixture.detectChanges();
    return httpMock.expectOne({ url: VALIDATE, method: 'POST' });
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentImport],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  /** Set by the one test that patches URL's statics, so they are put back however it ends. */
  let restoreUrl: (() => void) | null = null;

  afterEach(() => {
    httpMock.verify();
    restoreUrl?.();
    restoreUrl = null;
    vi.restoreAllMocks();
    // `restoreAllMocks` does not undo `vi.stubGlobal`. Nothing here should be stubbing a global,
    // and this makes that true rather than hoped for: a stub that escapes this file breaks whichever
    // suites happen to share the worker, which is a different set on every machine.
    vi.unstubAllGlobals();
  });

  // ── Choosing ─────────────────────────────────────────────────────────────────────────────

  it('offers the school its academic years and starts on the current one', () => {
    arrive();

    const select = element().querySelector('#import-session') as HTMLSelectElement;
    expect(select.value).toBe('s-2026');
    expect(text()).toContain('2026–27 (current)');
  });

  /**
   * The name and the size, and nothing else. The file holds hundreds of children's names and dates
   * of birth (ADR-0014) and this screen has no business having read any of it.
   */
  it('says which file is chosen without reading a word of it', () => {
    arrive();
    choose('class-5.csv');

    expect(text()).toContain('class-5.csv');
    // The bytes of the fixture are `admission_number\n1\n`; none of them may appear on screen.
    expect(text()).not.toContain('admission_number\n');
  });

  it('will not check anything until a file is chosen', () => {
    arrive();

    expect(button('Check the file')!.disabled).toBe(true);
    expect(text()).toContain('Choose a file above first');
  });

  // ── Checking ─────────────────────────────────────────────────────────────────────────────

  it('sends the file and the chosen year as multipart, and writes nothing', () => {
    arrive();
    choose('class-5.csv');

    const request = check();
    const body = request.request.body as FormData;
    expect((body.get('file') as File).name).toBe('class-5.csv');
    expect(body.get('academicSessionId')).toBe('s-2026');
    // The validate endpoint, not the import one. `httpMock.verify()` asserts nothing else went.
    expect(request.request.url).toBe(VALIDATE);

    request.flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();
  });

  it('enables the import once a check comes back with nothing wrong', () => {
    arrive();
    choose('class-5.csv');
    check().flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();

    expect(text()).toContain('Nothing wrong with this file');
    expect(text()).toContain('613 rows');
    expect(button('Import these students')!.disabled).toBe(false);
  });

  it('does not offer the import before anything has been checked', () => {
    arrive();
    choose('class-5.csv');

    expect(button('Import these students')!.disabled).toBe(true);
    expect(text()).toContain('Import stays unavailable until a check');
  });

  // ── The problems ─────────────────────────────────────────────────────────────────────────

  /**
   * The behaviour the whole screen exists for. A school with a long file and twenty-seven problems
   * has to fix all twenty-seven in one pass; showing the first, or a page of them, sends them round
   * the loop twenty-seven times and they give up.
   */
  it('renders every problem, not the first one', () => {
    arrive();
    choose('class-5.csv');

    const errors = Array.from({ length: 27 }, (_, index) =>
      problem(index + 2, 'date_of_birth', `Problem number ${index + 1}.`),
    );
    check().flush(envelope(report({ totalRows: 600, validRows: 573, errors })));
    fixture.detectChanges();

    for (const error of errors) {
      expect(text()).toContain(error.message);
    }
    expect(element().querySelectorAll('.problem')).toHaveLength(27);
  });

  it('counts the problems and the rows they are in', () => {
    arrive();
    choose('class-5.csv');
    check().flush(
      envelope(
        report({
          totalRows: 600,
          validRows: 598,
          errors: [problem(14, 'date_of_birth'), problem(14, 'class'), problem(31, 'gender')],
        }),
      ),
    );
    fixture.detectChanges();

    expect(text()).toContain('600 rows');
    expect(text()).toContain('3 problems');
    expect(text()).toContain('in 2 rows');
  });

  /** The header is row 1, so the numbers on screen are the ones in the spreadsheet. */
  it('groups by spreadsheet row, in the order the file will be fixed in', () => {
    arrive();
    choose('class-5.csv');
    check().flush(
      envelope(
        report({
          errors: [problem(31, 'gender'), problem(14, 'class'), problem(14, 'date_of_birth')],
        }),
      ),
    );
    fixture.detectChanges();

    const headings = Array.from(element().querySelectorAll('.group__name')).map(
      (node) => node.textContent?.trim() ?? '',
    );
    expect(headings).toEqual(['Row 14', 'Row 31']);
    expect(text()).toContain('Row 1 is the header');
  });

  /** The other question: one column wrong two hundred times is one mistake, not two hundred. */
  it('regroups by column, biggest group first', () => {
    arrive();
    choose('class-5.csv');
    check().flush(
      envelope(
        report({
          errors: [problem(3, 'class'), problem(4, 'date_of_birth'), problem(5, 'date_of_birth')],
        }),
      ),
    );
    fixture.detectChanges();

    button('By column')!.click();
    fixture.detectChanges();

    const headings = Array.from(element().querySelectorAll('.group__name')).map(
      (node) => node.textContent?.trim() ?? '',
    );
    expect(headings).toEqual(['date_of_birth', 'class']);
  });

  /**
   * ADR-0021 §2. One bad row imports nothing, so offering the button would be offering a
   * guaranteed failure — and the sentence beside it has to say that, or a greyed-out button is
   * just a broken screen.
   */
  it('refuses to offer the import while anything is wrong, and says why', () => {
    arrive();
    choose('class-5.csv');
    check().flush(
      envelope(report({ totalRows: 600, validRows: 599, errors: [problem(14, 'class')] })),
    );
    fixture.detectChanges();

    expect(button('Import these students')!.disabled).toBe(true);
    expect(text()).toContain('this would import nothing at all');
    expect(text()).toContain('the file comes in whole or not at all');
  });

  it('does not offer the import for a file with a header and no students', () => {
    arrive();
    choose('empty.csv');
    check().flush(envelope(report({ totalRows: 0, validRows: 0 })));
    fixture.detectChanges();

    expect(text()).toContain('There are no students in this file');
    expect(button('Import these students')!.disabled).toBe(true);
  });

  // ── Staleness ────────────────────────────────────────────────────────────────────────────

  /**
   * The rule ADR-0021 §1 is worth nothing without: a clean answer is about one file, and choosing
   * another one throws it away rather than letting it authorise an upload the server never saw.
   */
  it('throws the result away when a different file is chosen', () => {
    arrive();
    choose('class-5.csv');
    check().flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();
    expect(button('Import these students')!.disabled).toBe(false);

    choose('class-6.csv');

    expect(text()).not.toContain('Nothing wrong with this file');
    expect(text()).toContain('class-6.csv');
    expect(button('Import these students')!.disabled).toBe(true);
    expect(text()).toContain('Import stays unavailable until a check');
  });

  /** A different year can turn a clean file into a failing one, so the answer does not carry over. */
  it('throws the result away when a different academic year is chosen', () => {
    arrive();
    choose('class-5.csv');
    check().flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();

    const select = element().querySelector('#import-session') as HTMLSelectElement;
    select.value = 's-2025';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(button('Import these students')!.disabled).toBe(true);
  });

  // ── Importing ────────────────────────────────────────────────────────────────────────────

  it('imports the checked file and says how many landed', () => {
    arrive();
    choose('class-5.csv');
    check().flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();

    button('Import these students')!.click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: IMPORT, method: 'POST' });
    const body = request.request.body as FormData;
    expect((body.get('file') as File).name).toBe('class-5.csv');
    expect(body.get('academicSessionId')).toBe('s-2026');

    request.flush(envelope(report({ totalRows: 613, validRows: 613, imported: 613 })));
    fixture.detectChanges();

    expect(text()).toContain('613 students imported');
    expect(text()).toContain('2026–27');
    const link = element().querySelector('a[href="/students"]');
    expect(link).not.toBeNull();
  });

  /** All-or-nothing means the register is untouched, and the screen has to be able to say so. */
  it('says nothing was imported when the commit fails, and sends the file back to be checked', () => {
    arrive();
    choose('class-5.csv');
    check().flush(envelope(report({ totalRows: 613, validRows: 613 })));
    fixture.detectChanges();

    button('Import these students')!.click();
    fixture.detectChanges();
    httpMock
      .expectOne({ url: IMPORT, method: 'POST' })
      .flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Nothing was imported');
    expect(text()).toContain('the register is exactly as it was');
    expect(button('Import these students')!.disabled).toBe(true);
  });

  // ── Failures ─────────────────────────────────────────────────────────────────────────────

  /**
   * A 413 is refused by the container before it reaches a controller, so there is no envelope and
   * no error code to read — only the status. What the user needs is the one fact they can act on.
   */
  it('explains a file over the upload limit in words, not as "Request Entity Too Large"', () => {
    arrive();
    choose('everybody.csv');
    check().flush('', { status: 413, statusText: 'Request Entity Too Large' });
    fixture.detectChanges();

    expect(text()).toContain('That file is larger than the limit');
    expect(text()).toContain('Split the spreadsheet');
    expect(text()).not.toContain('Request Entity Too Large');
    expect(button('Import these students')!.disabled).toBe(true);
  });

  it('explains a 403 rather than crashing, and stops offering the flow', () => {
    arrive();
    choose('class-5.csv');
    check().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to import students');
    expect(text()).toContain('Nothing has been changed');
    expect(button('Check the file')).toBeUndefined();
    expect(button('Import these students')).toBeUndefined();
  });

  it('offers a retry when the check itself fails', () => {
    arrive();
    choose('class-5.csv');
    check().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('The file could not be checked');
    expect(button('Try again')).toBeDefined();
    expect(button('Import these students')!.disabled).toBe(true);
  });

  it('says a school with no academic year has to add one first', () => {
    arrive([]);

    expect(text()).toContain('This school has no academic year yet');
    expect(element().querySelector('#import-file')).toBeNull();
  });

  it('explains a 403 on the academic years, which leaves nothing to import into', () => {
    fixture = TestBed.createComponent(StudentImport);
    fixture.detectChanges();
    httpMock
      .expectOne({ url: SESSIONS, method: 'GET' })
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain("You cannot see this school's academic years");
  });

  // ── The template ─────────────────────────────────────────────────────────────────────────

  /**
   * The commonest import failure is a column called "DOB", and a header the school did not type is
   * the cheapest possible fix for it. Header row only — a sample child left in the sheet would be
   * imported as a real one.
   */
  it('offers a template with the header row and no data', () => {
    arrive();

    const blobs: Blob[] = [];
    const createObjectURL = vi.fn((blob: Blob) => {
      blobs.push(blob);
      return 'blob:test';
    });

    // Patch the two STATIC methods, never the `URL` global itself.
    //
    // `vi.stubGlobal('URL', { ...URL, createObjectURL })` is the obvious spelling and it breaks
    // every other suite in the same worker: spreading a class copies none of its construct
    // behaviour, so the global becomes a plain object and `new URL(...)` anywhere else throws
    // "URL is not a constructor". `vi.restoreAllMocks()` does not undo `stubGlobal` either, so the
    // damage outlives the file. It cost a green local run and a red CI one, because which files
    // share a worker differs between machines.
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn() as unknown as typeof URL.revokeObjectURL;
    restoreUrl = () => {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    };

    button('Download the template')!.click();
    fixture.detectChanges();

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    return blobs[0].text().then((csv) => {
      expect(csv).toBe(
        'admission_number,full_name,date_of_birth,gender,status,admitted_on,class,section,roll_number\r\n',
      );
    });
  });

  /**
   * The server caps the list at 200 so a pathological file cannot make the response unusable. If the
   * screen counted the array instead of reading `errorCount`, a school with 1,800 problems would be
   * told it has 200 — and would fix those, upload again, and be told there are 200 more.
   */
  it('says the problem list is only a sample when the server capped it', () => {
    arrive();
    choose('whole-school.csv');

    check().flush(
      envelope(
        report({
          totalRows: 1800,
          validRows: 0,
          errorCount: 1800,
          errors: [problem(2, 'gender'), problem(3, 'date_of_birth')],
        }),
      ),
    );
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1,800');
    expect(text.toLowerCase()).toContain('showing the first 2');
    // And the import is still refused, because 1,800 problems means nothing would land.
    expect(button('Import these students')?.disabled).toBe(true);
  });
});
