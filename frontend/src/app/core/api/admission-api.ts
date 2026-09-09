import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AssignCounsellorRequest,
  CreateEnquiryRequest,
  EnquiryDetailResponse,
  EnquiryFollowUpQueueItem,
  EnquirySource,
  EnquiryStatus,
  EnquirySummary,
  LogFollowUpRequest,
  PageResponse,
  UserSummary,
} from './models';
import { unwrap } from './unwrap';

/** Rows per page for the enquiry list and the follow-up queue. */
export const ENQUIRY_PAGE_SIZE = 25;

/** What the enquiry list may be narrowed by — every field optional, all ANDed. */
export interface EnquiryFilter {
  readonly q?: string | null;
  readonly status?: EnquiryStatus | null;
  readonly source?: EnquirySource | null;
  readonly assignedCounsellorId?: string | null;
  readonly interestedClassId?: string | null;
}

/**
 * HTTP access to the admission module's enquiry management (Phase 2, FR-016/017).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason — the session is a cookie the
 * server set.
 */
@Injectable({ providedIn: 'root' })
export class AdmissionApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/admissions`;

  /** One page of the enquiry list, filtered. */
  enquiries(
    filter: EnquiryFilter,
    page = 0,
    size = ENQUIRY_PAGE_SIZE,
  ): Observable<PageResponse<EnquirySummary>> {
    let params = new HttpParams().set('page', Math.max(0, page)).set('size', size);
    if (filter.q) {
      params = params.set('q', filter.q);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.source) {
      params = params.set('source', filter.source);
    }
    if (filter.assignedCounsellorId) {
      params = params.set('assignedCounsellorId', filter.assignedCounsellorId);
    }
    if (filter.interestedClassId) {
      params = params.set('interestedClassId', filter.interestedClassId);
    }
    return this.http
      .get<ApiResponse<PageResponse<EnquirySummary>>>(`${this.baseUrl}/enquiries`, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** One enquiry, in full, with its whole follow-up history. */
  enquiry(id: string): Observable<EnquiryDetailResponse> {
    return this.http
      .get<ApiResponse<EnquiryDetailResponse>>(`${this.baseUrl}/enquiries/${id}`, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Captures a new enquiry. */
  create(request: CreateEnquiryRequest): Observable<EnquiryDetailResponse> {
    return this.http
      .post<ApiResponse<EnquiryDetailResponse>>(`${this.baseUrl}/enquiries`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Assigns or reassigns the counsellor responsible for one enquiry's follow-up. */
  assign(id: string, request: AssignCounsellorRequest): Observable<EnquiryDetailResponse> {
    return this.http
      .post<ApiResponse<EnquiryDetailResponse>>(`${this.baseUrl}/enquiries/${id}/assign`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Logs a follow-up against one enquiry, returning the enquiry as it now stands. */
  logFollowUp(id: string, request: LogFollowUpRequest): Observable<EnquiryDetailResponse> {
    return this.http
      .post<ApiResponse<EnquiryDetailResponse>>(
        `${this.baseUrl}/enquiries/${id}/follow-ups`,
        request,
        {
          withCredentials: true,
        },
      )
      .pipe(unwrap);
  }

  /** Every account this school could assign an enquiry to — the assignment picker's own read. */
  counsellors(): Observable<UserSummary[]> {
    return this.http
      .get<ApiResponse<UserSummary[]>>(`${this.baseUrl}/counsellors`, { withCredentials: true })
      .pipe(unwrap);
  }

  /**
   * The due-date follow-up queue.
   *
   * @param mine true (the default) for the caller's own assigned enquiries; false for the whole
   *   school's.
   */
  dueFollowUps(
    mine = true,
    page = 0,
    size = ENQUIRY_PAGE_SIZE,
  ): Observable<PageResponse<EnquiryFollowUpQueueItem>> {
    const params = new HttpParams()
      .set('mine', String(mine))
      .set('page', Math.max(0, page))
      .set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<EnquiryFollowUpQueueItem>>>(`${this.baseUrl}/follow-ups/due`, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }
}
