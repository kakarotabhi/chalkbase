import { TestBed } from '@angular/core/testing';
import { MeResponse } from '../api/models';
import { Permission } from './permissions';
import { SessionStore } from './session-store';

/**
 * Signs a fake user in, holding exactly the permissions named — for specs, and only for specs.
 *
 * ## Why this exists rather than a mock
 *
 * The whole point of the permission gating is that it reads the real `SessionStore`. A spec that
 * stubbed `SessionStore.has` would pass whether or not the store, the helper and the template
 * agree, which is the one thing worth proving. So this fills the real store through the real
 * bootstrap path — the same call `SessionBootstrap` makes when `GET /api/me` lands — and every
 * screen spec then exercises the production code end to end.
 *
 * Calling it a second time with a different list is how a spec proves the check is live rather
 * than a snapshot taken when the component was constructed.
 *
 * ## Why it is not a `.spec.ts`
 *
 * It is imported by specs, not run as one. `tsconfig.app.json` compiles it and nothing in the
 * application imports it, so it is type-checked with the rest of the app and dropped from the
 * bundle. Nothing in `src/app` outside a spec may import this file.
 */
export function signInWith(...permissions: readonly Permission[]): void {
  TestBed.inject(SessionStore).bootstrapped(me(permissions));
}

/** The `/api/me` payload `signInWith` feeds the store. Exported for a spec that wants to edit it. */
export function me(permissions: readonly Permission[] = []): MeResponse {
  return {
    user: {
      id: '018f3a10-0000-7000-8000-0000000000ff',
      displayName: 'Test Teacher',
      mustChangePassword: false,
    },
    school: { code: 'EVG', name: 'Test School' },
    permissionsVersion: 'test-permissions-version',
    permissions: [...permissions],
    navigation: [],
  };
}
