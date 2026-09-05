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

/** RFC 9457 problem detail, the shape every backend error uses. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly errors?: readonly string[];
}
