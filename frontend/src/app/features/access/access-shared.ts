import { ScopeType } from '../../core/api/models';
import { SelectOption } from '../../shared/components/select/select';

/**
 * What the two access screens share: the words for identity's closed sets, and the error codes
 * they branch on (ADR-0007) — never the message, always the code.
 */

/** The error code a 403 carries. */
export const ACCESS_DENIED = 'PERM_001';

/** `uq_user_identifier_value`: this school already has an account signing in with that username. */
export const USERNAME_TAKEN = 'AUTH_009';

/**
 * Deactivating this account, revoking this grant, or dropping `identity:role:manage` from this
 * role would leave nobody at the school able to manage access. **A guard, not a failure** — the
 * system refusing to let a school lock itself out — so every screen words it as protection and
 * says what to do next (grant the same role to someone else first), never as a permission error.
 */
export const LAST_ACCESS_MANAGER = 'AUTH_010';

/**
 * The acting account tried to grant, or add to a role, a permission it does not itself hold.
 * `identity:role:manage` is not scoped to particular permissions, so this is the only thing
 * stopping a holder handing out access wider than their own. Never returned for *removing* a
 * permission — taking access away cannot escalate anything.
 */
export const CANNOT_GRANT_UNHELD_PERMISSION = 'AUTH_011';

/** `uq_role_code`. Two admins naming similarly-titled roles at the same instant. */
export const ROLE_NAME_TAKEN = 'AUTH_012';

/** `uq_user_role_grant`. Two clicks of "grant" landing at once. */
export const GRANT_ALREADY_EXISTS = 'AUTH_013';

/**
 * How an account's own state reads.
 *
 * `UserAccount.status` is `ACTIVE` or `DISABLED` on the backend, but the field is typed `String`
 * on the DTO (see `UserSummary` in `models.ts`) so the contract carries no enum to alias — the
 * same reason permission codes are literals in `core/auth/permissions.ts`.
 */
export const STATUS_LABELS: Readonly<Record<string, string>> = {
  ACTIVE: 'Active',
  DISABLED: 'Deactivated',
};

/**
 * What a grant is scoped to, as offered to an admin building one.
 *
 * `WARD` is deliberately never an option here even though `ScopeType` admits the literal: a
 * parent's reach is derived from the guardian-of relationship and is never assigned, and the
 * backend rejects it outright (`GrantRoleRequest`'s own Javadoc). Offering it would be a control
 * that always fails.
 *
 * **`CAMPUS` and `DEPARTMENT` are absent for a different reason: nothing to scope to.** Both are
 * legal `ScopeType` values on the wire — ADR-0005 names them as a future capability — but no module
 * in this build owns a campus or a department record, so there is no list to pick one from and no
 * way to tell a valid id from a typo. A free-text UUID box would be a control nobody could fill in
 * correctly. This screen offers them once a module exists to look one up against; until then this
 * is a known gap (`docs/status.md`), not an oversight.
 */
/** A `ScopeType` this screen can actually build a grant for — see `SCOPE_LABELS` above. */
export type AssignableScope = Extract<
  ScopeType,
  'SCHOOL' | 'CLASS' | 'SECTION' | 'SUBJECT' | 'SELF'
>;

export const SCOPE_LABELS: Readonly<Record<AssignableScope, string>> = {
  SCHOOL: 'The whole school',
  CLASS: 'One class',
  SECTION: 'One section',
  SUBJECT: 'One subject',
  SELF: "The holder's own records only",
};

export const SCOPE_OPTIONS: readonly SelectOption[] = (
  Object.keys(SCOPE_LABELS) as AssignableScope[]
).map((scope) => ({ value: scope, label: SCOPE_LABELS[scope] }));

/** Whether a scope needs `scopeId` — every one of them except the two that mean "no narrower than this". */
export function scopeNeedsId(scope: string): boolean {
  return scope !== 'SCHOOL' && scope !== 'SELF';
}

/**
 * Reads a value from one of the closed sets above, coping with one the backend added and this
 * build has not caught up with — the same trade `navLabel` and `students-shared.ts`'s `labelFor`
 * make.
 */
export function labelFor<T extends string>(
  labels: Readonly<Record<T, string>>,
  value: T | string,
): string {
  const known = (labels as Readonly<Record<string, string>>)[value];
  if (known) {
    return known;
  }
  const words = String(value ?? '')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : '—';
}

/** Groups the permission catalogue by its `module`, in the order modules are first seen. */
export function groupByModule<T extends { readonly module: string }>(
  items: readonly T[],
): ReadonlyMap<string, readonly T[]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const group = groups.get(item.module);
    if (group) {
      group.push(item);
    } else {
      groups.set(item.module, [item]);
    }
  }
  return groups;
}

/** `identity` → `Identity`. The module names in the catalogue are lower-case package names. */
export function moduleLabel(module: string): string {
  return module.charAt(0).toUpperCase() + module.slice(1);
}
