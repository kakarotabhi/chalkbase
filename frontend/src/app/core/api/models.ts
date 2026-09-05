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
