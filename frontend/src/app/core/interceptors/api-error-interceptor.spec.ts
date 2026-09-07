import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AUTH_ERROR } from '../api/auth-api';
import { MeResponse } from '../api/models';
import { me } from '../auth/session-fixture';
import { SessionStore } from '../auth/session-store';
import { NavigationStore } from '../navigation/navigation-store';
import { apiErrorInterceptor } from './api-error-interceptor';

/** The envelope the backend sends for a refusal. Only `error.code` is ever branched on. */
const refusal = (code: string, message: string) => ({
  success: false,
  timestamp: '2026-09-05T10:00:00Z',
  traceId: 'test-trace',
  error: { code, message },
});

/** The envelope `GET /api/me` answers with on success. */
const meEnvelope = (response: MeResponse) => ({
  success: true,
  timestamp: '2026-09-05T10:00:00Z',
  traceId: 'test-trace-me',
  data: response,
});

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;
  let session: SessionStore;
  let navigation: NavigationStore;
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
    navigation = TestBed.inject(NavigationStore);

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
   * Answers the `GET /api/me` that ADR-0008's staleness rule fires after any 403 that is not on
   * `/api/me` itself. Every test below that fails a request with 403 owes this response before
   * `httpMock.verify()` in `afterEach` will pass.
   */
  const flushStalenessRefetch = (response: MeResponse = me()) => {
    httpMock.expectOne('/api/me').flush(meEnvelope(response));
  };

  /**
   * The client half of the forced password change. The server refuses everything but the change
   * itself; this is how a session that discovers that mid-flight gets to the screen that can fix
   * it — because it was told to, not because the client re-derived the rule.
   */
  it('sends a session that still owes its password change to the change-password screen', () => {
    failWith(403, refusal(AUTH_ERROR.PASSWORD_CHANGE_REQUIRED, 'Set a new password.'));
    flushStalenessRefetch();

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
    flushStalenessRefetch();

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
    flushStalenessRefetch();

    expect(navigatedTo).toBeNull();
  });

  /** A lost session is still a lost session, and still goes to sign in. */
  it('still sends a 401 to the login screen', () => {
    failWith(401, refusal(AUTH_ERROR.NO_SESSION, 'Please sign in to continue.'));

    expect(navigatedTo).toBe('/login');
    expect(session.isSignedIn()).toBe(false);
  });

  /**
   * ADR-0008's staleness rule: the point of the whole feature. A menu built from a permission set
   * that the server has since narrowed is corrected before the user ever sees the refusal that
   * proves it is stale.
   */
  describe('ADR-0008 staleness refetch', () => {
    it('refetches /api/me and re-renders navigation before the original error surfaces', () => {
      let failure: unknown;
      http.get('/api/students').subscribe({ error: (error: unknown) => (failure = error) });
      httpMock
        .expectOne('/api/students')
        .flush(refusal('PERM_001', 'You do not have permission to do that.'), {
          status: 403,
          statusText: 'refused',
        });

      // The error has not reached the caller yet: it is waiting on the refetch.
      expect(failure).toBeUndefined();

      flushStalenessRefetch({
        ...me(),
        permissionsVersion: 'v2',
        navigation: [{ id: 'schools', labelKey: 'nav.schools', order: 10, children: [] }],
      });

      // Now it does — the refetch delays the error, it does not replace it.
      expect(failure).toBeTruthy();
      expect(session.permissionsVersion()).toBe('v2');
      expect(navigation.items().map((link) => link.id)).toEqual(['schools']);
    });

    it('shares one refetch between two 403s that arrive together', () => {
      http.get('/api/students').subscribe({ error: () => undefined });
      http.get('/api/school/profile').subscribe({ error: () => undefined });

      httpMock
        .expectOne('/api/students')
        .flush(refusal('PERM_001', 'You do not have permission to do that.'), {
          status: 403,
          statusText: 'refused',
        });
      httpMock
        .expectOne('/api/school/profile')
        .flush(refusal('PERM_001', 'You do not have permission to do that.'), {
          status: 403,
          statusText: 'refused',
        });

      // Two 403s, one refetch: `expectOne` throws if a second `/api/me` request also went out.
      flushStalenessRefetch();
    });

    it('does not refetch when the 403 comes from /api/me itself, so it cannot recurse', () => {
      // /api/me needs only a session (see MeApi), so this should never happen in production — but
      // the guard against it recursing must hold regardless.
      failWith(403, refusal('PERM_001', 'You do not have permission to do that.'), '/api/me');

      // No second /api/me request was made — httpMock.verify() in afterEach proves it.
    });

    it('does not throw a different error when the refetch itself fails', () => {
      let failure: unknown;
      http.get('/api/students').subscribe({ error: (error: unknown) => (failure = error) });
      httpMock
        .expectOne('/api/students')
        .flush(refusal('PERM_001', 'You do not have permission to do that.'), {
          status: 403,
          statusText: 'refused',
        });

      httpMock.expectOne('/api/me').flush('upstream is down', {
        status: 502,
        statusText: 'Bad Gateway',
      });

      // Still the original 403, not the refetch's 502 and not an unhandled exception.
      expect((failure as { status?: number })?.status).toBe(403);
    });

    it('signs out and redirects to sign-in when the refetch discovers the session is gone', () => {
      session.bootstrapped(me());

      failWith(403, refusal('PERM_001', 'You do not have permission to do that.'));
      httpMock
        .expectOne('/api/me')
        .flush(refusal(AUTH_ERROR.NO_SESSION, 'Please sign in to continue.'), {
          status: 401,
          statusText: 'Unauthorized',
        });

      expect(session.isSignedIn()).toBe(false);
      expect(navigatedTo).toBe('/login');
    });

    it('still shows the error when the refetch comes back with the same permissions version', () => {
      session.bootstrapped(me());
      const unchanged = session.permissionsVersion();

      let failure: unknown;
      http.get('/api/students').subscribe({ error: (error: unknown) => (failure = error) });
      httpMock
        .expectOne('/api/students')
        .flush(refusal('PERM_001', 'You do not have permission to do that.'), {
          status: 403,
          statusText: 'refused',
        });

      flushStalenessRefetch({
        ...me(),
        permissionsVersion: unchanged ?? 'test-permissions-version',
      });

      // Same version means the same permission set — a genuine denial, not staleness — and the
      // error still reaches the caller either way (see the comment in the interceptor for why
      // this app does not reword the message for this case).
      expect(failure).toBeTruthy();
    });
  });
});
