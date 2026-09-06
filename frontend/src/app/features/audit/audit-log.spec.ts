import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuditEvent } from '../../core/api/models';
import { AuditLog } from './audit-log';

const URL = '/api/audit';

const PRIYA = '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55';
const ARJUN = '9d2b41a7-3c55-4d18-9c02-71a0f5b3ee11';

/** Invented people and an invented school. Never real staff or student data in a fixture. */
const event = (over: Partial<AuditEvent> = {}): AuditEvent => ({
  id: '018f3a10-0000-7000-8000-000000000001',
  occurredAt: '2026-09-05T09:14:00Z',
  actorId: PRIYA,
  actorName: 'Priya Sharma',
  actorRoles: ['PRINCIPAL'],
  action: 'ENTITY_UPDATED',
  outcome: 'SUCCESS',
  entityType: 'SCHOOL_PROFILE',
  entityId: 'EVG-101',
  changedFields: ['phone', 'addressLine1'],
  ipAddress: '203.0.113.7',
  userAgent: 'Mozilla/5.0 (Linux; Android 13)',
  traceId: 'a1b2c3d4',
  ...over,
});

const page = (content: readonly AuditEvent[], totalElements = content.length, number = 0) => ({
  content,
  page: number,
  size: 25,
  totalElements,
  totalPages: Math.ceil(totalElements / 25),
});

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-05T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-05T10:00:00Z',
  error: { code, message: 'Refused.' },
});

