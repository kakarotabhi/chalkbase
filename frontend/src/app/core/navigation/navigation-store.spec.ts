import { TestBed } from '@angular/core/testing';
import { NavigationItem } from '../api/models';
import { NavigationStore } from './navigation-store';
import { NAV_ROUTES, NavRoute } from './nav-routes';

const item = (over: Partial<NavigationItem> & Pick<NavigationItem, 'id'>): NavigationItem => ({
  labelKey: `nav.${over.id}`,
  icon: null,
  order: 10,
  children: [],
  ...over,
});

/** A registry standing in for the real one, so these tests do not move when a module ships. */
const ROUTES = new Map<string, NavRoute>([
  ['dashboard', { path: '/dashboard', icon: 'dashboard' }],
  ['students', { path: '/students', icon: 'students' }],
  ['fees', { path: '/fees', icon: 'fees' }],
  ['fees.collect', { path: '/fees/collect', icon: 'fees' }],
]);

describe('NavigationStore', () => {
  let store: NavigationStore;
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: NAV_ROUTES, useValue: ROUTES }],
    });
    store = TestBed.inject(NavigationStore);
    warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => warn.mockRestore());

  it('renders what the server sent, in the order it asked for', () => {
    store.load([
      item({ id: 'fees', order: 30 }),
      item({ id: 'dashboard', order: 10 }),
      item({ id: 'students', order: 20 }),
    ]);

    expect(store.items().map((link) => link.id)).toEqual(['dashboard', 'students', 'fees']);
    expect(store.items().map((link) => link.path)).toEqual(['/dashboard', '/students', '/fees']);
  });

  it('drops an id it has no route for rather than rendering a dead link', () => {
    store.load([item({ id: 'dashboard', order: 10 }), item({ id: 'hostel', order: 20 })]);

    expect(store.items().map((link) => link.id)).toEqual(['dashboard']);
    // And says so, because a silent drop is how the two sides stay drifted.
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('hostel'));
  });

  it('drops the children of a dropped parent too', () => {
    store.load([
      item({
        id: 'hostel',
        children: [item({ id: 'hostel.rooms' })],
      }),
    ]);

    expect(store.items()).toEqual([]);
  });

  it('keeps the nesting the server described', () => {
    store.load([item({ id: 'fees', children: [item({ id: 'fees.collect' })] })]);

    expect(store.items()[0].children.map((child) => child.path)).toEqual(['/fees/collect']);
  });

  it('translates the label key', () => {
    store.load([item({ id: 'fees', labelKey: 'nav.fees' })]);

    expect(store.items()[0].label).toBe('Fees');
  });

  it('shows something readable when the key is one this build has never heard of', () => {
    store.load([item({ id: 'fees.collect', labelKey: 'nav.fees.something_new' })]);

    // Not "nav.fees.something_new", which is a bug report waiting to be filed by a user.
    expect(store.items()[0].label).toBe('Something new');
  });

  it("prefers the school's own name for an item over the catalogue", () => {
    store.load([item({ id: 'fees', labelKey: 'nav.fees', label: 'Fees & Dues' })]);

    expect(store.items()[0].label).toBe('Fees & Dues');
  });

  it('takes the icon from the registry, not from the server', () => {
    store.load([item({ id: 'students', icon: 'not-an-icon-we-have' })]);

    expect(store.items()[0].icon).toBe('students');
  });

  it('tells "no menu yet" apart from "a menu with nothing in it"', () => {
    expect(store.loaded()).toBe(false);

    store.load([]);
    expect(store.loaded()).toBe(true);
    expect(store.items()).toEqual([]);

    store.clear();
    expect(store.loaded()).toBe(false);
  });
});
