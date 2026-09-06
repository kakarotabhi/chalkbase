import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, AuditEvent, PageResponse } from './models';
import { unwrap } from './unwrap';

/**
 * Rows per page unless a caller says otherwise. The backend's own default, restated here so the
 * screen can show "1–25 of 137" without waiting for the first response to learn what it asked for.
 */
export const AUDIT_PAGE_SIZE = 25;

/**
 * The largest page the backend will serve: `spring.data.web.pageable.max-page-size` is 100, and a
 * larger `size` is silently clamped rather than refused. Clamping here too means the screen's
 * arithmetic — "showing 101–200 of 137" — cannot disagree with the rows it actually received.
 */
export const AUDIT_MAX_PAGE_SIZE = 100;

/**
 * What to narrow the audit log by. Every field is optional; the backend ANDs whatever is sent.
 *
 * `from` and `to` are **ISO-8601 instants, not dates**, and the asymmetry is deliberate on the
 * backend: `from` is inclusive and `to` is exclusive, so two consecutive ranges neither overlap
 * nor skip a row. Turning a day a human picked into those two instants is the caller's job and is
 * the one place this is easy to get wrong — see `AuditLog`.
 */
export interface AuditSearchQuery {
  /** Zero-based. */
  readonly page?: number;
  readonly size?: number;
  /** One person's actions, by account id. */
  readonly actorId?: string | null;
  /** One verb, exactly — `LOGIN_FAILED`. Not a prefix and not a search. */
  readonly action?: string | null;
  /** Inclusive lower bound on `occurredAt`. */
  readonly from?: string | null;
  /** **Exclusive** upper bound on `occurredAt`. */
  readonly to?: string | null;
}

/**
 * Reading this school's audit log.
 *
 * **One method, and it is a GET.** There is no create, update or delete here because there is no
 * such endpoint and there never will be (ADR-0018 §6): an audit log an administrator can edit is a
 * log that says whatever the last person to be embarrassed by it wanted it to say. Retention is a
 * scheduled platform job, not an API. Do not add a write method to this class.
 *
 * No school parameter, like every tenant-scoped call: the session says which school (ADR-0011).
 */
@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/audit`;

  /**
   * One page of the log, newest first.
   *
   * The sort is sent explicitly rather than left to the backend's default. It is the same value —
   * `occurredAt,desc` — but an audit log read in an order the screen did not choose is a page of
   * rows whose "Previous" and "Next" mean something else, and that should not depend on a default
   * on the other side of the wire.
   *
   * `withCredentials`, like every call behind a permission: the session is a cookie the server
   * set, and a browser will not attach it to a cross-origin request otherwise.
   */
  search(query: AuditSearchQuery = {}): Observable<PageResponse<AuditEvent>> {
    let params = new HttpParams()
      .set('page', Math.max(0, query.page ?? 0))
      .set('size', clampSize(query.size ?? AUDIT_PAGE_SIZE))
      .set('sort', 'occurredAt,desc');

    // An absent filter is an absent parameter, never `actorId=` — the backend binds an empty
    // string to a UUID by failing the request, and an empty `action` would match nothing at all.
    params = withOptional(params, 'actorId', query.actorId);
    params = withOptional(params, 'action', query.action);
    params = withOptional(params, 'from', query.from);
    params = withOptional(params, 'to', query.to);

    return this.http
      .get<ApiResponse<PageResponse<AuditEvent>>>(this.baseUrl, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }
}

function clampSize(size: number): number {
  return Math.min(Math.max(1, Math.trunc(size)), AUDIT_MAX_PAGE_SIZE);
}

function withOptional(params: HttpParams, name: string, value: string | null | undefined) {
  const trimmed = value?.trim();
  return trimmed ? params.set(name, trimmed) : params;
}