describe('AuditLog', () => {
  let fixture: ComponentFixture<AuditLog>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';
  /**
   * The one in-flight search. Called once per request: `expectOne` takes the request off the open
   * list, so asking for the same one twice fails.
   */
  const search = () => httpMock.expectOne((request) => request.url === URL);

  /** Answers the in-flight search and renders what came back. */
  const settle = (data: unknown = page([event()])) => {
    search().flush(envelope(data));
    fixture.detectChanges();
  };

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const rows = () => Array.from(element().querySelectorAll('tbody tr.row'));

  const pick = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const choose = (id: string, value: string) => {
    const select = element().querySelector(`#${id}`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  /** Creates the screen and answers its first request. */
  const arrive = (data: unknown = page([event()])) => {
    fixture = TestBed.createComponent(AuditLog);
    fixture.detectChanges();
    settle(data);
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLog],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ── Listing ──────────────────────────────────────────────────────────────────────────────

  it('shows a row the way a person reads it, not the way the database stores it', () => {
    arrive();

    expect(rows()).toHaveLength(1);
    expect(text()).toContain('Record updated');
    expect(text()).toContain('Priya Sharma');
    expect(text()).toContain('Succeeded');
    // The constant is never what a reader sees.
    expect(text()).not.toContain('ENTITY_UPDATED');
  });

  /**
   * The whole point of ADR-0014: the log holds the NAMES of the fields that changed, and no value
   * exists to pair them with. The screen has to say that and must never imply otherwise.
   */
  it('renders changed fields as names, and says so', () => {
    arrive();

    expect(text()).toContain('Fields changed');
    expect(text()).toContain('Phone');
    expect(text()).toContain('Address line 1');
    expect(text()).toContain('never the values');
  });

  /**
   * `AuditAction` is an open set of string constants so a module can name its own verb without
   * editing shared code. A verb this build has never heard of must still read as English.
   */
  it('renders an action it has never heard of legibly, and complains once in the console', () => {
    const warned = vi.spyOn(console, 'warn').mockImplementation(() => {});

    arrive(
      page([
        event({ id: 'a', action: 'STUDENT_PROMOTED' }),
        event({ id: 'b', action: 'STUDENT_PROMOTED' }),
      ]),
    );

    expect(text()).toContain('Student promoted');
    expect(text()).not.toContain('STUDENT_PROMOTED');
    // Once per unknown key, not once per row — twenty unknown rows is twenty lines, not twenty
    // thousand.
    expect(
      warned.mock.calls.filter(([first]) => String(first).includes('STUDENT_PROMOTED')).length,
    ).toBe(1);
  });

  it('says nothing has been recorded when the log is empty', () => {
    arrive(page([]));

    expect(text()).toContain('Nothing has been recorded yet');
    expect(element().querySelector('table')).toBeNull();
  });

  it('offers a retry when the log cannot be loaded', () => {
    fixture = TestBed.createComponent(AuditLog);
    fixture.detectChanges();
    search().flush(refusal('GEN_001'), { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the audit log');

    button('Try again').click();
    fixture.detectChanges();
    settle();

    expect(text()).toContain('Priya Sharma');
  });

  // ── Permission ───────────────────────────────────────────────────────────────────────────

  /**
   * There is deliberately no client-side guard (ADR-0008): the endpoint is the control, and
   * re-deriving the permission model here would be a second copy of it. Typing the URL without
   * `platform:audit:read` therefore lands here, and this is what it must look like.
   */
  it('explains a refusal calmly instead of crashing or redirecting', () => {
    fixture = TestBed.createComponent(AuditLog);
    fixture.detectChanges();
    search().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view the audit log');
    // No filters to fiddle with and no retry to hammer: neither would ever succeed.
    expect(element().querySelector('form')).toBeNull();
    expect(text()).not.toContain('Try again');
  });

  // ── The date range ───────────────────────────────────────────────────────────────────────

  /**
   * The trap. `to` is EXCLUSIVE on the API and inclusive to the user: someone who picks
   * "1 Sep to 5 Sep" means the 5th included. Sending the start of the 5th would silently drop
   * every row from the last day they asked for, and nothing on screen would say so.
   *
   * Asserted as a local calendar date rather than as a literal instant, so the test says what it
   * means on a machine in any time zone.
   */
  it('sends the start of the day AFTER the one picked, because `to` is exclusive', () => {
    arrive();

    pick('audit-from', '2026-09-01');
    settle();

    pick('audit-to', '2026-09-05');
    const request = search();
    const sent = request.request.params;

    const from = new Date(sent.get('from')!);
    expect([from.getFullYear(), from.getMonth(), from.getDate()]).toEqual([2026, 8, 1]);
    expect(from.getHours()).toBe(0);

    const to = new Date(sent.get('to')!);
    // The 6th, not the 5th — and midnight local, not midnight UTC.
    expect([to.getFullYear(), to.getMonth(), to.getDate()]).toEqual([2026, 8, 6]);
    expect(to.getHours()).toBe(0);

    request.flush(envelope(page([event()])));
    fixture.detectChanges();
  });

  it('rolls into the next month rather than inventing a 31 September', () => {
    arrive();

    pick('audit-to', '2026-09-30');
    const request = search();
    const to = new Date(request.request.params.get('to')!);
    expect([to.getFullYear(), to.getMonth(), to.getDate()]).toEqual([2026, 9, 1]);

    request.flush(envelope(page([event()])));
    fixture.detectChanges();
  });

  it('refuses a backwards range instead of asking for it', () => {
    arrive();

    pick('audit-from', '2026-09-10');
    settle();

    pick('audit-to', '2026-09-01');

    expect(text()).toContain('The end of the range is before its start');
    httpMock.expectNone((request) => request.url === URL);
  });

  it('narrows by action, and back to everything again', () => {
    arrive();

    choose('audit-action', 'LOGIN_FAILED');
    const narrowed = search();
    expect(narrowed.request.params.get('action')).toBe('LOGIN_FAILED');
    narrowed.flush(envelope(page([event({ action: 'LOGIN_FAILED', outcome: 'FAILURE' })])));
    fixture.detectChanges();

    // "Any action" is a real option, not a disabled placeholder: going back to everything is the
    // most common thing a reader does next.
    choose('audit-action', '');
    const widened = search();
    expect(widened.request.params.has('action')).toBe(false);
    widened.flush(envelope(page([event()])));
    fixture.detectChanges();
  });

  // ── The actor filter ─────────────────────────────────────────────────────────────────────

  /**
   * The API filters on `actorId`, a UUID, and there is no endpoint listing accounts to pick from.
   * The row is the picker: the id is already on screen, attached to a name the reader knows.
   */
  it('narrows to one person when their name is clicked, and shows it as a removable chip', () => {
    arrive(page([event({ id: 'a' }), event({ id: 'b', actorId: ARJUN, actorName: 'Arjun Rao' })]));

    button('Priya Sharma').click();
    fixture.detectChanges();

    const narrowed = search();
    expect(narrowed.request.params.get('actorId')).toBe(PRIYA);
    expect(narrowed.request.params.get('page')).toBe('0');
    narrowed.flush(envelope(page([event({ id: 'a' })])));
    fixture.detectChanges();

    expect(text()).toContain('Only Priya Sharma');

    (element().querySelector('.chip__remove') as HTMLButtonElement).click();
    fixture.detectChanges();

    const widened = search();
    expect(widened.request.params.has('actorId')).toBe(false);
    widened.flush(envelope(page([event({ id: 'a' })])));
    fixture.detectChanges();

    expect(text()).not.toContain('Only Priya Sharma');
  });

  /** A failed sign-in has no actor at all, so there is nothing to narrow to. */
  it('does not offer to filter by an actor that does not exist', () => {
    arrive(
      page([
        event({
          action: 'LOGIN_FAILED',
          outcome: 'FAILURE',
          actorId: undefined,
          actorName: undefined,
          actorRoles: [],
          changedFields: [],
        }),
      ]),
    );

    expect(text()).toContain('Not signed in');
    expect(element().querySelector('button.actor')).toBeNull();
  });

  // ── Details ──────────────────────────────────────────────────────────────────────────────

  it('keeps the address, device and trace id one tap away rather than in a column', () => {
    arrive();

    expect(text()).not.toContain('203.0.113.7');

    button('Details').click();
    fixture.detectChanges();

    expect(text()).toContain('203.0.113.7');
    expect(text()).toContain('Mozilla/5.0 (Linux; Android 13)');
    expect(text()).toContain('a1b2c3d4');

    const toggle = button('Hide details');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(text()).not.toContain('203.0.113.7');
  });

  // ── Paging ───────────────────────────────────────────────────────────────────────────────

  it('shows where in the log the reader is, and moves a page at a time', () => {
    const first = Array.from({ length: 25 }, (_, index) => event({ id: `first-${index}` }));
    const second = Array.from({ length: 5 }, (_, index) => event({ id: `second-${index}` }));

    arrive(page(first, 30));

    expect(text()).toContain('Showing 1–25 of 30');
    expect(button('Previous').disabled).toBe(true);

    button('Next').click();
    fixture.detectChanges();

    const request = search();
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('25');
    request.flush(envelope(page(second, 30, 1)));
    fixture.detectChanges();

    expect(text()).toContain('Showing 26–30 of 30');
    expect(button('Next').disabled).toBe(true);
    expect(button('Previous').disabled).toBe(false);
  });

  it('goes back to the first page whenever a filter changes', () => {
    const full = Array.from({ length: 25 }, (_, index) => event({ id: `row-${index}` }));
    arrive(page(full, 60));

    button('Next').click();
    fixture.detectChanges();
    settle(page(full, 60, 1));

    choose('audit-action', 'LOGIN_FAILED');

    // Page four of the previous filter is either the wrong rows or an empty page, and both look
    // like a broken screen.
    const request = search();
    expect(request.request.params.get('page')).toBe('0');
    request.flush(envelope(page([event({ action: 'LOGIN_FAILED' })], 1)));
    fixture.detectChanges();
  });
});
