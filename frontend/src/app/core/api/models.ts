/**
 * Hand-written API models.
 *
 * TODO(contracts): once the backend publishes contracts/openapi.json, this file is replaced by a
 * generated client and hand-editing it becomes a review blocker. See docs/development/api-client.md.
 */
export type Board = 'CBSE' | 'CISCE' | 'STATE' | 'IB' | 'CAIE' | 'OTHER';

export interface School {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly board: Board;
  readonly city?: string;
  readonly state?: string;
  readonly active: boolean;
}

export interface CreateSchoolRequest {
  readonly code: string;
  readonly name: string;
  readonly board: Board;
  readonly city?: string;
  readonly state?: string;
}

/* ── School profile (GET/PUT /api/school/profile) ────────────────────────── */

/**
 * The current school, as its own administrators see it.
 *
 * No id here and none in the URL: the tenant is the school (ADR-0011), so *which* school is
 * answered by the session. `code` and `schemaName` address that tenant and cannot be edited — the
 * backend refuses an update that changes either rather than quietly ignoring it.
 */
export interface SchoolProfile {
  readonly code: string;
  readonly schemaName: string;
  readonly name: string;
  readonly board: Board;
  readonly addressLine1?: string;
  readonly addressLine2?: string;
  readonly city?: string;
  readonly state?: string;
  readonly pincode?: string;
  readonly principalName?: string;
  readonly phone?: string;
  readonly email?: string;
  readonly website?: string;
  readonly affiliationNumber?: string;
  /** False when the school has never saved one: the fields above are seeds, not saved answers. */
  readonly configured: boolean;
  readonly updatedAt?: string;
}

/**
 * A full replacement of the profile — every editable field, every time.
 *
 * `code` and `schemaName` go back unchanged so that an attempt to change them is refused loudly
 * instead of dropped silently.
 */
export interface UpdateSchoolProfileRequest {
  readonly code: string;
  readonly schemaName: string;
  readonly name: string;
  readonly board: Board;
  readonly addressLine1: string;
  readonly addressLine2: string;
  readonly city: string;
  readonly state: string;
  readonly pincode: string;
  readonly principalName: string;
  readonly phone: string;
  readonly email: string;
  readonly website: string;
  readonly affiliationNumber: string;
}

/**
 * The envelope every `/api` response uses. Exactly one of `data` and `error` is present.
 * See ADR-0007.
 */
export interface ApiResponse<T> {
  readonly success: boolean;
  readonly data?: T;
  readonly error?: ApiError;
  readonly timestamp: string;
  readonly traceId?: string;
}

export interface ApiError {
  /** Stable machine-readable code, e.g. `VAL_001`. Branch on this, never on `message`. */
  readonly code: string;
  /** Sentence safe to show a user as-is. */
  readonly message: string;
  /** Field name to reason, present on validation failures. */
  readonly details?: Readonly<Record<string, string>>;
}

/* ── Auth (POST /api/auth/*) ─────────────────────────────────────────────── */

export interface LoginRequest {
  readonly schoolCode: string;
  readonly username: string;
  readonly password: string;
  /**
   * Lengthens the session from the 8-hour default to 7 days. Sent only when the parent ticks the
   * box, so a shared machine at the school counter keeps the short session.
   */
  readonly rememberMe: boolean;
}

/** The school a successful sign-in resolves to, as echoed back by the login endpoint. */
export interface LoginSchool {
  readonly code: string;
  readonly name: string;
}

export interface LoginResponse {
  readonly userId: string;
  readonly displayName: string;
  /** True when the school issued a temporary password that must be replaced before continuing. */
  readonly mustChangePassword: boolean;
  readonly school: LoginSchool;
  /**
   * The user's effective permissions, resolved once at sign-in.
   *
   * For deciding what to SHOW — a menu item, a button. Never for deciding what is allowed: the
   * server enforces every permission independently, and a client that hides a control is a
   * convenience, not a boundary (ADR-0005).
   */
  readonly permissions: readonly string[];
}

export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

/* ── Bootstrap (GET /api/me) ─────────────────────────────────────────────── */

/**
 * One node of the menu the server returns.
 *
 * There is deliberately no URL, path or component name here. The server sends a stable dotted
 * `id` (`fees.collect`) and this app maps it to a route it owns, so a backend deploy cannot break
 * navigation by naming a route the frontend does not have (ADR-0008). An id this app cannot
 * resolve is dropped and logged, never rendered as a dead link.
 */
