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
