import { InjectionToken } from '@angular/core';
import { IconName } from '../../shared/components/icon/icon-glyphs';

/** What this app knows how to do with a navigation id the server sends. */
export interface NavRoute {
  /** An absolute path into this app's own router config. Never something the server supplied. */
  readonly path: string;
  readonly icon: IconName;
}

/**
 * The route registry: the only place a server-side navigation id becomes a URL.
 *
 * ADR-0008 draws the line here. The server owns *which* items exist for this user, their order and
 * their nesting; this app owns what a route actually is and what it looks like. So the server
 * sends `fees.collect` and never `/fees/collect`, and a backend deploy cannot break navigation by
 * naming a route that does not exist.
 *
 * The other half of that bargain is enforced in `NavigationStore`: **an id with no entry here is
 * dropped and logged, never rendered.** A dead menu item — one that 404s, or lands on a screen
 * that is not built — is worse than a missing one, because the user cannot tell whether the
 * feature is broken or they are.
 *
 * ## Adding a module
 *
 * An entry goes in **in the same change that adds the module's routes**, not before. That is the
 * deliberate cost ADR-0008 accepts in exchange for routing that is type-checked and links that
 * always resolve. The ids below therefore track `app.routes.ts` exactly; the modules named in the
 * designs (students, fees, attendance, exams, communication, transport, reports, settings) join
 * this map as each one lands.
 *
 * TODO(contract): once the backend publishes its navigation catalogue, add the test ADR-0008 asks
 * for — every id the backend can emit is resolvable here, so a drop is a deployment mistake caught
 * in CI rather than a menu item that quietly vanishes in production.
 */
export const APP_NAV_ROUTES: ReadonlyMap<string, NavRoute> = new Map<string, NavRoute>([
  ['schools', { path: '/schools', icon: 'school' }],
  // The settings section. The backend has emitted this since the navigation catalogue landed and
  // it was being dropped every time, because there was nothing behind it; the school profile is
  // the first screen there is.
  ['settings', { path: '/settings', icon: 'settings' }],
  // Contributed by the school module under the settings container the identity module owns — the
  // backend places it by its dotted id, so neither module has to reach into the other.
  ['settings.profile', { path: '/settings/school-profile', icon: 'settings' }],
  // The audit log. The backend has emitted this id since the audit API shipped and it was being
  // dropped-and-logged every time, because there was no screen behind it; this entry is the whole
  // of what it took to switch the menu item on, with no backend change — which is the bargain
  // ADR-0008 describes, arriving exactly as described.
  ['audit', { path: '/audit', icon: 'shield-check' }],
  // Academics, and the two screens under it. All three share the one glyph: a nested group that
  // changes icon per child reads as three unrelated destinations rather than as one section, and
  // the label is what tells them apart.
  //
  // The container needs an entry of its own even though it is only a heading — `NavigationStore`
  // drops an unresolvable id along with its children, so without this the two screens would
  // vanish from the menu with the parent.
  ['academics', { path: '/academics', icon: 'academics' }],
  ['academics.sessions', { path: '/academics/sessions', icon: 'academics' }],
  ['academics.classes', { path: '/academics/classes', icon: 'academics' }],
  // `settings.access` is deliberately absent. The backend emits it, it has no screen in this
  // build, and so it stays dropped-and-logged — an entry here would be a menu item that 404s,
  // which is the one thing this registry exists to prevent.
]);

/**
 * The registry as a dependency, so a spec can hand the shell a menu of six items without inventing
 * six routes for the real app to fall over on. Production code never provides it — the default
 * factory is `APP_NAV_ROUTES`, which stays the single source of truth.
 */
export const NAV_ROUTES = new InjectionToken<ReadonlyMap<string, NavRoute>>('cb.nav-routes', {
  providedIn: 'root',
  factory: () => APP_NAV_ROUTES,
});
