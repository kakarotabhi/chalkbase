import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { GuardianStudent, GuardianSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { GuardianList } from './guardian-list';

const GUARDIANS = '/api/guardians';

/**
 * Invented families at an invented school.
 *
 * No `email` key, deliberately: the backend serialises with `default-property-inclusion: non_null`,
 * so a guardian without one arrives with the field absent rather than as `email: null`.
 */
const guardian = (over: Partial<GuardianSummary> = {}): GuardianSummary => ({
  id: 'g-1',
  fullName: 'Test Parent One',
  phone: '9000000001',
  occupation: 'Shopkeeper',
  linkedStudentCount: 4,
  ...over,
});

/** One of a guardian's children, as `GET /api/guardians/{id}/students` answers. */
const child = (over: Partial<GuardianStudent> = {}): GuardianStudent => ({
  studentId: 's-1',
  fullName: 'Test Child One',
  admissionNumber: '2026/0141',
  relation: 'FATHER',
  primary: true,
  currentEnrolment: {
    sessionName: '2026-27',
    className: 'Class 5',
    sectionName: 'A',
    rollNumber: '12',
  },
  ...over,
});

const page = (content: readonly GuardianSummary[], totalElements = content.length) => ({
  content,
  page: 0,
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

describe('GuardianList', () => {
  let fixture: ComponentFixture<GuardianList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  /** The directory page. Told apart from the duplicate check by its page size. */
  const search = () =>
    httpMock.expectOne(
      (request) => request.url === GUARDIANS && request.params.get('size') === '25',
    );

  /** The check that runs under the phone field while a new guardian is being typed. */
  const duplicateCheck = () =>
    httpMock.expectOne(
      (request) => request.url === GUARDIANS && request.params.get('size') === '10',
    );

  const childrenOf = (id: string) =>
    httpMock.expectOne({ url: `${GUARDIANS}/${id}/students`, method: 'GET' });

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const arrive = (rows: readonly GuardianSummary[] = [guardian()]) => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(envelope(page(rows)));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuardianList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // The default for every test below: somebody who may do the things this screen offers.
    // The tests that care sign a different user in instead, and none of them mocks the
    // permission check — the real `SessionStore` is what the templates read.
    signInWith(Permissions.GUARDIAN_READ, Permissions.GUARDIAN_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  /**
   * The count is the whole reason this screen exists rather than a phone-number box on each child:
   * it says that editing this row reaches every student the guardian is attached to.
   */
  it('shows each guardian once, with how many students they are linked to', () => {
    arrive();

    expect(text()).toContain('Test Parent One');
    expect(text()).toContain('9000000001');
    expect(text()).toContain('Linked to 4 students');
    expect(text()).toContain('correcting their phone number corrects it for all four');
  });

  /** Absent, not null — see the fixture. */
  it('says a field is not recorded when the server left it out', () => {
    arrive();

    expect(text()).toContain('Not recorded');
  });

  /**
   * ADR-0020 §6. A link is ended from the student's own record, where you can see whose it is; a
   * button here would end a relationship without showing which child it was about.
   */
  it('offers no way to delete a guardian, and no way to unlink one', () => {
    arrive();

    const labels = Array.from(element().querySelectorAll('button')).map((candidate) =>
      (candidate.textContent ?? '').trim().toLowerCase(),
    );
    expect(labels.some((label) => label.includes('delete'))).toBe(false);
    expect(labels.some((label) => label.includes('remove'))).toBe(false);
    expect(labels).toContain('edit');
  });

  it('explains a 403 rather than crashing', () => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view guardians');
    expect(element().querySelector('#guardian-search')).toBeNull();
  });

  it('offers a retry when the list fails for any other reason', () => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the guardians');

    button('Try again').click();
    fixture.detectChanges();
    search().flush(envelope(page([guardian()])));
    fixture.detectChanges();

    expect(text()).toContain('Test Parent One');
  });

  // ── Editing ──────────────────────────────────────────────────────────────────────────────

  /**
   * Editing here is the point of the model working: one record, one correction, and every child
   * of theirs reads the new number. The screen says so when it has done it.
   */
  it('saves a correction once, for every student the guardian is linked to', () => {
    arrive();

    button('Edit').click();
    fixture.detectChanges();

    type('guardian-phone', '9000000009');
    button('Save guardian').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${GUARDIANS}/g-1`, method: 'PUT' });
    expect(request.request.body).toEqual({
      fullName: 'Test Parent One',
      phone: '9000000009',
      email: '',
      occupation: 'Shopkeeper',
    });
    request.flush(envelope(null));
    fixture.detectChanges();

    // The page is re-read rather than patched: `linkedStudentCount` is computed per row.
    search().flush(envelope(page([guardian({ phone: '9000000009' })])));
    fixture.detectChanges();

    expect(text()).toContain('for every student they are linked to');
    expect(text()).toContain('9000000009');
  });

  /**
   * The second place a duplicate can be born — the first is the attach panel on a student's
   * record — so the same warning is said here, at the moment of creating.
   */
  it('tells the user to search before adding a new guardian', () => {
    arrive();

    button('Add a guardian').click();
    fixture.detectChanges();

    expect(text()).toContain('Search above first');
    expect(text()).toContain('rather than adding a second one');
  });

  it('refuses a guardian with no name, before asking the server', () => {
    arrive();

    button('Add a guardian').click();
    fixture.detectChanges();
    button('Save guardian').click();
    fixture.detectChanges();

    expect(text()).toContain("Enter the guardian's name");
    // Nothing was sent: `httpMock.verify()` in afterEach is what asserts that.
  });

  /**
   * Fake timers, because typing a phone number now also starts the debounced duplicate check, and
   * whether a real-timer test outran the debounce would depend on how busy the machine was. Driven
   * explicitly here and answered with nobody; the check has its own tests below.
   */
  it('creates a guardian and re-reads the page', () => {
    vi.useFakeTimers();
    try {
      arrive();

      button('Add a guardian').click();
      fixture.detectChanges();

      type('guardian-name', 'Test Parent Two');
      type('guardian-phone', '9000000002');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();
      duplicateCheck().flush(envelope(page([])));
      fixture.detectChanges();

      button('Save guardian').click();
      fixture.detectChanges();

      const request = httpMock.expectOne({ url: GUARDIANS, method: 'POST' });
      expect(request.request.body).toEqual({
        fullName: 'Test Parent Two',
        phone: '9000000002',
        email: '',
        occupation: '',
      });
      request.flush(envelope(guardian({ id: 'g-2', fullName: 'Test Parent Two' })));
      fixture.detectChanges();

      search().flush(
        envelope(page([guardian(), guardian({ id: 'g-2', fullName: 'Test Parent Two' })])),
      );
      fixture.detectChanges();

      expect(text()).toContain('Test Parent Two was added');
    } finally {
      vi.useRealTimers();
    }
  });

  // ── Which students, though ───────────────────────────────────────────────────────────────

  /**
   * The gap this closes. "Linked to 4 students" says the shared record is working; it does not say
   * which four, and *which four* is the only thing that tells two records with the same name apart.
   * A clerk who cannot tell them apart creates a third.
   */
  it('expands the count into the children behind it', () => {
    arrive([guardian({ linkedStudentCount: 2 })]);

    button('Linked to 2 students').click();
    fixture.detectChanges();

    childrenOf('g-1').flush(
      envelope([
        child(),
        child({
          studentId: 's-2',
          fullName: 'Test Child Two',
          admissionNumber: '2026/0142',
          currentEnrolment: undefined,
        }),
      ]),
    );
    fixture.detectChanges();

    expect(text()).toContain('Test Child One');
    // Class and section, which is what separates two children of one family on a screen.
    expect(text()).toContain('Class 5 · A');
    expect(text()).toContain('2026/0141');
    // Admitted, not yet placed. A real state and said as one.
    expect(text()).toContain('Test Child Two');
    expect(text()).toContain('Not in a class this year');

    // Closing does not re-ask: `httpMock.verify()` in afterEach is what asserts that.
    button('Linked to 2 students').click();
    fixture.detectChanges();
    expect(text()).not.toContain('Test Child One');
  });

  /**
   * The permission decision, seen from the screen. `GET …/students` is guarded by
   * `student:student:read` while the directory is guarded by `student:guardian:read`, so a role
   * really can hold the second without the first. The count is still true and stays; only the names
   * are refused, and the row says which permission is missing rather than looking broken.
   */
  it('keeps the count and explains itself when the caller may not read students', () => {
    arrive([guardian({ linkedStudentCount: 2 })]);

    button('Linked to 2 students').click();
    fixture.detectChanges();
    childrenOf('g-1').flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('Linked to 2 students');
    expect(text()).toContain('"View students" permission');
  });

  /** Nobody points at this record, so there is nothing to open and nothing offers to. */
  it('does not offer to expand a guardian with no students', () => {
    arrive([guardian({ linkedStudentCount: 0 })]);

    expect(text()).toContain('Not linked to any student');
    expect(button('Not linked to any student')).toBeUndefined();
  });

  // ── The duplicate check ──────────────────────────────────────────────────────────────────

  /**
   * The warning at the moment the duplicate would be created.
   *
   * The name search is the advice; this is the moment the advice is about. Two records for one man
   * are spelled two ways, so the number is the discriminator — which is the same reason the backend
   * search now compares digits to digits.
   */
  it('says who already has this number, and offers their record instead', () => {
    vi.useFakeTimers();
    try {
      arrive();

      button('Add a guardian').click();
      fixture.detectChanges();

      type('guardian-name', 'S. Kulkarni');
      type('guardian-phone', '+91 90000 00001');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      // Digits, not the string as typed. `+91 90000 00001` is searched for as `919000000001`.
      const check = duplicateCheck();
      expect(check.request.params.get('q')).toBe('919000000001');
      check.flush(envelope(page([guardian({ phone: '+91 90000 00001' })])));
      fixture.detectChanges();

      expect(text()).toContain('Someone at this school already has this number');
      expect(text()).toContain('Test Parent One');
      expect(text()).toContain('Linked to 4 students');
      // And it is not a refusal — see the next test, and the sentence that says so here.
      expect(text()).toContain('carry on and add them');

      button('Use this guardian').click();
      fixture.detectChanges();

      // The editor is now about the person who is already here, with their details in it.
      expect(text()).toContain('Edit this guardian');
      expect(text()).toContain('who is already at this school');
      expect((element().querySelector('#guardian-name') as HTMLInputElement).value).toBe(
        'Test Parent One',
      );
      // Nothing was created: `httpMock.verify()` in afterEach is what asserts that.
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * **It must not block.** Two people share a number — a couple most obviously — so a warning that
   * disabled Save would refuse a real family, and an override checkbox would be furniture within a
   * week. The control that is honest is the sentence plus the alternative, and then getting out of
   * the way.
   */
  it('still saves the new guardian after warning about the number', () => {
    vi.useFakeTimers();
    try {
      arrive();

      button('Add a guardian').click();
      fixture.detectChanges();

      type('guardian-name', 'Test Parent Two');
      type('guardian-phone', '9000000001');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      duplicateCheck().flush(envelope(page([guardian()])));
      fixture.detectChanges();

      expect(text()).toContain('already has this number');
      const save = button('Save guardian');
      expect(save.disabled).toBe(false);

      save.click();
      fixture.detectChanges();

      const created = httpMock.expectOne({ url: GUARDIANS, method: 'POST' });
      expect(created.request.body.fullName).toBe('Test Parent Two');
      created.flush(envelope(guardian({ id: 'g-2', fullName: 'Test Parent Two' })));
      fixture.detectChanges();
      search().flush(envelope(page([guardian()])));
      fixture.detectChanges();

      expect(text()).toContain('Test Parent Two was added');
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * A partial number is not a claim about a person. Warning from the first digit would tell every
   * clerk typing `9` that they are about to duplicate somebody, and a box that cries wolf on every
   * keystroke is a box nobody reads by the second week.
   */
  it('says nothing until enough of the number has been typed', () => {
    vi.useFakeTimers();
    try {
      arrive();

      button('Add a guardian').click();
      fixture.detectChanges();

      type('guardian-phone', '900');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      expect(text()).not.toContain('already has this number');
      // Nothing was asked of the server: `httpMock.verify()` in afterEach asserts it.
    } finally {
      vi.useRealTimers();
    }
  });

  // ── What the directory's actions are gated on ────────────────────────────────────────────

  describe('write actions', () => {
    it('offers adding and editing to somebody who may manage guardians', () => {
      arrive();

      expect(button('Add a guardian')).toBeTruthy();
      expect(button('Edit')).toBeTruthy();
    });

    it('offers neither to somebody who may only read the directory', () => {
      signInWith(Permissions.GUARDIAN_READ);
      arrive();

      expect(button('Add a guardian')).toBeUndefined();
      expect(button('Edit')).toBeUndefined();
      expect(text()).toContain('Test Parent One');
    });

    it('reads the live session, not a snapshot taken when the screen was built', () => {
      signInWith(Permissions.GUARDIAN_READ);
      arrive();
      expect(button('Add a guardian')).toBeUndefined();

      signInWith(Permissions.GUARDIAN_READ, Permissions.GUARDIAN_MANAGE);
      fixture.detectChanges();

      expect(button('Add a guardian')).toBeTruthy();
    });
  });
});
