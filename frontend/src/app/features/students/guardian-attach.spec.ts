import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { GuardianSummary } from '../../core/api/models';
import { GuardianAttach } from './guardian-attach';

const GUARDIANS = '/api/guardians';
const STUDENT = '018f3a10-0000-7000-8000-000000000001';

/**
 * Invented families at an invented school. Never real guardian data in a fixture.
 *
 * **`email` is missing from this object on purpose.** The backend serialises with
 * `default-property-inclusion: non_null`, so a guardian with no email address arrives with no
 * `email` key at all rather than with `email: null` — and a fixture that sent `null` would be
 * testing a payload the server never produces.
 */
const guardian = (over: Partial<GuardianSummary> = {}): GuardianSummary => ({
  id: 'g-1',
  fullName: 'Test Parent One',
  phone: '9000000001',
  occupation: 'Shopkeeper',
  linkedStudentCount: 4,
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

/**
 * The screen that decides whether ADR-0020 §5 is a working model or a decorative constraint.
 *
 * A guardian is shared between siblings on purpose, so that correcting a father's phone number
 * once corrects it for all four of his children. That benefit survives only if the office finds the
 * existing record instead of making a second one — so what these tests assert is not that the
 * component can create a guardian, but that it makes finding one the easy path.
 */
describe('GuardianAttach', () => {
  let fixture: ComponentFixture<GuardianAttach>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const search = () => httpMock.expectOne((request) => request.url === GUARDIANS);

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

  /** jsdom does not run a checkbox's activation behaviour on `.click()`, so the change is driven. */
  const tick = (id: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.checked = true;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  const choose = (id: string, value: string) => {
    const select = element().querySelector(`#${id}`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  const arrive = (results: readonly GuardianSummary[] = [guardian()], linked: string[] = []) => {
    fixture = TestBed.createComponent(GuardianAttach);
    fixture.componentRef.setInput('studentId', STUDENT);
    fixture.componentRef.setInput('linkedGuardianIds', linked);
    fixture.detectChanges();
    search().flush(envelope(page(results)));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuardianAttach],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ── Search first ─────────────────────────────────────────────────────────────────────────

  /**
   * The single most important behaviour on this screen. If it opened empty saying "type to
   * search", the first instinct would be to reach for "add a new one" — which is how a father with
   * four children here becomes four records with four phone numbers that disagree.
   */
  it('lists the guardians already at this school before anything is typed', () => {
    fixture = TestBed.createComponent(GuardianAttach);
    fixture.componentRef.setInput('studentId', STUDENT);
    fixture.detectChanges();

    const request = search();
    // No `q` on the opening request: this is "everybody", not a search for nothing.
    expect(request.request.params.has('q')).toBe(false);
    request.flush(envelope(page([guardian()])));
    fixture.detectChanges();

    expect(text()).toContain('Test Parent One');
  });

  /** The count is the argument for reusing the record rather than making a second one. */
  it('says how many students each guardian is already linked to', () => {
    arrive();

    expect(text()).toContain('Linked to 4 students');
  });

  it('counts one student in the singular', () => {
    arrive([guardian({ linkedStudentCount: 1 })]);

    expect(text()).toContain('Linked to 1 student');
    expect(text()).not.toContain('Linked to 1 students');
  });

  /**
   * Shown, not hidden. Dropping them from the list would make somebody wonder whether the search
   * works and reach for "add a new one" — the exact failure this screen exists to prevent.
   */
  it('shows a guardian already on this student, and does not offer to attach them again', () => {
    arrive([guardian()], ['g-1']);

    expect(text()).toContain('Test Parent One');
    expect(text()).toContain('Already on this student');
    expect(button('Choose')).toBeUndefined();
  });

  /**
   * Creating stays reachable — a family whose first child is being admitted is a real case — but
   * while there are results to read it is the quieter option below them, not a button of equal
   * weight beside them.
   */
  it('keeps "add a new guardian" available but subordinate while results are listed', () => {
    arrive();

    expect(text()).toContain('Not one of these?');
    const create = button('Add a new guardian');
    expect(create).toBeDefined();
    // A text button, not one of the screen's real buttons.
    expect(create.className).toContain('linklike');
  });

  // ── Attaching one that exists ────────────────────────────────────────────────────────────

  it('attaches a guardian who already exists, with the relation and the main-contact choice', () => {
    arrive();

    button('Choose').click();
    fixture.detectChanges();

    // The link count is repeated on the chosen guardian: it is what says "this is the same person
    // your other records already use".
    expect(text()).toContain('Linked to 4 students');
    expect(text()).toContain('edited on the Guardians screen');

    choose('guardian-relation', 'FATHER');
    tick('guardian-primary');

    button('Attach to this student').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({
      url: `/api/students/${STUDENT}/guardians`,
      method: 'POST',
    });
    expect(request.request.body).toEqual({
      guardianId: 'g-1',
      relation: 'FATHER',
      primary: true,
    });
    request.flush(envelope(null));
    fixture.detectChanges();
  });

  it('refuses to attach without a relation, before asking the server', () => {
    arrive();

    button('Choose').click();
    fixture.detectChanges();
    button('Attach to this student').click();
    fixture.detectChanges();

    expect(text()).toContain('Choose what this person is to the student');
    // Nothing was sent: `httpMock.verify()` in afterEach is what asserts that.
  });

  // ── Creating, only when the search found nobody ──────────────────────────────────────────

  it('offers to create when the school has no guardians at all', () => {
    arrive([]);

    expect(text()).toContain('There are no guardians at this school yet');
    expect(button('Add a new guardian')).toBeDefined();
  });

  /**
   * The moment when creating is the right answer, so it is said plainly and by name — and only
   * after a search has actually run and matched nobody.
   */
  it('names who was searched for when nobody matches', () => {
    vi.useFakeTimers();
    try {
      arrive([guardian()]);

      type('guardian-search', 'Nobody Here');
      vi.advanceTimersByTime(400);
      fixture.detectChanges();

      const request = search();
      expect(request.request.params.get('q')).toBe('Nobody Here');
      request.flush(envelope(page([])));
      fixture.detectChanges();

      expect(text()).toContain('No guardian here matches');
      expect(text()).toContain('Nobody Here');
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * There is no endpoint that creates a person and links them, so this is two requests in a fixed
   * order: the guardian has to exist before anything can point at them.
   */
  it('creates the person, then links them, in that order', () => {
    arrive([]);

    button('Add a new guardian').click();
    fixture.detectChanges();

    type('guardian-new-name', 'Test Parent Two');
    type('guardian-new-phone', '9000000002');
    choose('guardian-new-relation', 'MOTHER');

    button('Add and attach').click();
    fixture.detectChanges();

    const create = httpMock.expectOne({ url: GUARDIANS, method: 'POST' });
    expect(create.request.body).toEqual({
      fullName: 'Test Parent Two',
      phone: '9000000002',
      email: '',
      occupation: '',
    });
    create.flush(envelope(guardian({ id: 'g-new', fullName: 'Test Parent Two' })));
    fixture.detectChanges();

    const link = httpMock.expectOne({
      url: `/api/students/${STUDENT}/guardians`,
      method: 'POST',
    });
    expect(link.request.body).toEqual({
      guardianId: 'g-new',
      relation: 'MOTHER',
      primary: false,
    });
    link.flush(envelope(null));
    fixture.detectChanges();
  });

  /**
   * The half-failure. "Could not save" would send the user back to the top of the form, and
   * pressing the button again would create the person a second time — which is the duplicate this
   * whole screen exists to prevent. So it says what actually happened.
   */
  it('says the guardian exists when the create succeeded and the link did not', () => {
    arrive([]);

    button('Add a new guardian').click();
    fixture.detectChanges();

    type('guardian-new-name', 'Test Parent Three');
    choose('guardian-new-relation', 'GUARDIAN');
    button('Add and attach').click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: GUARDIANS, method: 'POST' })
      .flush(envelope(guardian({ id: 'g-new', fullName: 'Test Parent Three' })));
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `/api/students/${STUDENT}/guardians`, method: 'POST' })
      .flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Test Parent Three was added, but not attached');
    expect(text()).toContain('Do not add them again');
  });

  // ── Failures ─────────────────────────────────────────────────────────────────────────────

  /**
   * A search that did not run has not established that anybody is missing, so the way out is
   * "try again", never "add a new one".
   */
  it('does not offer to create a guardian when the search itself failed', () => {
    fixture = TestBed.createComponent(GuardianAttach);
    fixture.componentRef.setInput('studentId', STUDENT);
    fixture.detectChanges();
    search().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not search the guardians');
    expect(button('Add a new guardian')).toBeUndefined();
    expect(button('Try again')).toBeDefined();
  });

  it('explains a 403 on the guardian search rather than crashing', () => {
    fixture = TestBed.createComponent(GuardianAttach);
    fixture.componentRef.setInput('studentId', STUDENT);
    fixture.detectChanges();
    search().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view guardians');
  });
});
