import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AUTH_ERROR } from '../api/auth-api';
import { SessionStore } from '../auth/session-store';
import { apiErrorInterceptor } from './api-error-interceptor';

/** The envelope the backend sends for a refusal. Only `error.code` is ever branched on. */
const refusal = (code: string, message: string) => ({
  success: false,
  timestamp: '2026-09-05T10:00:00Z',
  traceId: 'test-trace',
  error: { code, message },
});

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;
  let session: SessionStore;
  let navigatedTo: string | null;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    session = TestBed.inject(SessionStore);

    // The router is not exercised for real here: these tests are about which destination the
    // interceptor chooses, and a real navigation would need every lazy route to load.
    navigatedTo = null;
    vi.spyOn(router, 'navigateByUrl').mockImplementation((url) => {
      navigatedTo = String(url);
      return Promise.resolve(true);
    });
    vi.spyOn(router, 'navigate').mockImplementation((commands) => {
      navigatedTo = (commands as unknown[]).join('/');
      return Promise.resolve(true);
    });
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  const failWith = (status: number, body: object, url = '/api/students') => {
    http.get(url).subscribe({ error: () => undefined });
    httpMock.expectOne(url).flush(body, { status, statusText: 'refused' });
  };

  /**
   * The client half of the forced password change. The server refuses everything but the change
   * itself; this is how a session that discovers that mid-flight gets to the screen that can fix
   * it — because it was told to, not because the client re-derived the rule.
   */
  it('sends a session that still owes its password change to the change-password screen', () => {
    failWith(403, refusal(AUTH_ERROR.PASSWORD_CHANGE_REQUIRED, 'Set a new password.'));

    expect(navigatedTo).toBe('/change-password');
  });

  /**
   * The session is real and still usable for the one thing that matters. Clearing it would send
   * the user to a login screen where the same temporary password signs them straight back in, into
   * the same refusal.
   */
  it('does not sign the session out when the refusal is the forced password change', () => {
    session.signedIn(
      {
        userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
        displayName: 'Arun Shetty',
        mustChangePassword: true,
        school: { code: 'GPS-S12', name: 'Greenfield Public School' },
        permissions: [],
      },
      'Temporary#2026',
    );

    failWith(403, refusal(AUTH_ERROR.PASSWORD_CHANGE_REQUIRED, 'Set a new password.'));

    expect(session.isSignedIn()).toBe(true);
    // Still held, so the change-password screen can submit it as `currentPassword` rather than
    // making someone retype a password they were handed on a slip of paper.
    expect(session.temporaryPassword()).toBe('Temporary#2026');
  });

  /**
   * The distinction the new code exists for. An ordinary permission denial means "ask your school
   * for this permission" and the screen explains it in place; redirecting it to a password form
   * would be nonsense.
   */
  it('leaves an ordinary permission denial where it is', () => {
    failWith(403, refusal('PERM_001', 'You do not have permission to do that.'));

    expect(navigatedTo).toBeNull();
  });

  /** A lost session is still a lost session, and still goes to sign in. */
  it('still sends a 401 to the login screen', () => {
    failWith(401, refusal(AUTH_ERROR.NO_SESSION, 'Please sign in to continue.'));

    expect(navigatedTo).toBe('/login');
    expect(session.isSignedIn()).toBe(false);
  });
});
