import { Component, Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LoginResponse } from '../api/models';
import { Permissions } from './permissions';
import { me } from './session-fixture';
import { SessionStore, permitted } from './session-store';

/**
 * The permission check, on its own.
 *
 * Every screen spec exercises this through a real template; these are the cases a screen cannot
 * reach — the login path, sign-out, and what the answer is before anybody has signed in at all.
 */
describe('SessionStore permissions', () => {
  let store: SessionStore;

  const login = (permissions: readonly string[]): LoginResponse => ({
    userId: '018f3a10-0000-7000-8000-0000000000fe',
    displayName: 'Test Principal',
    mustChangePassword: false,
    school: { code: 'EVG', name: 'Test School' },
    permissions: [...permissions],
  });

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(SessionStore);
  });

  it('answers yes for a permission the signed-in user holds', () => {
    store.bootstrapped(me([Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE]));

    expect(store.has(Permissions.STUDENT_MANAGE)).toBe(true);
  });

  it('answers no for a permission they do not hold', () => {
    store.bootstrapped(me([Permissions.STUDENT_READ]));

    expect(store.has(Permissions.STUDENT_MANAGE)).toBe(false);
  });

  /**
   * The fail direction, stated as a test so it cannot drift.
   *
   * Nothing known means nothing offered. The server enforces regardless, so the cost of being
   * wrong this way is a hidden button on a screen a route guard is already leaving; the cost of
   * failing the other way is the defect this replaced — a form that ends in a 403.
   */
  it('answers no when nobody is signed in', () => {
    expect(store.has(Permissions.STUDENT_MANAGE)).toBe(false);
  });

  it('reads the permissions the login response carried, not only the bootstrap', () => {
    store.signedIn(login([Permissions.GUARDIAN_MANAGE]), 'temporary-password');

    expect(store.has(Permissions.GUARDIAN_MANAGE)).toBe(true);
    expect(store.has(Permissions.STUDENT_MANAGE)).toBe(false);
  });

  it('forgets them on sign-out', () => {
    store.bootstrapped(me([Permissions.STUDENT_MANAGE]));
    store.signedOut();

    expect(store.has(Permissions.STUDENT_MANAGE)).toBe(false);
  });

  /**
   * The shared machine at the school office: one person signs out, the next signs in, and the
   * second person must not inherit the first one's buttons.
   */
  it('follows the session when a different user signs in', () => {
    const injector = TestBed.inject(Injector);
    const canManage = runInInjectionContext(injector, () => permitted(Permissions.STUDENT_MANAGE));

    store.bootstrapped(me([Permissions.STUDENT_MANAGE]));
    expect(canManage()).toBe(true);

    store.signedOut();
    store.bootstrapped(me([Permissions.STUDENT_READ]));

    expect(canManage()).toBe(false);
  });

  /** `permitted` is a field initialiser, so it has to work where `inject` does. */
  it('is usable as a component field', () => {
    @Component({ template: '', standalone: true })
    class Host {
      readonly canManage = permitted(Permissions.STUDENT_MANAGE);
    }

    store.bootstrapped(me([Permissions.STUDENT_MANAGE]));
    const fixture = TestBed.createComponent(Host);

    expect(fixture.componentInstance.canManage()).toBe(true);
  });
});
