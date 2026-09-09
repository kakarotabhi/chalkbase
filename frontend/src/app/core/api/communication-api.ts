import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AcknowledgeRecipientRequest,
  ApiResponse,
  CircularDetail,
  CircularRecipientResponse,
  CircularSummary,
  CreateCircularRequest,
  PageResponse,
  TargetPreviewResponse,
} from './models';
import { unwrap } from './unwrap';

/** Rows per page for the circular list and a circular's own recipient list. */
export const CIRCULAR_PAGE_SIZE = 25;

/**
 * HTTP access to the communication module: composing, publishing and reading circulars, and their
 * per-recipient status (Phase 2).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason — the session is a cookie the
 * server set.
 *
 * **There is no "update a draft's targets" call.** This build ships no endpoint for it — a circular
 * is composed whole, with every target given at creation, and a school that wants a different
 * audience discards the draft and composes another.
 */
@Injectable({ providedIn: 'root' })
export class CommunicationApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/communication/circulars`;

  /** Every circular, newest first. */
  list(page = 0, size = CIRCULAR_PAGE_SIZE): Observable<PageResponse<CircularSummary>> {
    const params = new HttpParams().set('page', Math.max(0, page)).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<CircularSummary>>>(this.baseUrl, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** One circular in full. */
  get(circularId: string): Observable<CircularDetail> {
    return this.http
      .get<ApiResponse<CircularDetail>>(`${this.baseUrl}/${circularId}`, { withCredentials: true })
      .pipe(unwrap);
  }

  /**
   * How many actively enrolled students one candidate class or section would reach — the
   * composer's own preview, before it is added as a target.
   *
   * @param sectionId omitted to mean "every active section of `classId`".
   */
  targetPreview(classId: string, sectionId?: string): Observable<TargetPreviewResponse> {
    let params = new HttpParams().set('classId', classId);
    if (sectionId) {
      params = params.set('sectionId', sectionId);
    }
    return this.http
      .get<ApiResponse<TargetPreviewResponse>>(`${this.baseUrl}/target-preview`, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Composes a circular in `DRAFT`, with every target given. */
  create(request: CreateCircularRequest): Observable<CircularDetail> {
    return this.http
      .post<ApiResponse<CircularDetail>>(this.baseUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }

  /** Publishes a circular: locks it and generates its recipients. */
  publish(circularId: string): Observable<CircularDetail> {
    return this.http
      .post<ApiResponse<CircularDetail>>(
        `${this.baseUrl}/${circularId}/publish`,
        {},
        { withCredentials: true },
      )
      .pipe(unwrap);
  }

  /** A published circular's own recipient list, in publish order. */
  recipients(
    circularId: string,
    page = 0,
    size = CIRCULAR_PAGE_SIZE,
  ): Observable<PageResponse<CircularRecipientResponse>> {
    const params = new HttpParams().set('page', Math.max(0, page)).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<CircularRecipientResponse>>>(
        `${this.baseUrl}/${circularId}/recipients`,
        { params, withCredentials: true },
      )
      .pipe(unwrap);
  }

  /** Records that a recipient's family has acknowledged the circular, on their behalf. */
  acknowledge(
    circularId: string,
    recipientId: string,
    request: AcknowledgeRecipientRequest,
  ): Observable<CircularRecipientResponse> {
    return this.http
      .post<ApiResponse<CircularRecipientResponse>>(
        `${this.baseUrl}/${circularId}/recipients/${recipientId}/acknowledge`,
        request,
        { withCredentials: true },
      )
      .pipe(unwrap);
  }
}
