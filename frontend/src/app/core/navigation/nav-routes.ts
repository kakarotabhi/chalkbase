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
 * The check ADR-0008 asks for — "every id the backend can emit is resolvable here" — is
 * `.github/workflows/navigation-contract.yml`, comparing `contracts/navigation-ids.json` (written
 * by the backend's `NavigationContractExportTests`) against this map. It is a CI script rather
 * than a spec here, because a mismatch is not automatically a defect: an id this map does not know
 * yet may simply not have shipped a screen, which `NavigationStore`'s drop-and-log exists to allow.
 * The check tells that apart from a typo with an allowlist entry per deliberate gap, each one
 * carrying a required reason — see `tools/navigation-contract/check.mjs`.
 */
export const APP_NAV_ROUTES: ReadonlyMap<string, NavRoute> = new Map<string, NavRoute>([
  // `schools` is deliberately absent. The backend stopped emitting it: the school REGISTER is a
  // platform-operator view, and every shipped role held the permission that gated it, so every user
  // was shown a menu item leading to a list of every other school. The route still exists for an
  // operator who types it; nothing puts it in a menu.
  ['schools', { path: '/schools', icon: 'school' }],
  // The dashboard. No permission gates it on the backend, so it is the first item in every
  // signed-in user's own menu (order 10) and, through `landingGuard`, everyone's landing screen.
  ['dashboard', { path: '/dashboard', icon: 'dashboard' }],
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
  // Academics, and the three screens under it. All four share the one glyph: a nested group that
  // changes icon per child reads as unrelated destinations rather than as one section, and the
  // label is what tells them apart.
  //
  // The container needs an entry of its own even though it is only a heading — `NavigationStore`
  // drops an unresolvable id along with its children, so without this the three screens would
  // vanish from the menu with the parent.
  ['academics', { path: '/academics', icon: 'academics' }],
  ['academics.sessions', { path: '/academics/sessions', icon: 'academics' }],
  ['academics.classes', { path: '/academics/classes', icon: 'academics' }],
  ['academics.subjects', { path: '/academics/subjects', icon: 'academics' }],
  // Students, and the two screens under it. The container needs an entry of its own even though
  // it only ever renders as a heading — `NavigationStore` drops an unresolvable id along with its
  // children, so without this both screens would vanish from the menu with the parent.
  //
  // All three share the one glyph: a nested group that changes icon per child reads as three
  // unrelated destinations rather than as one section, and the label is what tells them apart.
  ['students', { path: '/students', icon: 'students' }],
  ['students.all', { path: '/students', icon: 'students' }],
  ['students.guardians', { path: '/students/guardians', icon: 'students' }],
  // Bulk import (ADR-0021). Registered before the backend is known to emit the id — which costs
  // nothing, because the registry is a map from ids the server sends to routes this app owns, and
  // an entry nobody asks for is simply never looked up. The risk this file exists to prevent runs
  // the other way: an id emitted with no entry here, which is dropped and logged.
  ['students.import', { path: '/students/import', icon: 'students' }],
  // `/students/:id` is deliberately not here and never will be. The registry maps ids the *server*
  // sends to menu destinations, and a student's record is reached from the list, not from a menu.
  // Roles and access. `IdentityNavigation` has emitted this id since the navigation catalogue
  // landed; it was dropped-and-logged every visit because there was no screen behind it, and this
  // entry is what switches the menu item on. Same glyph as `settings` and `settings.profile` —
  // three settings entries sharing one icon is this file's own existing convention for a section,
  // and the label is what tells them apart.
  ['settings.access', { path: '/settings/access', icon: 'settings' }],
  // The account roster. `IdentityNavigation` (backend) now emits `settings.users`, gated on
  // `identity:user:read`; this entry is what switches the menu item on, with no other change —
  // the same bargain `audit` and `settings.access` already describe above. Same glyph as the other
  // two settings entries, for the same reason: three items sharing one icon read as one section,
  // and the label is what tells them apart.
  ['settings.users', { path: '/settings/users', icon: 'settings' }],

  // Attendance (Phase 2, ADR-0030). Both screens share the one glyph, same convention as academics
  // and students above: a nested group that changes icon per child reads as unrelated destinations,
  // and the label is what tells them apart. The container needs its own entry for the same reason
  // every other container above does — `NavigationStore` drops an unresolvable id along with its
  // children.
  ['attendance', { path: '/attendance', icon: 'attendance' }],
  ['attendance.mark', { path: '/attendance/mark', icon: 'attendance' }],
  ['attendance.leave', { path: '/attendance/leave', icon: 'attendance' }],
  ['attendance.corrections', { path: '/attendance/corrections', icon: 'attendance' }],

  // Admissions (Phase 2): enquiry management only — FR-016, FR-017. Both screens share the one
  // glyph, the same convention as every other section above; the container needs its own entry for
  // the same reason every other container does — `NavigationStore` drops an unresolvable id along
  // with its children.
  ['admissions', { path: '/admissions', icon: 'admissions' }],
  ['admissions.enquiries', { path: '/admissions/enquiries', icon: 'admissions' }],
  ['admissions.follow_ups', { path: '/admissions/follow-ups', icon: 'admissions' }],
  // Fee structure (Phase 2, ADR-0012, ADR-0033). Same convention as academics and attendance
  // above: both screens share the one glyph, and the container needs its own entry so
  // `NavigationStore` does not drop both children along with an unresolvable parent.
  //
  // `fees.collect`, `fees.receipts` and `fees.defaulters` are reserved in the label catalogue
  // (`nav-labels.ts`) for the collection lane, which has not shipped and so has no entry here yet
  // — an id this map does not know is dropped and logged, exactly as ADR-0008 intends.
  ['fees', { path: '/fees', icon: 'fees' }],
  ['fees.heads', { path: '/fees/heads', icon: 'fees' }],
  ['fees.structure', { path: '/fees/structure', icon: 'fees' }],
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
