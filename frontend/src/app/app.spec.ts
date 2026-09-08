import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, Routes, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { MeResponse } from './core/api/models';
import { SessionBootstrap } from './core/auth/session-bootstrap';

const ME: MeResponse = {
  user: {
    id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword: false,
  },
  school: { code: 'GPS-S12', name: 'Greenfield Public School', timezone: 'Asia/Kolkata' },
  permissionsVersion: '7',
  permissions: [],
  navigation: [],
};

/** Stands in for the sign-in screen: a real route, with no guard in front of it. */
@Component({
  selector: 'cb-login-stub',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p>Sign in to Chalkbase</p>',
})
class LoginStub {}

/**
 * What the root component does while nobody knows yet who is signed in.
 *
 * The defect these cover: `authGuard` blocks the shell route, the router renders nothing for a
 * route it has not activated, and `App` was a bare outlet — so a slow `/api/me` was a blank white
 * page for as long as the call took, measured at 121 seconds against a cold API. Everything here
 * is asserted through the rendered text, because "what did the user see" is the whole question.
 *
 * Fake timers throughout: the boot state is defined by *when* it appears, and a real-timer test of
 * a 600ms delay would pass or fail depending on how busy the machine is.
 */
describe('App', () => {
  let fixture: ComponentFixture<App>;
  let httpMock: HttpTestingController;

  const text = () => ((fixture.nativeElement as HTMLElement).textContent ?? '').trim();

  /** Boots the app the way `app.config.ts` does: the bootstrap call starts, nobody waits for it. */
  const boot = (routes: Routes = []) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    httpMock = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionBootstrap).start();

    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
  };

  const wait = (ms: number) => {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
  };

  const respondWithSession = () => {
    httpMock
      .expectOne('/api/me')
      .flush({ success: true, timestamp: '2026-09-06T10:00:00Z', data: ME });
    fixture.detectChanges();
  };

  const respondSignedOut = () => {
    httpMock.expectOne('/api/me').flush(
      {
        success: false,
        timestamp: '2026-09-06T10:00:00Z',
        error: { code: 'AUTH_002', message: 'Sign in to continue.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();
  };

  beforeEach(() => vi.useFakeTimers());

  afterEach(() => {
    vi.useRealTimers();
    httpMock.verify();
  });

  it('shows nothing at all while a fast answer is on its way', () => {
    // A warm `/api/me` answers in well under 200ms. A loading panel that flashes on every one of
    // those is worse than the blank frame it replaces, so the first 600ms are deliberately silent.
    boot();
    wait(300);

    expect(text()).toBe('');

    respondWithSession();
    // And it must not turn up late either — the timers belong to a component that is now gone.
    wait(20_000);
    expect(text()).toBe('');
  });

  it('puts its live region on the page before it has anything to say', () => {
    // A screen reader announces a live region's *changes*. One that appears with its text already
    // in it is announced unreliably, so the region is in the DOM from the first frame and empty,
    // and the delay happens inside it. This is the assertion that stops someone "simplifying" the
    // delay up into the `@if` in `app.html`.
    boot();

    const region = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(region).not.toBeNull();
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(text()).toBe('');

    respondWithSession();
  });

  it('says what it is waiting for once the wait is long enough to notice', () => {
    boot();
    wait(700);

    expect(text()).toContain('Loading Chalkbase');

    respondWithSession();
  });

  it('acknowledges a wait that has gone on too long to leave unexplained', () => {
    boot();
    wait(700);

    // Ten seconds of an unexplained indicator is the blank page again in a nicer colour — but at
    // 700ms there is nothing unusual to report yet.
    expect(text()).not.toContain('longer than usual');

    wait(10_000);

    expect(text()).toContain('This is taking longer than usual');
    expect(text()).toContain('no need to reload');
    // Never a diagnosis of the server it happens to be talking to. This text ships to schools.
    expect(text()).not.toMatch(/render|free tier|cold start|asleep|sleeping/i);

    respondWithSession();
  });

  it('goes away when the session lands', () => {
    boot();
    wait(700);
    expect(text()).toContain('Loading Chalkbase');

    respondWithSession();

    expect(text()).toBe('');
  });

  it('goes away when the server says there is no session', () => {
    // The 401 arm. The guard sends this person to the sign-in screen; what must not happen is a
    // loading panel left on top of it forever because only the success path cleared the flag.
    boot();
    wait(700);
    expect(text()).toContain('Loading Chalkbase');

    respondSignedOut();

    expect(text()).toBe('');
  });

  it('goes away when the call fails outright', () => {
    // The other arm of the same failure handler: a 503 is not a sign-out, and it is not a licence
    // to wait forever either.
    const logged = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    boot();
    wait(700);

    httpMock
      .expectOne('/api/me')
      .flush(
        { success: false, timestamp: '2026-09-06T10:00:00Z' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();

    expect(text()).toBe('');
    logged.mockRestore();
  });

  it('never covers a screen the router has already rendered', async () => {
    // `/login` has no guard, so it activates while `/api/me` is still in flight. Someone who opened
    // the sign-in screen directly on a slow connection can type into it; a loading panel over the
    // top would be the app answering a question nobody asked.
    boot([{ path: 'login', component: LoginStub }]);
    await TestBed.inject(Router).navigateByUrl('/login');
    fixture.detectChanges();

    wait(20_000);

    expect(text()).toContain('Sign in to Chalkbase');
    expect(text()).not.toContain('Loading Chalkbase');

    respondWithSession();
  });
});
