import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard } from './auth-guard';
import { SessionStore } from './session-store';

const USER = {
  userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
  displayName: 'Priya Sharma',
  mustChangePassword: false,
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissions: [],
};

describe('authGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  const guard = (url: string) =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  const signIn = () => TestBed.inject(SessionStore).signedIn(USER, 'secret-one');

  it('sends someone who is not signed in to the login screen', () => {
    const result = guard('/schools');

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toContain('/login');
  });

  it('remembers where they were going, query string and all', () => {
    const result = guard('/fees/receipts?year=2026') as UrlTree;

    expect(result.queryParams['returnTo']).toBe('/fees/receipts?year=2026');
  });

  it('does not clutter the login URL when the destination was the default anyway', () => {
    const result = guard('/') as UrlTree;

    expect(result.queryParams['returnTo']).toBeUndefined();
  });

  it('lets a signed-in user through', () => {
    signIn();

    expect(guard('/schools')).toBe(true);
  });

  it('closes again the moment the session is cleared', () => {
    signIn();
    TestBed.inject(SessionStore).signedOut();

    expect(guard('/schools')).toBeInstanceOf(UrlTree);
  });
});
