import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CopyFeeStructureRequest,
  CopyFeeStructureResponse,
  FeeConcessionType,
  FeeHead,
  FeeStructure,
  SaveFeeConcessionTypeRequest,
  SaveFeeHeadRequest,
  SaveFeeStructureRequest,
} from './models';
import { unwrap } from './unwrap';

/**
 * HTTP access to the fee module: fee heads, concession types, and the session-scoped fee
 * structure (ADR-0012, ADR-0033).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason academics and attendance carry
 * it — the session is a cookie the server set.
 *
 * **There is no delete method here and there is not meant to be one**, for fee heads and
 * concession types the same reason `AcademicsApi` gives for classes and sections: a head or a
 * concession type already named by a fee structure item must not point at nothing. There is also
 * no per-item PATCH on a structure — `saveStructure` always sends the complete new item list; see
 * `SaveFeeStructureRequest`'s own doc comment.
 */
@Injectable({ providedIn: 'root' })
export class FeeApi {
  private readonly http = inject(HttpClient);
  private readonly headsUrl = `${environment.apiBaseUrl}/fees/heads`;
  private readonly concessionTypesUrl = `${environment.apiBaseUrl}/fees/concession-types`;
  private readonly structuresUrl = `${environment.apiBaseUrl}/fees/structures`;

  /* ── Fee heads ────────────────────────────────────────────────────────── */

  /** Every fee head, active and retired alike, ordered by name. Not paged: a school's price list is a few dozen rows at most. */
  heads(): Observable<FeeHead[]> {
    return this.http
      .get<ApiResponse<FeeHead[]>>(this.headsUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  createHead(request: SaveFeeHeadRequest): Observable<FeeHead> {
    return this.http
      .post<ApiResponse<FeeHead>>(this.headsUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  updateHead(id: string, request: SaveFeeHeadRequest): Observable<FeeHead> {
    return this.http
      .put<ApiResponse<FeeHead>>(`${this.headsUrl}/${id}`, request, { withCredentials: true })
      .pipe(unwrap);
  }

  /* ── Concession types ─────────────────────────────────────────────────── */

  /** Every concession type, active and retired alike, ordered by name. */
  concessionTypes(): Observable<FeeConcessionType[]> {
    return this.http
      .get<ApiResponse<FeeConcessionType[]>>(this.concessionTypesUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  createConcessionType(request: SaveFeeConcessionTypeRequest): Observable<FeeConcessionType> {
    return this.http
      .post<ApiResponse<FeeConcessionType>>(this.concessionTypesUrl, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  updateConcessionType(
    id: string,
    request: SaveFeeConcessionTypeRequest,
  ): Observable<FeeConcessionType> {
    return this.http
      .put<ApiResponse<FeeConcessionType>>(`${this.concessionTypesUrl}/${id}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /* ── Fee structure ────────────────────────────────────────────────────── */

  /** Every class's live structure for one session — a class with none yet is simply absent from the list. */
  structures(sessionId: string): Observable<FeeStructure[]> {
    const params = new HttpParams().set('sessionId', sessionId);
    return this.http
      .get<ApiResponse<FeeStructure[]>>(this.structuresUrl, { params, withCredentials: true })
      .pipe(unwrap);
  }

  /**
   * One class's live structure for one session, or an error the caller reads as "not set up yet"
   * — a 404 here is an ordinary answer, not a fault, since a brand new session starts with none.
   */
  structure(sessionId: string, classId: string): Observable<FeeStructure> {
    return this.http
      .get<ApiResponse<FeeStructure>>(`${this.structuresUrl}/${sessionId}/${classId}`, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Writes the next version of one class's structure for one session.
   *
   * **Send every item, every time.** This never patches: the previous version (if there was one)
   * is superseded and this becomes the new complete structure. A caller that means "change one
   * amount" loads the current structure, edits one field, and resubmits the whole thing.
   */
  saveStructure(
    sessionId: string,
    classId: string,
    request: SaveFeeStructureRequest,
  ): Observable<FeeStructure> {
    return this.http
      .put<ApiResponse<FeeStructure>>(`${this.structuresUrl}/${sessionId}/${classId}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Copies every class's live structure from one session into another, skipping any class that
   * already has one in the destination. Never overwrites anything a school has already entered.
   */
  copyStructure(request: CopyFeeStructureRequest): Observable<CopyFeeStructureResponse> {
    return this.http
      .post<ApiResponse<CopyFeeStructureResponse>>(`${this.structuresUrl}/copy`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }
}
