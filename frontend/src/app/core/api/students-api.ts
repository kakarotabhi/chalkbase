import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CreateEnrolmentRequest,
  Enrolment,
  LinkGuardianRequest,
  PageResponse,
  SaveStudentRequest,
  StudentDetail,
  StudentStatus,
  StudentSummary,
  UpdateEnrolmentRequest,
  UpdateStudentGuardianRequest,
} from './models';
import { unwrap } from './unwrap';

/** Rows per page unless a caller says otherwise. The backend's own default, restated here. */
export const STUDENT_PAGE_SIZE = 25;

/**
 * The largest page the backend will serve. A larger `size` is clamped rather than refused, so
 * clamping here too keeps "showing 101–200 of 137" from ever being printed.
 */
export const STUDENT_MAX_PAGE_SIZE = 100;

/** The default order. Whole name, ascending — there is no surname to sort by (ADR-0020 §1). */
export const STUDENT_DEFAULT_SORT = 'fullName,asc';

/**
 * What to narrow the student list by. Every field is optional; the backend ANDs whatever is sent.
 *
 * `q` is free text over the name and the admission number, and it is the one place a child's name
 * legitimately travels in a query string: the user typed it into a search box, which is their own
 * choice. Nothing else here may carry one — see the class comment.
 */
export interface StudentSearchQuery {
  /** Zero-based. */
  readonly page?: number;
  readonly size?: number;
  /** Free text over name and admission number. */
  readonly q?: string | null;
  readonly status?: StudentStatus | null;
  /** One section, by id. The class ladder is what turns a class and a section into this. */
  readonly sectionId?: string | null;
  readonly sort?: string;
}

/**
 * HTTP access to the student module: students, their enrolments, and their guardian links
 * (ADR-0020).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason — the session is a cookie the
 * server set, and a browser will not attach it to a cross-origin request otherwise.
 *
 * ## Everything here is Confidential (ADR-0014)
 *
 * Names, dates of birth, admission numbers, guardians' phone numbers. **Nothing in this file may
 * log a request, a response or a value**, and no method may put a name or a date of birth into a
 * path or a query string — a URL lands in a server access log, and an access log is a log. `q` is
 * the single exception and it is the user's own search text.
 *
 * ## There is no delete of a student, and there is not meant to be one
 *
 * ADR-0020 §6: a student is `WITHDRAWN` or `TRANSFERRED`, never removed, because fees, attendance
 * and marks all reference them. Erasure under the DPDP Act is a different operation with its own
 * design, and a `DELETE` endpoint would not have been an answer to it. Do not add one.
 *
 * `detachGuardian` is the one delete here and it removes a **link**, not a person: the guardian
 * record survives because their other children still point at it (ADR-0020 §5).
 *
 * ## Several writes deliberately discard their response
 *
 * The contract states a status code for the enrolment and guardian-link writes but not a body, and
 * two of them change more than the row they name — setting a guardian primary clears the previous
 * primary server-side. A caller that patched only the row it asked about would show two primary
 * guardians until something else refetched. So those methods answer `void` and the screen re-reads
 * `GET /api/students/{id}`, which is the one shape the contract does pin down.
 */
