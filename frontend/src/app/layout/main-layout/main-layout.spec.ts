import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NavigationItem } from '../../core/api/models';
import { NavigationStore } from '../../core/navigation/navigation-store';
import { NAV_ROUTES, NavRoute } from '../../core/navigation/nav-routes';
import { SessionStore } from '../../core/auth/session-store';
import { MainLayout } from './main-layout';

/**
 * A registry standing in for the real one. The shell's behaviour depends on how many items it is
 * given, not on which modules happen to have shipped, so these tests supply their own ids rather
 * than moving every time `nav-routes.ts` grows an entry.
 */
const ROUTES = new Map<string, NavRoute>([
  ['dashboard', { path: '/dashboard', icon: 'dashboard' }],
  ['students', { path: '/students', icon: 'students' }],
  ['fees', { path: '/fees', icon: 'fees' }],
  ['fees.collect', { path: '/fees/collect', icon: 'fees' }],
  ['attendance', { path: '/attendance', icon: 'attendance' }],
  ['exams', { path: '/exams', icon: 'exams' }],
  ['reports', { path: '/reports', icon: 'reports' }],
]);

const ORDERED_IDS = ['dashboard', 'students', 'fees', 'attendance', 'exams', 'reports'] as const;

const navItem = (over: Partial<NavigationItem> & Pick<NavigationItem, 'id'>): NavigationItem => ({
  labelKey: `nav.${over.id}`,
  order: 10,
  children: [],
  ...over,
});

/** The first `count` ids, in order, as the server would send them. */
const menuOf = (count: number): NavigationItem[] =>
  ORDERED_IDS.slice(0, count).map((id, index) => navItem({ id, order: (index + 1) * 10 }));

describe('MainLayout', () => {
  let fixture: ComponentFixture<MainLayout>;

  const element = () => fixture.nativeElement as HTMLElement;
  const header = () => element().querySelector('.shell__header');
  const nav = () => element().querySelector('nav[aria-label="Primary"]');
  // Links only. The More button wears the same class — it is a navigation entry drawn the same
  // way — so it is asked for by name where a test means it.
  const navLinks = () => Array.from(element().querySelectorAll('.shell__nav a.nav-item'));
  const barLinks = () =>
    navLinks().filter((item) => !item.classList.contains('nav-item--overflow'));
  const labels = (items: Element[]) =>
    items.map((item) => item.querySelector('.nav-item__label')?.textContent?.trim());
  const moreButton = () => element().querySelector<HTMLButtonElement>('.nav-item--more');
  const sheet = () => element().querySelector('cb-bottom-sheet');

  const showMenu = (items: NavigationItem[]) => {
    TestBed.inject(NavigationStore).load(items);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // A route that matches anything: the sheet's links are real `routerLink`s, and a click in
        // a test that resolves to nothing leaves the router rejecting after the fixture is gone.
        provideRouter([{ path: '**', children: [] }]),
        { provide: NAV_ROUTES, useValue: ROUTES },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayout);
  });

  it('names the school whose data is on screen', () => {
    TestBed.inject(SessionStore).signedIn(
      {
        userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
        displayName: 'Priya Sharma',
        mustChangePassword: false,
        school: { code: 'GPS-S12', name: 'Greenfield Public School' },
        permissions: [],
      },
      'secret-one',
    );
    fixture.detectChanges();

    expect(header()?.textContent).toContain('Greenfield Public School');
    // And who is looking at it, from the same store.
    expect(header()?.querySelector('.user-menu__avatar')?.textContent).toBe('PS');
  });

  it('falls back to the product name rather than an empty header', () => {
    fixture.detectChanges();

    expect(header()?.textContent).toContain('Chalkbase');
  });

  it('renders the menu the server returned, in the order it asked for', () => {
    showMenu([
      navItem({ id: 'fees', order: 30 }),
      navItem({ id: 'dashboard', order: 10 }),
      navItem({ id: 'students', order: 20 }),
    ]);

    expect(labels(navLinks())).toEqual(['Dashboard', 'Students', 'Fees']);
    expect(navLinks().map((link) => link.getAttribute('href'))).toEqual([
      '/dashboard',
      '/students',
      '/fees',
    ]);
  });

  it('does not render an item this build has no route for', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    showMenu([navItem({ id: 'dashboard' }), navItem({ id: 'hostel', order: 20 })]);

    expect(labels(navLinks())).toEqual(['Dashboard']);
    expect(element().textContent).not.toContain('Hostel');
  });

  it('shows something readable when the label key is one this build has never heard of', () => {
    showMenu([navItem({ id: 'fees.collect', labelKey: 'nav.fees.not_translated_yet' })]);

    expect(labels(navLinks())).toEqual(['Not translated yet']);
  });

  it('puts all five destinations in the bottom bar when there are five', () => {
    showMenu(menuOf(5));

    expect(barLinks()).toHaveLength(5);
    expect(moreButton()).toBeNull();
  });

  it('shows the first four plus More when there are six', () => {
    showMenu(menuOf(6));

    // Every item is still in the DOM once — the rail and the sidebar show all six, and the bar
    // hides the overflow in CSS. Only the four the bar keeps are unmarked.
    expect(navLinks()).toHaveLength(6);
    expect(labels(barLinks())).toEqual(['Dashboard', 'Students', 'Fees', 'Attendance']);
    expect(moreButton()).not.toBeNull();
  });

  it('renders navigation once, whatever the width', () => {
    showMenu(menuOf(6));

    // Three copies would give a screen reader three navigation landmarks (ADR-0010).
    expect(element().querySelectorAll('nav')).toHaveLength(1);
  });

  it('opens the More sheet with every section in it, children included', () => {
    showMenu([...menuOf(6), navItem({ id: 'fees.collect', order: 70 })]);
    moreButton()?.click();
    fixture.detectChanges();

    expect(sheet()).not.toBeNull();
    const sheetLinks = Array.from(element().querySelectorAll('.sheet-nav__link'));
    expect(sheetLinks.map((link) => link.textContent?.trim())).toContain('Reports');
    expect(sheetLinks.map((link) => link.textContent?.trim())).toContain('Collect fees');
  });

  it('closes the More sheet on Escape and gives focus back to the button that opened it', () => {
    showMenu(menuOf(6));
    const trigger = moreButton();
    trigger?.focus();
    trigger?.click();
    fixture.detectChanges();
    expect(sheet()).not.toBeNull();

    sheet()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(sheet()).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('closes the More sheet when a section in it is chosen', async () => {
    showMenu(menuOf(6));
    moreButton()?.click();
    fixture.detectChanges();

    element().querySelector<HTMLAnchorElement>('.sheet-nav__link')?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(sheet()).toBeNull();
  });

  it('still shows the shell when the bootstrap call never delivered a menu', () => {
    // A failed /api/me leaves the store empty. The header, the account menu and the content area
    // are all still there — an empty menu is a missing menu, not a blank screen.
    fixture.detectChanges();

    expect(navLinks()).toHaveLength(0);
    expect(nav()).not.toBeNull();
    expect(header()).not.toBeNull();
    expect(element().querySelector('.shell__content')).not.toBeNull();
  });
});
