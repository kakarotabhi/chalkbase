import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AcademicSession,
  ApiResponse,
  CreateClassRequest,
  CreateSectionRequest,
  CreateSubjectRequest,
  PageResponse,
  ReorderClassesRequest,
  SaveAcademicSessionRequest,
  SchoolClass,
  Section,
  Subject,
  UpdateClassRequest,
  UpdateSectionRequest,
  UpdateSubjectRequest,
} from './models';
import { unwrap } from './unwrap';

/** Rows per page unless a caller says otherwise. Matches the guardian and student lists. */
export const SUBJECT_PAGE_SIZE = 25;

/** The largest page the backend will serve; a larger `size` is clamped rather than refused. */
export const SUBJECT_MAX_PAGE_SIZE = 100;

export interface SubjectSearchQuery {
  /** Zero-based. */
  readonly page?: number;
  readonly size?: number;
  /** Free text over the subject's name and code. The user's own search box. */
  readonly q?: string | null;
}

/**
 * HTTP access to the academics module: the school's academic years, and its ladder of classes and
 * sections (ADR-0019).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason — the session is a cookie the
 * server set, and a browser will not attach it to a cross-origin request otherwise.
 *
 * **There is no delete method here and there is not meant to be one.** ADR-0019 decided that a
 * class or a section is deactivated rather than removed, because by the time anything references
 * one it is too late to discover that deleting it was wrong, and a mistyped name is fixed by
 * renaming. Do not add one.
 */
@Injectable({ providedIn: 'root' })
export class AcademicsApi {
  private readonly http = inject(HttpClient);
  private readonly sessionsUrl = `${environment.apiBaseUrl}/academics/sessions`;
  private readonly classesUrl = `${environment.apiBaseUrl}/academics/classes`;
  private readonly sectionsUrl = `${environment.apiBaseUrl}/academics/sections`;
  private readonly subjectsUrl = `${environment.apiBaseUrl}/academics/subjects`;

  /* ── Academic sessions ───────────────────────────────────────────────── */

  /** Every session, newest first. Not paged: a school has one of these a year. */
  sessions(): Observable<AcademicSession[]> {
    return this.http
      .get<ApiResponse<AcademicSession[]>>(this.sessionsUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  createSession(request: SaveAcademicSessionRequest): Observable<AcademicSession> {
    return this.http
      .post<ApiResponse<AcademicSession>>(this.sessionsUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  updateSession(id: string, request: SaveAcademicSessionRequest): Observable<AcademicSession> {
    return this.http
      .put<ApiResponse<AcademicSession>>(`${this.sessionsUrl}/${id}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Move the whole school on to this session, and get every session back as it now stands.
   *
   * A POST with no body, and the previous current session is cleared server-side in the same
   * transaction. Nothing here may try to keep the two in step with a second request: a client that
   * did could leave the school with two current sessions, or none, if the second call failed.
   *
   * **The answer is the whole list, not the one session that was named.** Two rows change, so a
   * caller handed only one would have nothing to clear `current` on the other with, and would show
   * two current sessions until something else refetched. Same shape and same reason as
   * `reorderClasses`.
   */
  makeSessionCurrent(id: string): Observable<AcademicSession[]> {
    return this.http
      .post<ApiResponse<AcademicSession[]>>(`${this.sessionsUrl}/${id}/current`, null, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /* ── Classes and sections ────────────────────────────────────────────── */

  /**
   * The ladder, already ordered by `sequence`, each class carrying its sections ordered by name.
   *
   * Inactive classes and sections are **included and flagged**, not filtered out. That is what
   * lets a screen show a deactivated rung in its place rather than as a hole in the ladder, and it
   * is what makes the id list `reorder` needs available without a second request.
   */
  classes(): Observable<SchoolClass[]> {
    return this.http
      .get<ApiResponse<SchoolClass[]>>(this.classesUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  /** Appended to the end of the ladder. Position is chosen afterwards, with `reorder`. */
  createClass(request: CreateClassRequest): Observable<SchoolClass> {
    return this.http
      .post<ApiResponse<SchoolClass>>(this.classesUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  updateClass(id: string, request: UpdateClassRequest): Observable<SchoolClass> {
    return this.http
      .put<ApiResponse<SchoolClass>>(`${this.classesUrl}/${id}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Reorder the whole ladder in one transaction, and get it back as the server now holds it.
   *
   * **`classIds` must name every class, in the new order** — the inactive ones too. The server
   * refuses a partial list rather than renumbering what is left, because a caller that dropped an
   * id would silently close the gap and lose a rung. The URL is `/classes/order` and not
   * `/classes/{id}/…`: the request is about the ladder, not about one class.
   */
  reorderClasses(classIds: readonly string[]): Observable<SchoolClass[]> {
    const body: ReorderClassesRequest = { classIds: [...classIds] };
    return this.http
      .put<ApiResponse<SchoolClass[]>>(`${this.classesUrl}/order`, body, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  createSection(classId: string, request: CreateSectionRequest): Observable<Section> {
    return this.http
      .post<ApiResponse<Section>>(`${this.classesUrl}/${classId}/sections`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * A section is addressed on its own, without its class: the id identifies it, and routing the
   * update through the class would invite a caller to pass a class the section is not in.
   */
  updateSection(id: string, request: UpdateSectionRequest): Observable<Section> {
    return this.http
      .put<ApiResponse<Section>>(`${this.sectionsUrl}/${id}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /* ── Subjects ─────────────────────────────────────────────────────────── */

  /**
   * One page of the subject catalogue, by name unless the caller sorts otherwise.
   *
   * Paged, unlike {@link classes} and {@link sessions}: a subject catalogue can genuinely run into
   * dozens of rows for a school offering electives at Classes 9-12, where a class ladder tops out
   * at a few dozen rungs and a school gains one session a year. Inactive subjects are included and
   * flagged, not filtered out, for the reason `Subject` gives.
   */
  subjects(query: SubjectSearchQuery = {}): Observable<PageResponse<Subject>> {
    let params = new HttpParams()
      .set('page', Math.max(0, query.page ?? 0))
      .set('size', clampSubjectPageSize(query.size ?? SUBJECT_PAGE_SIZE));

    const q = query.q?.trim();
    if (q) {
      params = params.set('q', q);
    }

    return this.http
      .get<ApiResponse<PageResponse<Subject>>>(this.subjectsUrl, { params, withCredentials: true })
      .pipe(unwrap);
  }

  createSubject(request: CreateSubjectRequest): Observable<Subject> {
    return this.http
      .post<ApiResponse<Subject>>(this.subjectsUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  updateSubject(id: string, request: UpdateSubjectRequest): Observable<Subject> {
    return this.http
      .put<ApiResponse<Subject>>(`${this.subjectsUrl}/${id}`, request, { withCredentials: true })
      .pipe(unwrap);
  }
}

function clampSubjectPageSize(size: number): number {
  return Math.min(Math.max(1, Math.trunc(size)), SUBJECT_MAX_PAGE_SIZE);
}
