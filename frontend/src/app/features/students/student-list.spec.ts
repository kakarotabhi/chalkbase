import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SchoolClass, StudentSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { StudentList } from './student-list';

const STUDENTS = '/api/students';
const CLASSES = '/api/academics/classes';

/** Invented children at an invented school. Never real student data in a fixture (AGENTS rule 9). */
const student = (over: Partial<StudentSummary> = {}): StudentSummary => ({
  id: '018f3a10-0000-7000-8000-000000000001',
  admissionNumber: 'EVG/2026/0148',
  fullName: 'Test Student One',
  gender: 'FEMALE',
  status: 'ACTIVE',
  currentEnrolment: {
    sessionName: '2026–27',
    className: 'Class 5',
    sectionName: 'A',
    rollNumber: '12',
  },
  ...over,
});

const ladder: SchoolClass[] = [
  {
    id: 'c1',
    name: 'Class 5',
    sequence: 5,
    active: true,
    sections: [
      { id: 'sec-a', name: 'A', active: true },
      { id: 'sec-b', name: 'B', active: false },
    ],
  },
];

/**
 * A student who has been admitted but is not in a class yet.
 *
 * Built by hand rather than as `student({ currentEnrolment: null })`, because the backend
 * serialises with `default-property-inclusion: non_null`: the key is **absent** from the JSON, not
 * set to null. A fixture that sent null would be testing a payload the server never sends, and the
 * screen's `if (currentEnrolment)` would pass for the wrong reason.
 */
const unenrolled = (): StudentSummary => ({
  id: '018f3a10-0000-7000-8000-000000000002',
  admissionNumber: 'EVG/2026/0149',
  fullName: 'Test Student Two',
  gender: 'MALE',
  status: 'ACTIVE',
});

const page = (content: readonly StudentSummary[], totalElements = content.length, number = 0) => ({
  content,
  page: number,
  size: 25,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / 25)),
});

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

