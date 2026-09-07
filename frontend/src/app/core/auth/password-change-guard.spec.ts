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
import { passwordChangeGuard } from './password-change-guard';

const me = (mustChangePassword: boolean): MeResponse => ({
  user: {
    id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword,
  },
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissionsVersion: '7',
  permissions: [],
  navigation: [],
});

describe('passwordChangeGuard', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Subscribes and hands back whatever the guard settles on, or undefined while it is waiting. */
  const guard = (url = '/students/1234') => {
    const outcome = TestBed.runInInjectionContext(() =>
      passwordChangeGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;

    let decision: boolean | UrlTree | undefined;
    outcome.subscribe((value) => (decision = value));
    return () => decision;
  };

  const respondWith = (response: MeResponse) => {
    httpMock
      .expectOne('/api/me')
      .flush({ success: true, timestamp: '2026-09-07T10:00:00Z', data: response });
  };

  const respondSignedOut = () => {
    httpMock.expectOne('/api/me').flush(
      {
        success: false,
        timestamp: '2026-09-07T10:00:00Z',
        error: { code: 'AUTH_002', message: 'Sign in to continue.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );
  };

  const destination = (decision: boolean | UrlTree | undefined): string | null =>
    decision instanceof UrlTree ? TestBed.inject(Router).serializeUrl(decision) : null;

  it('sends a deep link to the change-password screen when the session still owes it', () => {
    // The gap this guard closes: typing `/students/1234` directly used to get past both the login
    // screen's own redirect and `landingGuard`'s, because neither sits in front of an arbitrary
    // child route.
    const decision = guard('/students/1234');
    respondWith(me(true));

    expect(destination(decision())).toBe('/change-password');
  });

  it('lets a session with no outstanding password change through to whatever it asked for', () => {
    const decision = guard('/students/1234');
    respondWith(me(false));

    expect(decision()).toBe(true);
  });

  it('has nothing to add when there is no session at all', () => {
    // `authGuard` runs first in the same `canActivate` array and would already have redirected to
    // `/login` — this guard just has to not invent a second opinion if it somehow still ran.
    const decision = guard('/students/1234');
    respondSignedOut();

    expect(decision()).toBe(true);
  });

  it('asks once, sharing the call the shell guard already started', () => {
    const first = guard('/students/1234');
    respondWith(me(true));

    const second = guard('/students/5678');
    // There is no second request to flush; `httpMock.verify()` in afterEach fails if one was made.
    expect(destination(first())).toBe('/change-password');
    expect(destination(second())).toBe('/change-password');
  });
});
