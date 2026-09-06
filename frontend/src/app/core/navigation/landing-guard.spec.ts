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
import { MeResponse, NavigationItem } from '../api/models';
import { landingGuard } from './landing-guard';

/**
 * These use the real route registry rather than a stand-in, deliberately.
 *
 * The ids below — `audit`, `students`, `students.all`, `academics.sessions` — are ids the backend
 * actually emits, and the question this guard answers is whether *this build* can open them. A
 * fake registry would answer a question nobody asked.
 */
const navItem = (over: Partial<NavigationItem> & Pick<NavigationItem, 'id'>): NavigationItem => ({
  labelKey: `nav.${over.id}`,
  order: 10,
  children: [],
  ...over,
});

const me = (over: Partial<MeResponse> = {}): MeResponse => ({
  user: {
    id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword: false,
  },
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissionsVersion: '7',
  permissions: [],
  navigation: [],
  ...over,
});

describe('landingGuard', () => {
  let httpMock: HttpTestingController;
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    // The store logs every id it cannot route. Two of these tests hand it one on purpose.
    warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => {
    httpMock.verify();
    warn.mockRestore();
  });

  /** Subscribes and hands back whatever the guard settles on, or undefined while it is waiting. */
  const landOn = () => {
    const outcome = TestBed.runInInjectionContext(() =>
      landingGuard({} as ActivatedRouteSnapshot, { url: '/' } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;

    let decision: boolean | UrlTree | undefined;
    outcome.subscribe((value) => (decision = value));
    return () => decision;
  };

  const respondWith = (response: MeResponse) => {
    httpMock
      .expectOne('/api/me')
      .flush({ success: true, timestamp: '2026-09-06T10:00:00Z', data: response });
  };

  /** What the router would actually navigate to, or null when the guard rendered in place. */
  const destination = (decision: boolean | UrlTree | undefined): string | null =>
    decision instanceof UrlTree ? TestBed.inject(Router).serializeUrl(decision) : null;

  it('sends an auditor to the one screen an auditor has', () => {
    // The defect this whole change exists for. The auditor holds `platform:audit:read` and
    // nothing else, so the server sends them one item — and the old `redirectTo: 'students'`
    // greeted them with a 403 and a console full of errors as their first screen after signing in.
    const decision = landOn();
    respondWith(
      me({ permissions: ['platform:audit:read'], navigation: [navItem({ id: 'audit' })] }),
    );

    expect(destination(decision())).toBe('/audit');
  });

  it('lands on the first leaf under a container, not on the container', () => {
    const decision = landOn();
    respondWith(
      me({
        navigation: [
          navItem({
            id: 'academics',
            // Ordered by the server, and the order is the point: a class means little until there
            // is a year to enrol anybody into, so sessions is the one it names first.
            children: [
              navItem({ id: 'academics.classes', order: 20 }),
              navItem({ id: 'academics.sessions', order: 10 }),
            ],
          }),
        ],
      }),
    );

    // `/academics` is a heading that only redirects. The user lands on a screen.
    expect(destination(decision())).toBe('/academics/sessions');
  });

  it('lands on the first item of the menu the server sent, whatever that is', () => {
    const decision = landOn();
    respondWith(
      me({
        navigation: [
          navItem({ id: 'settings', order: 90, children: [navItem({ id: 'settings.profile' })] }),
          navItem({
            id: 'students',
            order: 20,
            children: [
              navItem({ id: 'students.all', order: 10 }),
              navItem({ id: 'students.guardians', order: 20 }),
            ],
          }),
        ],
      }),
    );

    // Not because "students" is what a school opens the product to do — that was the last guess.
    // Because it is what this user's own menu puts first.
    expect(destination(decision())).toBe('/students');
  });

  it('ignores an item this build cannot open and takes the next one that resolves', () => {
    const decision = landOn();
    respondWith(
      me({
        navigation: [
          // Emitted by a backend that switched a module on before this build shipped its screens.
          navItem({ id: 'hostel', order: 10 }),
          navItem({ id: 'audit', order: 20 }),
        ],
      }),
    );

    expect(destination(decision())).toBe('/audit');
  });

  it('stays put and explains itself when nothing in the menu resolves', () => {
    const decision = landOn();
    respondWith(me({ navigation: [navItem({ id: 'hostel' })] }));

    // True, not a redirect: the empty route renders `NoDestination`, which says why it is empty.
    // A fallback URL here would be the hardcoded landing page this change removed.
    expect(decision()).toBe(true);
  });

  it('stays put when the menu came back empty', () => {
    const decision = landOn();
    respondWith(me({ navigation: [] }));

    expect(decision()).toBe(true);
  });

  it('waits for the menu instead of racing it to a default', () => {
    const decision = landOn();

    // The tree arrives with `/api/me`, and until it does there is no honest answer. Deciding here
    // is how a landing page becomes a constant again.
    expect(decision()).toBeUndefined();

    respondWith(me({ navigation: [navItem({ id: 'audit' })] }));

    expect(destination(decision())).toBe('/audit');
  });

  it('sends someone with a temporary password to change it, menu or no menu', () => {
    const decision = landOn();
    respondWith(
      me({
        user: {
          id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
          displayName: 'Priya Sharma',
          mustChangePassword: true,
        },
        navigation: [navItem({ id: 'audit' })],
      }),
    );

    expect(destination(decision())).toBe('/change-password');
  });

  it('sends someone with a temporary password to change it even with nowhere else to go', () => {
    const decision = landOn();
    respondWith(
      me({
        user: {
          id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
          displayName: 'Priya Sharma',
          mustChangePassword: true,
        },
        navigation: [],
      }),
    );

    expect(destination(decision())).toBe('/change-password');
  });

  it('asks once, sharing the call the shell guard already started', () => {
    const first = landOn();
    respondWith(me({ navigation: [navItem({ id: 'audit' })] }));

    const second = landOn();
    // There is no second request to flush; `httpMock.verify()` in afterEach fails if one was made.
    expect(destination(first())).toBe('/audit');
    expect(destination(second())).toBe('/audit');
  });

  it('explains itself rather than redirecting when the menu could not be fetched at all', () => {
    const decision = landOn();
    httpMock
      .expectOne('/api/me')
      .flush(
        { success: false, timestamp: '2026-09-06T10:00:00Z' },
        { status: 503, statusText: 'Service Unavailable' },
      );

    // No session and no menu: the shell's own guard is what sends this person to sign in. This
    // guard has nothing to add and must not invent a destination.
    expect(decision()).toBe(true);
  });
});