describe('StudentList', () => {
  let fixture: ComponentFixture<StudentList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const search = () => httpMock.expectOne((request) => request.url === STUDENTS);
  const classes = () => httpMock.expectOne((request) => request.url === CLASSES);

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const choose = (id: string, value: string) => {
    const select = element().querySelector(`#${id}`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  /** Creates the screen and answers both of its opening requests. */
  const arrive = (rows: readonly StudentSummary[] = [student()]) => {
    fixture = TestBed.createComponent(StudentList);
    fixture.detectChanges();
    search().flush(envelope(page(rows)));
    classes().flush(envelope(ladder));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // The default for every test below: somebody who may do the things this screen offers.
    // The tests that care sign a different user in instead, and none of them mocks the
    // permission check — the real `SessionStore` is what the templates read.
    signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ── Listing ──────────────────────────────────────────────────────────────────────────────

  /**
   * "Which section is this child in" is the question the office is asking, so the answer has to be
   * on the row rather than one tap away.
   */
  it('shows where each student currently sits, on the row', () => {
    arrive();

    expect(text()).toContain('Test Student One');
    expect(text()).toContain('EVG/2026/0148');
    expect(text()).toContain('Class 5 · A');
    expect(text()).toContain('2026–27');
    expect(text()).toContain('12');
    expect(text()).toContain('Active');
  });

  /** A real state between admission and the class lists settling, not data we lost. */
  it('copes with a currentEnrolment the server left out entirely', () => {
    arrive([unenrolled()]);

    expect(text()).toContain('Not enrolled yet');
  });

  /**
   * ADR-0014: a link this app builds must never carry a child's name, a date of birth or an
   * admission number, because an href lands in browser history and in every screenshot of the
   * address bar.
   */
  it('links to the record by id, with nothing about the child in the address', () => {
    arrive();

    const link = element().querySelector('a.name') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/students/018f3a10-0000-7000-8000-000000000001');
    expect(link.getAttribute('href')).not.toContain('Test');
    expect(link.getAttribute('href')).not.toContain('EVG');
  });

  /** ADR-0020 §6: a student is withdrawn, never removed, and no screen may offer otherwise. */
  it('offers no way to delete a student', () => {
    arrive();

    const labels = Array.from(element().querySelectorAll('button')).map((candidate) =>
      (candidate.textContent ?? '').toLowerCase(),
    );
    expect(labels.some((label) => label.includes('delete'))).toBe(false);
    expect(labels.some((label) => label.includes('remove'))).toBe(false);
  });

  // ── Filters ──────────────────────────────────────────────────────────────────────────────

  it('offers every section in the school as one class-and-section choice', () => {
    arrive();

    const options = Array.from(element().querySelectorAll('#student-section-filter option')).map(
      (option) => option.textContent?.trim(),
    );
    expect(options).toContain('Any class or section');
    expect(options).toContain('Class 5 · A');
    // Listed and marked rather than hidden: a student can still be enrolled in a section the
    // school has stopped running, and dropping it would make them unfindable by this filter.
    expect(options).toContain('Class 5 · B · not running');
  });

  it('narrows by status, from the first page', () => {
    arrive([student(), student({ id: 'b', fullName: 'Test Student Two' })]);

    choose('student-status-filter', 'WITHDRAWN');

    const request = search();
    expect(request.request.params.get('status')).toBe('WITHDRAWN');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(envelope(page([])));
    fixture.detectChanges();

    expect(text()).toContain('No student matches this search');
  });

  /**
   * The class ladder is a separate permission and a separate request. Losing it costs one filter,
   * not the six hundred students the screen is actually for.
   */
  it('keeps working when the class ladder cannot be loaded', () => {
    fixture = TestBed.createComponent(StudentList);
    fixture.detectChanges();
    search().flush(envelope(page([student()])));
    classes().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('Test Student One');
    expect(text()).toContain('this filter is empty');
  });

  // ── Failures ─────────────────────────────────────────────────────────────────────────────

  /**
   * No client-side permission guard, deliberately (ADR-0008). Typing the URL without the
   * permission lands here and is explained rather than redirected.
   */
  it('explains a 403 instead of redirecting or crashing', () => {
    fixture = TestBed.createComponent(StudentList);
    fixture.detectChanges();
    search().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    classes().flush(envelope(ladder));
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view students');
    // The filters are gone with the list: there is nothing to filter.
    expect(element().querySelector('#student-search')).toBeNull();
  });

  it('offers a retry when the list fails for any other reason', () => {
    fixture = TestBed.createComponent(StudentList);
    fixture.detectChanges();
    search().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    classes().flush(envelope(ladder));
    fixture.detectChanges();

    expect(text()).toContain('Could not load the students');

    button('Try again').click();
    fixture.detectChanges();
    search().flush(envelope(page([student()])));
    fixture.detectChanges();

    expect(text()).toContain('Test Student One');
  });

  // ── Adding ───────────────────────────────────────────────────────────────────────────────

  /**
   * A student who has just been admitted has no guardian and no enrolment, and both live on the
   * record — so the record is where the flow ends, not back on a list of six hundred names.
   */
  it('creates a student and opens their record', () => {
    arrive();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    button('Add a student').click();
    fixture.detectChanges();

    type('student-admission-number', 'EVG/2026/0149');
    type('student-full-name', 'Test Student Two');
    type('student-date-of-birth', '2016-06-01');
    choose('student-gender', 'MALE');
    type('student-admitted-on', '2026-04-01');

    button('Add student').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: STUDENTS, method: 'POST' });
    expect(request.request.body).toEqual({
      admissionNumber: 'EVG/2026/0149',
      fullName: 'Test Student Two',
      dateOfBirth: '2016-06-01',
      gender: 'MALE',
      // A new student is active; every other status is something that happened later.
      status: 'ACTIVE',
      admittedOn: '2026-04-01',
    });
    request.flush(envelope({ ...student({ id: 'new-1' }), guardians: [], enrolments: [] }));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/students', 'new-1']);
  });

  it('refuses a date of birth that is not in the past, before asking the server', () => {
    arrive();

    button('Add a student').click();
    fixture.detectChanges();

    type('student-admission-number', 'EVG/2026/0150');
    type('student-full-name', 'Test Student Three');
    type('student-date-of-birth', '2999-01-01');
    choose('student-gender', 'OTHER');
    type('student-admitted-on', '2026-04-01');

    button('Add student').click();
    fixture.detectChanges();

    expect(text()).toContain('A date of birth has to be in the past');
    // Nothing was sent: `httpMock.verify()` in afterEach is what asserts that.
  });

  // ── What the toolbar is gated on ─────────────────────────────────────────────────────────

  /**
   * The permission check is exercised through the real `SessionStore` and the real template.
   * Nothing here mocks it: a stub would pass whether or not the store, the helper and the markup
   * agree, which is the only thing these three tests are for.
   */
  describe('write actions', () => {
    const importLink = () => element().querySelector('a[href="/students/import"]');

    it('offers both ways of adding a student to somebody who may manage them', () => {
      arrive();

      expect(button('Add a student')).toBeTruthy();
      expect(importLink()).not.toBeNull();
    });

    it('offers neither to a classteacher who may only read the roll', () => {
      signInWith(Permissions.STUDENT_READ);
      arrive();

      expect(button('Add a student')).toBeUndefined();
      expect(importLink()).toBeNull();
      // The list itself is what the read entitles them to, and it still works.
      expect(text()).toContain('Test Student One');
    });

    it('reads the live session, not a snapshot taken when the screen was built', () => {
      signInWith(Permissions.STUDENT_READ);
      arrive();
      expect(button('Add a student')).toBeUndefined();

      signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE);
      fixture.detectChanges();

      expect(button('Add a student')).toBeTruthy();
    });
  });
});
