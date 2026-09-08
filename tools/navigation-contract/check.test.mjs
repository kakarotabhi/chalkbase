// Fixture-based proof for check.mjs. Run with `node --test tools/navigation-contract/check.test.mjs`
// — no dependencies, no npm install: Node's own test runner, because this whole check exists to be
// runnable without a build.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkNavigationContract, extractFrontendIds, parseBackendIds } from './check.mjs';

// A realistic slice of nav-routes.ts, including a comment that mentions an id in prose
// ("students.import") right next to a real, commented-out entry — the two must not be confused.
const FIXTURE_NAV_ROUTES_TS = `
export const APP_NAV_ROUTES: ReadonlyMap<string, NavRoute> = new Map<string, NavRoute>([
  ['schools', { path: '/schools', icon: 'school' }],
  ['dashboard', { path: '/dashboard', icon: 'dashboard' }],
  // Bulk import (ADR-0021). Registered before the backend is known to emit the id, the same way
  // students.import is described in the real registry.
  ['students.import', { path: '/students/import', icon: 'students' }],
  ['settings', { path: '/settings', icon: 'settings' }],
  ['settings.access', { path: '/settings/access', icon: 'settings' }],
  // ['settings.disabled', { path: '/settings/disabled', icon: 'settings' }],
]);
`;

test('extractFrontendIds reads every live entry and ignores comments', () => {
  const ids = extractFrontendIds(FIXTURE_NAV_ROUTES_TS);

  assert.deepEqual(
    [...ids].sort(),
    ['dashboard', 'schools', 'settings', 'settings.access', 'students.import'].sort(),
  );
  // The id only ever appears inside a comment here (both as prose and as a commented-out entry);
  // stripping comments before scanning must keep it out.
  assert.ok(!ids.includes('settings.disabled'));
});

test('extractFrontendIds throws rather than returning nothing when the map has moved', () => {
  assert.throws(() => extractFrontendIds('export const SOMETHING_ELSE = 1;'), /could not find/);
  assert.throws(
    () => extractFrontendIds('new Map<string, NavRoute>([ // no closing bracket here'),
    /closing/,
  );
});

test('parseBackendIds refuses anything that is not a flat array of strings', () => {
  assert.deepEqual(parseBackendIds('["a", "b"]'), ['a', 'b']);
  assert.throws(() => parseBackendIds('{"a": 1}'), /flat JSON array/);
  assert.throws(() => parseBackendIds('["a", 1]'), /flat JSON array/);
});

// ── The design's core claim, proved rather than argued ─────────────────────────────────────────

test('a genuine typo is reported as an error, not silently dropped', () => {
  // settings.acess (missing an "s") is the brief's own example: the backend meant to emit
  // settings.access, mistyped the constant, and now emits an id the frontend cannot map — the same
  // *shape* as a deliberate, not-yet-built screen, which is exactly why this cannot be waved
  // through without a name on the allowlist.
  const { errors } = checkNavigationContract({
    backendIds: ['dashboard', 'settings', 'settings.acess'],
    frontendIds: ['dashboard', 'settings', 'settings.access'],
    allowlist: [],
    minimumIds: 0,
  });

  assert.ok(
    errors.some((message) => message.includes('"settings.acess"')),
    `expected an error naming settings.acess, got: ${JSON.stringify(errors)}`,
  );
});

test('the same typo stops being an error only if someone allowlists the exact misspelling', () => {
  // Proves the allowlist is not a blanket "ignore backend-only ids" switch: allow-listing the
  // *correct* id does nothing for a typo of it.
  const stillFails = checkNavigationContract({
    backendIds: ['settings.acess'],
    frontendIds: ['settings.access'],
    allowlist: [{ id: 'settings.access', reason: 'wrong id allow-listed on purpose, for the test' }],
    minimumIds: 0,
  });
  assert.ok(stillFails.errors.some((m) => m.includes('"settings.acess"')));

  const passes = checkNavigationContract({
    backendIds: ['settings.acess'],
    frontendIds: ['settings.access'],
    allowlist: [{ id: 'settings.acess', reason: 'pretending this one is deliberate, for the test' }],
    minimumIds: 0,
  });
  assert.deepEqual(passes.errors, []);
});

test('a deliberate, allow-listed gap produces no error', () => {
  const { errors } = checkNavigationContract({
    backendIds: ['dashboard', 'students', 'students.documents'],
    frontendIds: ['dashboard', 'students'],
    allowlist: [{ id: 'students.documents', reason: 'no frontend screen yet' }],
    minimumIds: 0,
  });

  assert.deepEqual(errors, []);
});

test('an allowlist entry with no reason is itself an error', () => {
  const { errors } = checkNavigationContract({
    backendIds: ['dashboard', 'dashboard.widgets'],
    frontendIds: ['dashboard'],
    allowlist: [{ id: 'dashboard.widgets', reason: '' }],
    minimumIds: 0,
  });

  assert.ok(errors.some((m) => m.includes('no "reason"')));
  // And the underlying gap is still reported, because a reason-less entry protects nothing.
  assert.ok(errors.some((m) => m.includes('"dashboard.widgets"') && m.includes('does not map')));
});

test('a stale allowlist entry — the frontend caught up — is an error, not silence', () => {
  const { errors } = checkNavigationContract({
    backendIds: ['dashboard', 'settings.access'],
    frontendIds: ['dashboard', 'settings.access'],
    allowlist: [{ id: 'settings.access', reason: 'used to have no screen; it does now' }],
    minimumIds: 0,
  });

  assert.ok(errors.some((m) => m.includes('allowlist.json lists "settings.access"') && m.includes('no longer')));
});

test('a frontend-only id is a warning, never an error', () => {
  const { errors, warnings } = checkNavigationContract({
    backendIds: ['dashboard'],
    frontendIds: ['dashboard', 'students.import', 'schools'],
    allowlist: [],
    minimumIds: 0,
  });

  assert.deepEqual(errors, []);
  assert.equal(warnings.length, 2);
  assert.ok(warnings.some((m) => m.includes('"students.import"')));
  assert.ok(warnings.some((m) => m.includes('"schools"')));
});

test('too few ids on either side fails loudly instead of passing by finding nothing', () => {
  const emptyBackend = checkNavigationContract({ backendIds: [], frontendIds: ['a', 'b'], allowlist: [] });
  assert.ok(emptyBackend.errors.some((m) => m.includes('backend id(s) were read')));

  const emptyFrontend = checkNavigationContract({ backendIds: ['a', 'b'], frontendIds: [], allowlist: [] });
  assert.ok(emptyFrontend.errors.some((m) => m.includes('frontend id(s) were read')));
});

test('a well-formed contract with no gaps at all passes clean', () => {
  const { errors, warnings } = checkNavigationContract({
    backendIds: Array.from({ length: 12 }, (_, i) => `id${i}`),
    frontendIds: Array.from({ length: 12 }, (_, i) => `id${i}`),
    allowlist: [],
  });

  assert.deepEqual(errors, []);
  assert.deepEqual(warnings, []);
});