export interface NavigationItem {
  /** Stable, dotted, e.g. `fees.collect`. The key into the frontend's route registry. */
  readonly id: string;
  /** Translation key, e.g. `nav.fees.collect`. Never a display string. */
  readonly labelKey: string;
  /**
   * A school's own renaming of the item — "Fees & Dues" rather than "Fees". Present only when a
   * school overrode the default (Tier-2 configuration, ADR-0006), and it wins over `labelKey`
   * because it is that school's data rather than a translation of ours.
   */
  readonly label?: string;
  /** A hint only. Icons are the frontend's business (ADR-0008), so the registry decides. */
  readonly icon: string | null;
  /** Ascending. Ties are broken by id so the menu never reshuffles between loads. */
  readonly order: number;
  readonly children: readonly NavigationItem[];
}

export interface MeUser {
  readonly id: string;
  readonly displayName: string;
  readonly mustChangePassword: boolean;
}

export interface MeSchool {
  readonly code: string;
  readonly name: string;
}

/**
 * The single bootstrap call: who is signed in, where, what they may see, and the menu.
 *
 * One call rather than five, because the alternative is a request waterfall on every page load,
 * on a school's broadband (ADR-0008).
 */
export interface MeResponse {
  readonly user: MeUser;
  readonly school: MeSchool;
  /**
   * Changes when the user's effective permissions are recomputed. Effective permissions are
   * resolved once per session, so this is what tells a long-lived tab its view has gone stale.
   */
  readonly permissionsVersion: string;
  /**
   * For deciding what to SHOW. Never for deciding what is allowed — the server enforces every
   * permission independently (ADR-0005).
   */
  readonly permissions: readonly string[];
  readonly navigation: readonly NavigationItem[];
}

/* ── Paging ──────────────────────────────────────────────────────────────── */

/**
 * One page of a list endpoint, carried as the `data` of an `ApiResponse`.
 *
 * Offset pagination — `?page=0&size=25&sort=occurredAt,desc` — because every list in a school ERP
 * is a bounded, admin-facing table where the user wants "page 7 of 12" and a total, and a cursor
 * cannot answer "how many are there".
 *
 * Four numbers and a list, deliberately: the backend does not serialise Spring Data's own `Page`,
 * so nothing here is coupled to a Spring Data implementation detail.
 *
 * `size` is the size that was *requested*, not the number of rows returned — the last page is
 * shorter. Count `content.length` when you mean "rows on this page".
 */
export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/* ── Audit log (GET /api/audit) ──────────────────────────────────────────── */

/** How an audited attempt ended. A closed set on the backend, so a union here. */
export type AuditOutcome = 'SUCCESS' | 'FAILURE' | 'DENIED';

/**
 * One row of the school's audit log (ADR-0018).
 *
 * ## `action` is a string, not a union, and that is the contract
 *
 * The backend declares its verbs as string constants rather than an enum precisely so a module can
 * name its own action without editing shared code. A union here would quietly re-close the set and
 * make every new verb a compile error in a build that has no reason to know about it. Anything
 * rendering this must cope with a value it has never seen — see `actionLabel` in
 * `features/audit/audit-actions.ts`.
 *
 * ## `changedFields` holds field NAMES
 *
 * Never values, and there is no before/after pair to ask for: the backend rejects anything in this
 * list that is not a plain field name (ADR-0014). Nothing rendering it may imply otherwise.
 *
 * ## `actorName` and `actorRoles` are snapshots
 *
 * They read as they did when the action happened, not as they read now — which is why they are
 * plain strings and not a reference to an account. `actorId` is still the id that was acting, and
 * is what the actor filter narrows on; it is null when nobody was authenticated, as on a failed
 * sign-in.
 */
export interface AuditEvent {
  readonly id: string;
  /** ISO-8601 instant, UTC. Rendered in local time — never shown as the raw string. */
  readonly occurredAt: string;
  readonly actorId: string | null;
  readonly actorName: string | null;
  readonly actorRoles: readonly string[];
  readonly action: string;
  readonly outcome: AuditOutcome;
  readonly entityType: string | null;
  readonly entityId: string | null;
  /** Field names only (ADR-0014). Empty for an event that changed nothing, such as a sign-in. */
  readonly changedFields: readonly string[];
  /** Personal data under the DPDP Act. Detail, not a column. */
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  /** The same trace id the ADR-0007 envelope returns, so an error screen leads back to here. */
  readonly traceId: string | null;
}
