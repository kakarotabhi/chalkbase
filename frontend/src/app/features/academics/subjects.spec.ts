import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject as SubjectModel } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { Subjects } from './subjects';

const SUBJECTS_URL = '/api/academics/subjects';

/** An invented catalogue for an invented school. Never real school data in a fixture. */
const subject = (over: Partial<SubjectModel> = {}): SubjectModel => ({
  id: 'sub-math',
  name: 'Mathematics',
  code: 'MATH',
  active: true,
  ...over,
});

const page = (content: readonly SubjectModel[], totalElements = content.length) => ({
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

const refusal = (code: string, details?: Record<string, string>) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.', details },
});

describe('Subjects', () => {
  let fixture: ComponentFixture<Subjects>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const list = () =>
    httpMock.expectOne((request) => request.url === SUBJECTS_URL && request.method === 'GET');

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

  const arrive = (rows: readonly SubjectModel[] = [subject()]) => {
    fixture = TestBed.createComponent(Subjects);
    fixture.detectChanges();
    list().flush(envelope(page(rows)));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Subjects],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // The default for every test below: somebody who may do the things this screen offers. The
    // tests that care sign a different user in instead, and none of them mocks the permission
    // check — the real `SessionStore` is what the templates read.
    signInWith(Permissions.SUBJECT_READ, Permissions.SUBJECT_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists the catalogue', () => {
    arrive([subject(), subject({ id: 'sub-eng', name: 'English', code: 'ENG' })]);

    expect(text()).toContain('Mathematics');
    expect(text()).toContain('MATH');
    expect(text()).toContain('English');
    expect(text()).toContain('ENG');
  });

  it('flags a retired subject rather than hiding it', () => {
    arrive([subject({ active: false })]);

    expect(text()).toContain('Mathematics');
    expect(text()).toContain('Retired');
  });

  it('explains a 403 rather than crashing', () => {
    fixture = TestBed.createComponent(Subjects);
    fixture.detectChanges();
    list().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view subjects');
    expect(element().querySelector('#subject-search')).toBeNull();
  });

  it('offers a retry when the list fails for any other reason', () => {
    fixture = TestBed.createComponent(Subjects);
    fixture.detectChanges();
    list().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the subjects');

    button('Try again').click();
    fixture.detectChanges();
    list().flush(envelope(page([subject()])));
    fixture.detectChanges();

    expect(text()).toContain('Mathematics');
  });

  it('says when there are no subjects yet', () => {
    arrive([]);

    expect(text()).toContain('No subjects yet');
  });

  // ── Search ───────────────────────────────────────────────────────────────────────────────

  it('searches by name or code', () => {
    arrive();

    type('subject-search', 'math');
    fixture.detectChanges();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === SUBJECTS_URL && candidate.params.get('q') === 'math',
    );
    request.flush(envelope(page([subject()])));
    fixture.detectChanges();

    expect(text()).toContain('Mathematics');
  });

  // ── Adding ───────────────────────────────────────────────────────────────────────────────

  it('creates a subject and re-reads the page', () => {
    arrive();

    button('Add a subject').click();
    fixture.detectChanges();

    type('subject-name', 'English');
    type('subject-code', 'ENG');
    button('Save subject').click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: SUBJECTS_URL, method: 'POST' });
    expect(created.request.body).toEqual({ name: 'English', code: 'ENG' });
    created.flush(envelope(subject({ id: 'sub-eng', name: 'English', code: 'ENG' })));
    fixture.detectChanges();

    list().flush(
      envelope(page([subject(), subject({ id: 'sub-eng', name: 'English', code: 'ENG' })])),
    );
    fixture.detectChanges();

    expect(text()).toContain('English was added');
  });

  it('refuses a subject with no name or code, before asking the server', () => {
    arrive();

    button('Add a subject').click();
    fixture.detectChanges();
    button('Save subject').click();
    fixture.detectChanges();

    expect(text()).toContain('Give the subject a name');
    expect(text()).toContain('Give the subject a short code');
    // Nothing was sent: `httpMock.verify()` in afterEach is what asserts that.
  });

  /**
   * VAL_001's `details`, keyed by field name (ADR-0007). The server's own reason takes the field's
   * error slot when it disagrees with what the client checked, and typing again clears it rather
   * than leaving a stale complaint under a field the user has already changed.
   */
  it("surfaces the server's own reason for a field, and clears it once the field is edited", () => {
    arrive();

    button('Add a subject').click();
    fixture.detectChanges();
    type('subject-name', 'Mathematics');
    type('subject-code', 'MATH');
    button('Save subject').click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: SUBJECTS_URL, method: 'POST' });
    created.flush(refusal('VAL_001', { code: 'That code is already in a different form here.' }), {
      status: 400,
      statusText: 'Bad Request',
    });
    fixture.detectChanges();

    expect(text()).toContain('That code is already in a different form here.');

    type('subject-code', 'MATHS');
    fixture.detectChanges();

    expect(text()).not.toContain('That code is already in a different form here.');
  });

  /**
   * ACAD_008. Worth its own message rather than the generic failure, and worth naming which row
   * already holds it — a subject keeps its name after it is retired, so "already exists" alone is
   * baffling until the clash is pointed at.
   */
  it('names the row that already holds a clashing name', () => {
    arrive([subject({ name: 'Hindi', code: 'HIN', active: false })]);

    button('Add a subject').click();
    fixture.detectChanges();
    type('subject-name', 'Hindi');
    type('subject-code', 'HIN2');
    button('Save subject').click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: SUBJECTS_URL, method: 'POST' });
    created.flush(refusal('ACAD_008'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('already used by a subject that has stopped being taught');
    expect(text()).toContain('Hindi (HIN)');
  });

  // ── Editing ──────────────────────────────────────────────────────────────────────────────

  it('saves a rename without touching whether the subject is active', () => {
    arrive();

    button('Edit').click();
    fixture.detectChanges();

    type('subject-name', 'Maths');
    button('Save subject').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${SUBJECTS_URL}/sub-math`, method: 'PUT' });
    expect(request.request.body).toEqual({ name: 'Maths', code: 'MATH', active: true });
    request.flush(envelope(subject({ name: 'Maths' })));
    fixture.detectChanges();

    list().flush(envelope(page([subject({ name: 'Maths' })])));
    fixture.detectChanges();

    expect(text()).toContain('Maths was saved');
  });

  // ── Retiring and reinstating ─────────────────────────────────────────────────────────────

  it('retires a subject and announces that it can come back', () => {
    arrive();

    button('Retire').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${SUBJECTS_URL}/sub-math`, method: 'PUT' });
    expect(request.request.body).toEqual({ name: 'Mathematics', code: 'MATH', active: false });
    request.flush(envelope(subject({ active: false })));
    fixture.detectChanges();

    list().flush(envelope(page([subject({ active: false })])));
    fixture.detectChanges();

    expect(text()).toContain('has been retired');
    expect(text()).toContain('brought back at any time');
  });

  it('reinstates a retired subject', () => {
    arrive([subject({ active: false })]);

    button('Reinstate').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${SUBJECTS_URL}/sub-math`, method: 'PUT' });
    expect(request.request.body).toEqual({ name: 'Mathematics', code: 'MATH', active: true });
    request.flush(envelope(subject({ active: true })));
    fixture.detectChanges();

    list().flush(envelope(page([subject({ active: true })])));
    fixture.detectChanges();

    expect(text()).toContain('is being taught again');
  });

  // ── What the screen's actions are gated on ──────────────────────────────────────────────

  describe('write actions', () => {
    it('offers adding, editing and retiring to somebody who may manage subjects', () => {
      arrive();

      expect(button('Add a subject')).toBeTruthy();
      expect(button('Edit')).toBeTruthy();
      expect(button('Retire')).toBeTruthy();
    });

    it('offers none of them to somebody who may only read the catalogue', () => {
      signInWith(Permissions.SUBJECT_READ);
      arrive();

      expect(button('Add a subject')).toBeUndefined();
      expect(button('Edit')).toBeUndefined();
      expect(button('Retire')).toBeUndefined();
      expect(text()).toContain('Mathematics');
    });

    it('reads the live session, not a snapshot taken when the screen was built', () => {
      signInWith(Permissions.SUBJECT_READ);
      arrive();
      expect(button('Add a subject')).toBeUndefined();

      signInWith(Permissions.SUBJECT_READ, Permissions.SUBJECT_MANAGE);
      fixture.detectChanges();

      expect(button('Add a subject')).toBeTruthy();
    });
  });
});
