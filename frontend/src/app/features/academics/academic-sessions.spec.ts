import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AcademicSession } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { AcademicSessions } from './academic-sessions';

const SESSIONS_URL = '/api/academics/sessions';

/** Invented years for an invented school. Never real school data in a fixture. */
const CURRENT: AcademicSession = {
  id: '018f3a10-0000-7000-8000-00000000a001',
  name: '2026–27',
  startsOn: '2026-04-01',
  endsOn: '2027-03-31',
  current: true,
};

const PREVIOUS: AcademicSession = {
  id: '018f3a10-0000-7000-8000-00000000a002',
  name: '2025–26',
  startsOn: '2025-04-01',
  endsOn: '2026-03-31',
  current: false,
};

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

describe('AcademicSessions', () => {
  let fixture: ComponentFixture<AcademicSessions>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const list = () => httpMock.expectOne({ url: SESSIONS_URL, method: 'GET' });

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement | undefined;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  /** Creates the screen and answers its first request. */
  const arrive = (sessions: readonly AcademicSession[] = [CURRENT, PREVIOUS]) => {
    fixture = TestBed.createComponent(AcademicSessions);
    fixture.detectChanges();
    list().flush(envelope(sessions));
    fixture.detectChanges();
  };

  /** Answers the quiet refetch every successful write makes. */
  const settleRefresh = (sessions: readonly AcademicSession[]) => {
    list().flush(envelope(sessions));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AcademicSessions],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // The default for every test below: somebody who may do the things this screen offers.
    // The tests that care sign a different user in instead, and none of them mocks the
    // permission check — the real `SessionStore` is what the templates read.
    signInWith(Permissions.SESSION_READ, Permissions.SESSION_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ── Listing ──────────────────────────────────────────────────────────────────────────────

  /**
   * The most consequential thing on the screen. Said at the top, where it is legible without
   * scrolling past eight years of sessions, and again on the row itself — and never in colour
   * alone, so it survives a photocopy and a colour-blind reader.
   */
  it('makes the current session unmistakable, at the top and on its row', () => {
    arrive();

    expect(text()).toContain('Current session: 2026–27');
    expect(text()).toContain('1 Apr 2026 – 31 Mar 2027');

    const currentRow = element().querySelector('.session--current');
    expect(currentRow?.textContent).toContain('2026–27');
    expect(currentRow?.textContent).toContain('Current session');

    // The one that is current cannot be made current again.
    expect(currentRow?.textContent).not.toContain('Make current');
  });

  /**
   * A date is a calendar day the school picked, not an instant. Parsing `2026-04-01` with
   * `new Date(string)` reads it as UTC and shifts it by the viewer's offset — five and a half
   * hours in India, which is enough to render a session that starts on 31 March.
   */
  it('renders a date as the day it says, not the day a UTC parse would make it', () => {
    arrive([{ ...CURRENT, startsOn: '2026-04-01', endsOn: '2027-03-31' }]);

    expect(text()).toContain('1 Apr 2026');
    expect(text()).toContain('31 Mar 2027');
  });

  it('says a school with no sessions has none, and what to do about it', () => {
    arrive([]);

    expect(text()).toContain('No academic sessions yet');
    expect(element().querySelector('.sessions')).toBeNull();
  });

  /**
   * A real state on a school that has added a year but not switched to it. Nothing academic can be
   * recorded until one is current, so an absence is worth a sentence rather than silence.
   */
  it('warns when there are sessions but none of them is current', () => {
    arrive([{ ...CURRENT, current: false }, PREVIOUS]);

    expect(text()).toContain('No session is current');
    expect(element().querySelector('.session--current')).toBeNull();
  });

  it('offers a retry when the list cannot be loaded', () => {
    fixture = TestBed.createComponent(AcademicSessions);
    fixture.detectChanges();
    list().flush(refusal('GEN_001'), { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the academic sessions');

    button('Try again')!.click();
    fixture.detectChanges();
    list().flush(envelope([CURRENT]));
    fixture.detectChanges();

    expect(text()).toContain('2026–27');
  });

  /**
   * There is deliberately no client-side guard (ADR-0008): the endpoint is the control, and
   * re-deriving the permission model here would be a second copy of it.
   */
  it('explains a refusal calmly instead of crashing or redirecting', () => {
    fixture = TestBed.createComponent(AcademicSessions);
    fixture.detectChanges();
    list().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view academic sessions');
    // Nothing to fiddle with and no retry to hammer: neither would ever succeed.
    expect(button('Add a session')).toBeUndefined();
    expect(text()).not.toContain('Try again');
  });

  // ── Creating and editing ─────────────────────────────────────────────────────────────────

  it('adds a session and re-reads the list the server actually holds', () => {
    arrive([PREVIOUS]);

    button('Add a session')!.click();
    fixture.detectChanges();

    type('session-name', '2026–27');
    type('session-starts', '2026-04-01');
    type('session-ends', '2027-03-31');

    button('Save session')!.click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: SESSIONS_URL, method: 'POST' });
    expect(created.request.body).toEqual({
      name: '2026–27',
      startsOn: '2026-04-01',
      endsOn: '2027-03-31',
    });
    // `current` is never sent on a create or an edit: switching the school on to a year is its own
    // endpoint, so fixing a typo cannot move everybody by accident.
    expect(created.request.body).not.toHaveProperty('current');
    created.flush(envelope({ ...CURRENT, current: false }));
    fixture.detectChanges();

    // The list is ordered by the server, and only the server knows where a newly dated session
    // belongs in it.
    settleRefresh([{ ...CURRENT, current: false }, PREVIOUS]);

    expect(text()).toContain('2026–27 added.');
    expect(element().querySelectorAll('.session')).toHaveLength(2);
  });

  it('edits a session in place', () => {
    arrive([CURRENT]);

    button('Edit')!.click();
    fixture.detectChanges();

    // The editor opens on what is already there rather than empty.
    expect((element().querySelector('#session-name') as HTMLInputElement).value).toBe('2026–27');

    type('session-name', '2026-27 (revised)');
    button('Save session')!.click();
    fixture.detectChanges();

    const saved = httpMock.expectOne({
      url: `${SESSIONS_URL}/${CURRENT.id}`,
      method: 'PUT',
    });
    expect(saved.request.body.name).toBe('2026-27 (revised)');
    saved.flush(envelope({ ...CURRENT, name: '2026-27 (revised)' }));
    fixture.detectChanges();
    settleRefresh([{ ...CURRENT, name: '2026-27 (revised)' }]);

    expect(text()).toContain('2026-27 (revised)');
  });

  it('refuses a session that ends before it starts, without asking the server', () => {
    arrive([]);

    button('Add a session')!.click();
    fixture.detectChanges();

    type('session-name', '2026–27');
    type('session-starts', '2027-03-31');
    type('session-ends', '2026-04-01');

    button('Save session')!.click();
    fixture.detectChanges();

    expect(text()).toContain('A session has to end after it starts');
    httpMock.expectNone({ url: SESSIONS_URL, method: 'POST' });
  });

  it('refuses a name longer than the backend will take', () => {
    arrive([]);

    button('Add a session')!.click();
    fixture.detectChanges();

    type('session-name', 'x'.repeat(41));
    type('session-starts', '2026-04-01');
    type('session-ends', '2027-03-31');

    button('Save session')!.click();
    fixture.detectChanges();

    expect(text()).toContain('40 characters or fewer');
    httpMock.expectNone({ url: SESSIONS_URL, method: 'POST' });
  });

  /**
   * The backend keys `details` by field name (ADR-0007), and those names are these control names —
   * which is what lets each message go under the control it belongs to instead of one sentence at
   * the top with the user left to find the field.
   */
  it('puts a refused field back under the field that was refused', () => {
    arrive([]);

    button('Add a session')!.click();
    fixture.detectChanges();
    type('session-name', '2026–27');
    type('session-starts', '2026-04-01');
    type('session-ends', '2027-03-31');
    button('Save session')!.click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: SESSIONS_URL, method: 'POST' })
      .flush(refusal('VAL_001', { name: 'A session with this name already exists.' }), {
        status: 400,
        statusText: 'Bad Request',
      });
    fixture.detectChanges();

    expect(text()).toContain('A session with this name already exists.');
    // The editor stays open on what was typed, so nothing has to be entered twice.
    expect((element().querySelector('#session-name') as HTMLInputElement).value).toBe('2026–27');
  });

  /**
   * The cross-field rule comes back keyed on `endsOn` — named after a field this form has rather
   * than after the rule — and under its own code rather than `VAL_001`. A screen that branched on
   * the code alone would answer a date the user can see and fix with "check your connection".
   */
  it('shows a refused date range against the date box, whatever code carried it', () => {
    arrive([]);

    button('Add a session')!.click();
    fixture.detectChanges();
    type('session-name', '2026–27');
    type('session-starts', '2026-04-01');
    type('session-ends', '2027-03-31');
    button('Save session')!.click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: SESSIONS_URL, method: 'POST' })
      .flush(refusal('ACAD_002', { endsOn: 'A session must end after it starts.' }), {
        status: 422,
        statusText: 'Unprocessable Content',
      });
    fixture.detectChanges();

    expect(text()).toContain('A session must end after it starts.');
    expect(text()).toContain('Some of these details were refused');
    expect(text()).not.toContain('Check your connection');
  });

  it('leaves the editor without saving, and without losing the row it was on', () => {
    arrive([CURRENT]);

    button('Edit')!.click();
    fixture.detectChanges();
    type('session-name', 'half typed');

    button('Cancel')!.click();
    fixture.detectChanges();

    expect(text()).toContain('2026–27');
    expect(element().querySelector('#session-name')).toBeNull();
    httpMock.expectNone({ url: `${SESSIONS_URL}/${CURRENT.id}`, method: 'PUT' });
  });

  // ── Making one current ───────────────────────────────────────────────────────────────────

  /**
   * The change that moves the whole school on to a different year. A misplaced tap on a phone must
   * not be able to do it, and the question has to say what it will do — which is the whole reason
   * this is `cb-dialog` and not `window.confirm`, whose button says "OK".
   */
  it('asks before moving the school on to another session, and says what that means', () => {
    arrive([CURRENT, PREVIOUS]);

    button('Make current')!.click();
    fixture.detectChanges();

    const dialog = element().querySelector('.dialog__panel');
    expect(dialog).not.toBeNull();
    expect(dialog?.textContent).toContain('Make 2025–26 the current session?');
    expect(dialog?.textContent).toContain('Everyone at this school will be working in');
    // The one that is losing it is named too, along with the reassurance that it is reversible.
    expect(dialog?.textContent).toContain('2026–27 stops being current');
    expect(dialog?.textContent).toContain('Make it current');

    // Nothing has been asked of the server yet.
    httpMock.expectNone({ url: `${SESSIONS_URL}/${PREVIOUS.id}/current`, method: 'POST' });
  });

  it('does nothing at all when the question is declined', () => {
    arrive([CURRENT, PREVIOUS]);

    button('Make current')!.click();
    fixture.detectChanges();
    button('Leave it as it is')!.click();
    fixture.detectChanges();

    expect(element().querySelector('.dialog__panel')).toBeNull();
    expect(text()).toContain('Current session: 2026–27');
    httpMock.expectNone({ url: `${SESSIONS_URL}/${PREVIOUS.id}/current`, method: 'POST' });
  });

  it('switches the school over once the question is answered', () => {
    arrive([CURRENT, PREVIOUS]);

    button('Make current')!.click();
    fixture.detectChanges();
    button('Make it current')!.click();
    fixture.detectChanges();

    const switched = httpMock.expectOne({
      url: `${SESSIONS_URL}/${PREVIOUS.id}/current`,
      method: 'POST',
    });
    // Two rows changed, not one — the server cleared the previous session in the same transaction
    // — so the endpoint answers with the whole list, and that list is what gets rendered. No
    // second request: patching only the row that was named would leave two sessions both showing
    // as current.
    switched.flush(
      envelope([
        { ...CURRENT, current: false },
        { ...PREVIOUS, current: true },
      ]),
    );
    fixture.detectChanges();

    expect(text()).toContain('Current session: 2025–26');
    expect(text()).toContain('2025–26 is now the current session.');
    expect(element().querySelector('.dialog__panel')).toBeNull();
  });

  it('leaves the school where it was when the switch fails, and says so', () => {
    arrive([CURRENT, PREVIOUS]);

    button('Make current')!.click();
    fixture.detectChanges();
    button('Make it current')!.click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `${SESSIONS_URL}/${PREVIOUS.id}/current`, method: 'POST' })
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to change the current session');
    // Still on the year it was on, and no refetch was made to discover that.
    expect(text()).toContain('Current session: 2026–27');
    expect(element().querySelector('.dialog__panel')).toBeNull();
  });

  // ── What the write actions are gated on ──────────────────────────────────────────────────

  describe('write actions', () => {
    const control = (id: string) => element().querySelector(`#${id}`);

    it('offers adding, editing and switching year to somebody who may manage sessions', () => {
      arrive();

      expect(control('session-add')).not.toBeNull();
      expect(control(`session-edit-${PREVIOUS.id}`)).not.toBeNull();
      expect(control(`session-current-${PREVIOUS.id}`)).not.toBeNull();
    });

    /**
     * The reported defect, as a test. A classteacher holds `academics:session:read`, reaches this
     * screen from the menu the server built for them, and was offered a form whose submit is a
     * 403.
     */
    it('offers none of them to a classteacher who may only read the years', () => {
      signInWith(Permissions.SESSION_READ);
      arrive();

      expect(control('session-add')).toBeNull();
      expect(control(`session-edit-${PREVIOUS.id}`)).toBeNull();
      expect(control(`session-current-${PREVIOUS.id}`)).toBeNull();
      // The list and which year is current is the whole of what the read entitles them to.
      expect(text()).toContain('2026–27');
    });

    it('reads the live session, not a snapshot taken when the screen was built', () => {
      signInWith(Permissions.SESSION_READ);
      arrive();
      expect(control('session-add')).toBeNull();

      signInWith(Permissions.SESSION_READ, Permissions.SESSION_MANAGE);
      fixture.detectChanges();

      expect(control('session-add')).not.toBeNull();
    });
  });
});
