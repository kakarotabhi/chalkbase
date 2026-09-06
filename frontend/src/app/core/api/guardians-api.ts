import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, GuardianSummary, PageResponse, SaveGuardianRequest } from './models';
import { unwrap } from './unwrap';

/** Rows per page unless a caller says otherwise. Matches the student list, for one reading rhythm. */
export const GUARDIAN_PAGE_SIZE = 25;

/** The largest page the backend will serve; a larger `size` is clamped rather than refused. */
export const GUARDIAN_MAX_PAGE_SIZE = 100;

export interface GuardianSearchQuery {
  /** Zero-based. */
  readonly page?: number;
  readonly size?: number;
  /** Free text over the guardian's details. The user's own search box. */
  readonly q?: string | null;
}

/**
 * HTTP access to guardians as **people** (ADR-0020 §5).
 *
 * Separate from `StudentsApi` because the addresses are separate and so is the idea: `/api/students`
 * is a child and everything hanging off them, `/api/guardians` is a person who may be attached to
 * four of them. A guardian row is shared between siblings on purpose, so that correcting a father's
 * phone number once corrects it for every one of his children — which is precisely why there is no
 * per-student copy of a guardian anywhere in this file.
 *
 * No school parameter, like every tenant-scoped call: the session says which school (ADR-0011).
 *
 * **Confidential (ADR-0014).** A guardian's name, phone number and email are personal data of a
 * child's family. Nothing here may log a value, and no method may put one in a path.
 *
 * **There is no delete**, and there is not meant to be one. ADR-0020 §6: nothing in this model is
 * deleted. Detaching a guardian from one child is `StudentsApi.detachGuardian`, which ends a link
 * and leaves the person in place for their other children.
 */
@Injectable({ providedIn: 'root' })
export class GuardiansApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/guardians`;

  /**
   * One page of guardians, with how many students each is linked to.
   *
   * This is the search that has to come first when someone attaches a guardian to a child. If it
   * is skipped, the office creates a second copy of a father who is already here, and the shared
   * record — the whole point of the model — is quietly lost.
   */
  search(query: GuardianSearchQuery = {}): Observable<PageResponse<GuardianSummary>> {
    let params = new HttpParams()
      .set('page', Math.max(0, query.page ?? 0))
      .set('size', clampSize(query.size ?? GUARDIAN_PAGE_SIZE));

    const q = query.q?.trim();
    if (q) {
      params = params.set('q', q);
    }

    return this.http
      .get<ApiResponse<PageResponse<GuardianSummary>>>(this.baseUrl, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Creates the person, and answers with them.
   *
   * The id in that answer is load-bearing: creating a guardian from inside "attach a guardian to
   * this child" is create-then-link, two requests, and the second one needs the id the first
   * returned. There is no combined endpoint, deliberately (see `LinkGuardianRequest`).
   */
  create(request: SaveGuardianRequest): Observable<GuardianSummary> {
    return this.http
      .post<ApiResponse<GuardianSummary>>(this.baseUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  /**
   * Corrects the person, everywhere at once.
   *
   * The response is discarded and the caller re-reads its page: `linkedStudentCount` is computed
   * per row, and a screen that patched in a body it had not asked the contract about would be
   * guessing at a number it is showing to a user.
   */
  update(id: string, request: SaveGuardianRequest): Observable<void> {
    return this.http
      .put<ApiResponse<unknown>>(`${this.baseUrl}/${id}`, request, { withCredentials: true })
      .pipe(map((): void => undefined));
  }
}

function clampSize(size: number): number {
  return Math.min(Math.max(1, Math.trunc(size)), GUARDIAN_MAX_PAGE_SIZE);
}
