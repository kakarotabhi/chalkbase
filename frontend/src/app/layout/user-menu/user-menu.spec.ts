import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SessionStore } from '../../core/auth/session-store';
import { UserMenu } from './user-menu';

const USER = {
  userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
  displayName: 'Priya Sharma',
  mustChangePassword: false,
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissions: [],
};

describe('UserMenu', () => {
  let fixture: ComponentFixture<UserMenu>;
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let navigate: ReturnType<typeof vi.spyOn>;

  const element = () => fixture.nativeElement as HTMLElement;
  const trigger = () => element().querySelector('.user-menu__trigger') as HTMLButtonElement;
  const menu = () => element().querySelector('[role="menu"]');
  const items = () => [...element().querySelectorAll('[role="menuitem"]')] as HTMLButtonElement[];
  const itemNamed = (label: string) =>
    items().find((item) => item.textContent?.includes(label)) as HTMLButtonElement;

  const press = (target: HTMLElement, key: string) => {
    target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    fixture.detectChanges();
  };

  const open = () => {
    trigger().click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserMenu],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(UserMenu);
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    session.signedIn(USER, 'secret-one');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('shows who is signed in, with their initials', () => {
    expect(trigger().textContent).toContain('Priya Sharma');
    expect(element().querySelector('.user-menu__avatar')?.textContent).toBe('PS');
  });

  it('falls back to something sensible when nobody is in the store', () => {
    session.signedOut();
    fixture.detectChanges();

    expect(trigger().textContent).toContain('Account');
    expect(element().querySelector('.user-menu__avatar')?.textContent).toBe('A');
  });

  it('starts closed', () => {
    expect(menu()).toBeNull();
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
  });

  it('opens on click and puts focus on the first item', () => {
    open();

    expect(menu()).not.toBeNull();
    expect(trigger().getAttribute('aria-expanded')).toBe('true');
    expect(items().map((item) => item.textContent?.trim())).toEqual([
      'My profile',
      'Settings',
      'Sign out',
    ]);
    expect(document.activeElement).toBe(items()[0]);
  });

  it('opens from the keyboard, on Enter and on Space alike', () => {
    press(trigger(), 'Enter');
    expect(menu()).not.toBeNull();

    press(trigger(), 'Escape');
    expect(menu()).toBeNull();

    press(trigger(), ' ');
    expect(menu()).not.toBeNull();
    expect(document.activeElement).toBe(items()[0]);
  });

  it('closes on Escape and hands focus back to the trigger', () => {
    open();

    press(items()[0], 'Escape');

    expect(menu()).toBeNull();
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
    // Losing focus to the page body here would strand a keyboard user at the top of the document.
    expect(document.activeElement).toBe(trigger());
  });

  it('moves between items with the arrow keys, wrapping at both ends', () => {
    open();

    press(items()[0], 'ArrowDown');
    expect(document.activeElement).toBe(items()[1]);

    press(items()[1], 'End');
    expect(document.activeElement).toBe(items()[2]);

    press(items()[2], 'ArrowDown');
    expect(document.activeElement).toBe(items()[0]);

    press(items()[0], 'ArrowUp');
    expect(document.activeElement).toBe(items()[2]);
  });

  it('shows the screens that are not built as unavailable rather than as dead links', () => {
    open();

    expect(itemNamed('My profile').getAttribute('aria-disabled')).toBe('true');
    expect(itemNamed('Settings').getAttribute('aria-disabled')).toBe('true');
    expect(itemNamed('Sign out').getAttribute('aria-disabled')).toBeNull();
    // Nothing in the menu is an anchor, so nothing can be followed to a page that does not exist.
    expect(element().querySelector('[role="menu"] a')).toBeNull();
  });

  it('signs out: calls the API, clears the store, and goes to the login screen', () => {
    open();
    itemNamed('Sign out').click();
    fixture.detectChanges();

    const request = httpMock.expectOne('/api/auth/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);

    request.flush({ success: true, timestamp: '2026-09-05T10:00:00Z' });
    fixture.detectChanges();

    expect(session.isSignedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(menu()).toBeNull();
  });

  it('signs out even when the request fails', () => {
    open();
    itemNamed('Sign out').click();
    fixture.detectChanges();

    // A teacher on a school connection that drops must not be left looking at a signed-in shell.
    httpMock
      .expectOne('/api/auth/logout')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();

    expect(session.isSignedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
