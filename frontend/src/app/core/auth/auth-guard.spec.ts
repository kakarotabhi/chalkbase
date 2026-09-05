import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable } from 'rxjs';
import { MeResponse } from '../api/models';
import { NavigationStore } from '../navigation/navigation-store';
import { authGuard } from './auth-guard';
import { SessionStore } from './session-store';

const ME: MeResponse = {
  user: {
    id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword: false,
  },
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissionsVersion: '7',
  permissions: ['school:school:read'],
  navigation: [{ id: 'schools', labelKey: 'nav.schools', icon: null, order: 10, children: [] }],
};

describe('authGuard', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Subscribes and hands back whatever the guard settles on — it now answers asynchronously. */
  const guard = (url: string) => {
    const outcome = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;

    let decision: boolean | UrlTree | undefined;
    outcome.subscribe((value) => (decision = value));
    return () => decision;
  };

  const respondWithSession = () => {
    httpMock
      .expectOne('/api/me')
      .flush({ success: true, timestamp: '2026-09-05T10:00:00Z', data: ME });
  };

  const respondSignedOut = () => {
    httpMock.expectOne('/api/me').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: 'AUTH_002', message: 'Sign in to continue.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );
  };

  it('admits the user when the server says there is a session', () => {
    const decision = guard('/schools');
    respondWithSession();

    expect(decision()).toBe(true);
  });

  it('seeds the session and the menu from the same answer', () => {
    guard('/schools');
    respondWithSession();

    expect(TestBed.inject(SessionStore).user()?.displayName).toBe('Priya Sharma');
    expect(TestBed.inject(SessionStore).permissionsVersion()).toBe('7');
    expect(
      TestBed.inject(NavigationStore)
        .items()
        .map((item) => item.id),
    ).toEqual(['schools']);
  });

  it('sends someone with no session to the login screen', () => {
    const decision = guard('/schools');
    respondSignedOut();

    expect(decision()).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(decision() as UrlTree)).toContain('/login');
  });

  it('remembers where they were going, query string and all', () => {
    const decision = guard('/fees/receipts?year=2026');
    respondSignedOut();

    expect((decision() as UrlTree).queryParams['returnTo']).toBe('/fees/receipts?year=2026');
  });

  it('does not clutter the login URL when the destination was the default anyway', () => {
    const decision = guard('/');
    respondSignedOut();

    expect((decision() as UrlTree).queryParams['returnTo']).toBeUndefined();
  });

  it('asks the server once, not on every navigation', () => {
    guard('/schools');
    respondWithSession();

    const second = guard('/schools/new');
    // There is no second request to flush. `httpMock.verify()` in afterEach fails if one was made.
    expect(second()).toBe(true);
  });

  it('asks again once the session has been cleared', () => {
    guard('/schools');
    respondWithSession();

    TestBed.inject(SessionStore).signedOut();
    const decision = guard('/schools');
    respondSignedOut();

    expect(decision()).toBeInstanceOf(UrlTree);
  });

  it('keeps a signed-in user in place when the bootstrap call merely fails', () => {
    // A 503 or a dead connection is not evidence that the session ended. Signing a working user
    // out over a blip would send them to a login screen they cannot reach either.
    guard('/schools');
    respondWithSession();
    TestBed.inject(NavigationStore).clear();

    const decision = guard('/schools');
    httpMock
      .expectOne('/api/me')
      .flush(
        { success: false, timestamp: '2026-09-05T10:00:00Z' },
        { status: 503, statusText: 'Service Unavailable' },
      );

    expect(decision()).toBe(true);
  });
});