@Injectable({ providedIn: 'root' })
export class StudentsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/students`;

  /* ── Students ────────────────────────────────────────────────────────── */

  /** One page of students, ordered by name unless the caller asks otherwise. */
  search(query: StudentSearchQuery = {}): Observable<PageResponse<StudentSummary>> {
    let params = new HttpParams()
      .set('page', Math.max(0, query.page ?? 0))
      .set('size', clampSize(query.size ?? STUDENT_PAGE_SIZE))
      // Sent explicitly rather than left to the backend's default: a list read in an order the
      // screen did not choose is a page whose "Previous" and "Next" mean something else.
      .set('sort', query.sort ?? STUDENT_DEFAULT_SORT);

    // An absent filter is an absent parameter, never `sectionId=` — the backend binds an empty
    // string to a UUID by failing the request, and an empty `q` would be a search for nothing.
    params = withOptional(params, 'q', query.q);
    params = withOptional(params, 'status', query.status);
    params = withOptional(params, 'sectionId', query.sectionId);

    return this.http
      .get<ApiResponse<PageResponse<StudentSummary>>>(this.baseUrl, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** The whole record: the student, their guardians and their enrolment history. */
  get(id: string): Observable<StudentDetail> {
    return this.http
      .get<ApiResponse<StudentDetail>>(`${this.baseUrl}/${id}`, { withCredentials: true })
      .pipe(unwrap);
  }

  create(request: SaveStudentRequest): Observable<StudentDetail> {
    return this.http
      .post<ApiResponse<StudentDetail>>(this.baseUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  update(id: string, request: SaveStudentRequest): Observable<StudentDetail> {
    return this.http
      .put<ApiResponse<StudentDetail>>(`${this.baseUrl}/${id}`, request, { withCredentials: true })
      .pipe(unwrap);
  }

  /* ── Enrolments ──────────────────────────────────────────────────────── */

  /** Puts a student into a section for a session. The answer is the enrolment that was created. */
  addEnrolment(studentId: string, request: CreateEnrolmentRequest): Observable<Enrolment> {
    return this.http
      .post<ApiResponse<Enrolment>>(`${this.baseUrl}/${studentId}/enrolments`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Corrects one enrolment. See the class comment for why the response is discarded. */
  updateEnrolment(
    studentId: string,
    enrolmentId: string,
    request: UpdateEnrolmentRequest,
  ): Observable<void> {
    return this.http
      .put<ApiResponse<unknown>>(
        `${this.baseUrl}/${studentId}/enrolments/${enrolmentId}`,
        request,
        { withCredentials: true },
      )
      .pipe(discardBody);
  }

  /* ── Guardian links ──────────────────────────────────────────────────── */

  /**
   * Links a guardian who **already exists** to this student.
   *
   * There is no endpoint that creates a person and links them in one go, and that is the model
   * working: finding the guardian first is what keeps a father with four children here one record
   * rather than four (ADR-0020 §5).
   */
  linkGuardian(studentId: string, request: LinkGuardianRequest): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.baseUrl}/${studentId}/guardians`, request, {
        withCredentials: true,
      })
      .pipe(discardBody);
  }

  /**
   * Corrects what a guardian is to this child, and whether they are the main contact.
   *
   * Two rows can change: setting a new primary clears the old one in the same transaction. That is
   * the reason this answers `void` — see the class comment.
   */
  updateGuardianLink(
    studentId: string,
    linkId: string,
    request: UpdateStudentGuardianRequest,
  ): Observable<void> {
    return this.http
      .put<ApiResponse<unknown>>(`${this.baseUrl}/${studentId}/guardians/${linkId}`, request, {
        withCredentials: true,
      })
      .pipe(discardBody);
  }

  /**
   * Ends one link. **The guardian record survives** — their other children still point at it, and
   * removing the person because one child no longer lists them would break the others.
   *
   * A 204 carries no envelope at all, so nothing here may unwrap one.
   */
  detachGuardian(studentId: string, linkId: string): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown> | null>(`${this.baseUrl}/${studentId}/guardians/${linkId}`, {
        withCredentials: true,
      })
      .pipe(discardBody);
  }
}

/**
 * Throws away a success payload the contract does not pin down.
 *
 * Not `unwrapVoid`: that reads `success` off the body, and a 204 has no body to read it off. The
 * interceptor has already turned every non-2xx into an error by the time this runs, so reaching
 * here at all means the write went through.
 */
const discardBody = map((): void => undefined);

function clampSize(size: number): number {
  return Math.min(Math.max(1, Math.trunc(size)), STUDENT_MAX_PAGE_SIZE);
}

function withOptional(params: HttpParams, name: string, value: string | null | undefined) {
  const trimmed = value?.trim();
  return trimmed ? params.set(name, trimmed) : params;
}
