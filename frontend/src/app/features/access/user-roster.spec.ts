import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { UserSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { SessionStore } from '../../core/auth/session-store';
import { UserRoster } from './user-roster';

const USERS_URL = '/api/access/users';

/** The id `session-fixture.ts` signs a test session in as — this account is "you" in these specs. */
const SELF_ID = '018f3a10-0000-7000-8000-0000000000ff';

const account = (over: Partial<UserSummary> = {}): UserSummary => ({
  id: 'acct-priya',
  displayName: 'Priya Sharma',
  status: 'ACTIVE',
  ...over,
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

describe('UserRoster', () => {
  let fixture: ComponentFixture<UserRoster>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const list = () =>
    httpMock.expectOne((request) => request.url === USERS_URL && request.method === 'GET');

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  /**
   * The dialog's own confirm button, scoped away from the row button that opened it — both are
   * legitimately labelled "Deactivate" / "Reset password", the row's own verb and the dialog's
   * confirmation of it, so a plain text search would find whichever renders first in the DOM.
   */
  const confirmDialog = () =>
    (
      element().querySelector('.dialog__panel .dialog__confirm button') as HTMLButtonElement
    ).click();

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const arrive = (rows: readonly UserSummary[] = [account()]) => {
    fixture = TestBed.createComponent(UserRoster);
    fixture.detectChanges();
    list().flush(envelope(rows));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserRoster],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    signInWith(Permissions.USER_READ, Permissions.USER_MANAGE, Permissions.ROLE_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists the roster', () => {
    arrive([account(), account({ id: 'acct-2', displayName: 'Rohan Gupta', status: 'DISABLED' })]);

    expect(text()).toContain('Priya Sharma');
    expect(text()).toContain('Rohan Gupta');
    expect(text()).toContain('Deactivated');
  });

  it('explains a 403 rather than crashing', () => {
    fixture = TestBed.createComponent(UserRoster);
    fixture.detectChanges();
    list().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view user accounts');
  });

  it('marks the signed-in account as "You"', () => {
    arrive([account({ id: SELF_ID, displayName: 'Test Teacher' })]);

    expect(text()).toContain('You');
  });

  // ── Creating an account ──────────────────────────────────────────────────────────────────

  it('creates an account and reveals the temporary password exactly once', () => {
    arrive();

    button('Add an account').click();
    fixture.detectChanges();

    type('account-username', 'rohan.gupta');
    type('account-display-name', 'Rohan Gupta');
    button('Create account').click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: USERS_URL, method: 'POST' });
    expect(created.request.body).toEqual({ username: 'rohan.gupta', displayName: 'Rohan Gupta' });
    created.flush(
      envelope({
        id: 'acct-2',
        username: 'rohan.gupta',
        displayName: 'Rohan Gupta',
        temporaryPassword: 'Sw1ft!Correct-Horse',
      }),
    );
    fixture.detectChanges();

    expect(text()).toContain('Sw1ft!Correct-Horse');
    expect(text()).toContain('will not be shown again');

    // The reveal panel is what refetches the roster, not the create call itself.
    list().flush(envelope([account(), account({ id: 'acct-2', displayName: 'Rohan Gupta' })]));
    fixture.detectChanges();

    button("I've saved this password").click();
    fixture.detectChanges();

    // Gone once acknowledged: nothing on screen still shows it.
    expect(text()).not.toContain('Sw1ft!Correct-Horse');
  });

  it('AUTH_009: says the username is taken, on the field, not the page', () => {
    arrive();

    button('Add an account').click();
    fixture.detectChanges();
    type('account-username', 'priya.sharma');
    type('account-display-name', 'Priya Sharma');
    button('Create account').click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: USERS_URL, method: 'POST' })
      .flush(refusal('AUTH_009'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('already in use at this school');
  });

  // ── Deactivate ────────────────────────────────────────────────────────────────────────────

  it('confirms before deactivating, then refreshes the roster', () => {
    arrive();

    button('Deactivate').click();
    fixture.detectChanges();
    expect(text()).toContain('ends every session');

    confirmDialog(); // the dialog's own confirm button
    fixture.detectChanges();

    httpMock.expectOne({ url: `${USERS_URL}/acct-priya/deactivate`, method: 'POST' }).flush(
      envelope({
        id: 'acct-priya',
        displayName: 'Priya Sharma',
        status: 'DISABLED',
        mustChangePassword: false,
      }),
    );
    fixture.detectChanges();

    list().flush(envelope([account({ status: 'DISABLED' })]));
    fixture.detectChanges();

    expect(text()).toContain('account has been deactivated');
  });

  it('AUTH_010: words the guard as protection, not a failure', () => {
    arrive();

    button('Deactivate').click();
    fixture.detectChanges();
    confirmDialog();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `${USERS_URL}/acct-priya/deactivate`, method: 'POST' })
      .flush(refusal('AUTH_010'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    // Worded as the guard it is, not "you do not have permission" — this admin does.
    expect(text()).toContain('This would leave nobody able to manage access');
    expect(text()).not.toContain('do not have permission');
  });

  it('signs itself out immediately when an admin deactivates their own account', () => {
    arrive([account({ id: SELF_ID, displayName: 'Test Teacher' })]);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const signedOut = vi.spyOn(TestBed.inject(SessionStore), 'signedOut');

    button('Deactivate').click();
    fixture.detectChanges();
    expect(text()).toContain('This is your own account');

    confirmDialog();
    fixture.detectChanges();

    httpMock.expectOne({ url: `${USERS_URL}/${SELF_ID}/deactivate`, method: 'POST' }).flush(
      envelope({
        id: SELF_ID,
        displayName: 'Test Teacher',
        status: 'DISABLED',
        mustChangePassword: false,
      }),
    );
    fixture.detectChanges();

    expect(signedOut).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // ── Reset password ───────────────────────────────────────────────────────────────────────

  it('reveals a reset password once and signs the admin out if it was their own', () => {
    arrive([account({ id: SELF_ID, displayName: 'Test Teacher' })]);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    button('Reset password').click();
    fixture.detectChanges();
    expect(text()).toContain('You will be signed out immediately');

    confirmDialog();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `${USERS_URL}/${SELF_ID}/reset-password`, method: 'POST' })
      .flush(envelope({ id: SELF_ID, temporaryPassword: 'N3w-Temp-Pass!' }));
    fixture.detectChanges();

    expect(text()).toContain('N3w-Temp-Pass!');

    button("I've saved this password").click();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  // ── Unlock ────────────────────────────────────────────────────────────────────────────────

  it('clears a lockout without asking for confirmation', () => {
    arrive();

    button('Clear lockout').click();
    fixture.detectChanges();

    httpMock.expectOne({ url: `${USERS_URL}/acct-priya/unlock`, method: 'POST' }).flush(
      envelope({
        id: 'acct-priya',
        displayName: 'Priya Sharma',
        status: 'ACTIVE',
        mustChangePassword: false,
      }),
    );
    fixture.detectChanges();

    expect(text()).toContain('lockout');
    expect(text()).toContain('cleared');
  });
});
