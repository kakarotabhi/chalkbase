import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CorrectionRequestResponse,
  DecideCorrectionRequest,
  MarkAttendanceRequest,
  PageResponse,
  RequestCorrectionRequest,
  SectionAttendanceView,
  StudentAttendanceRecord,
} from './models';
import { unwrap } from './unwrap';

/** Rows per page for the correction queue. There is no reason to match any other list's size. */
export const CORRECTION_QUEUE_PAGE_SIZE = 25;

/**
 * HTTP access to the attendance module: daily marking, one student's history, and correction
 * requests (Phase 2, ADR-0030).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason — the session is a cookie the
 * server set.
 *
 * **There is no way to name a period here.** Only the daily grain has a write path in this build;
 * a period-wise screen is later work and gets its own methods when it exists.
 */
@Injectable({ providedIn: 'root' })
export class AttendanceApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/attendance`;

  /**
   * A section's roster for one date, with each student's mark if one exists yet.
   *
   * @param date `yyyy-MM-dd`. Omitted for today — the marking screen's own common case.
   */
  sectionAttendance(sectionId: string, date?: string): Observable<SectionAttendanceView> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http
      .get<ApiResponse<SectionAttendanceView>>(`${this.baseUrl}/sections/${sectionId}`, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Marks or edits a section's attendance for one date, every entry in one call. */
  mark(sectionId: string, request: MarkAttendanceRequest): Observable<SectionAttendanceView> {
    return this.http
      .post<ApiResponse<SectionAttendanceView>>(`${this.baseUrl}/sections/${sectionId}`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** One student's daily attendance between two dates (`yyyy-MM-dd`), inclusive. */
  studentHistory(
    studentId: string,
    from: string,
    to: string,
  ): Observable<StudentAttendanceRecord[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .get<ApiResponse<StudentAttendanceRecord[]>>(`${this.baseUrl}/students/${studentId}`, {
        params,
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Files a correction request against a locked mark. */
  requestCorrection(
    markId: string,
    request: RequestCorrectionRequest,
  ): Observable<CorrectionRequestResponse> {
    return this.http
      .post<ApiResponse<CorrectionRequestResponse>>(
        `${this.baseUrl}/marks/${markId}/correction-requests`,
        request,
        { withCredentials: true },
      )
      .pipe(unwrap);
  }

  /** Every correction request ever filed against one mark, newest first. */
  correctionHistory(markId: string): Observable<CorrectionRequestResponse[]> {
    return this.http
      .get<ApiResponse<CorrectionRequestResponse[]>>(
        `${this.baseUrl}/marks/${markId}/correction-requests`,
        {
          withCredentials: true,
        },
      )
      .pipe(unwrap);
  }

  /** The admin's queue: requests awaiting a decision, oldest first. */
  pendingCorrections(
    page = 0,
    size = CORRECTION_QUEUE_PAGE_SIZE,
  ): Observable<PageResponse<CorrectionRequestResponse>> {
    const params = new HttpParams().set('page', Math.max(0, page)).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<CorrectionRequestResponse>>>(
        `${this.baseUrl}/correction-requests`,
        {
          params,
          withCredentials: true,
        },
      )
      .pipe(unwrap);
  }

  /** Approves or rejects one correction request. */
  decideCorrection(
    requestId: string,
    decision: DecideCorrectionRequest,
  ): Observable<CorrectionRequestResponse> {
    return this.http
      .post<ApiResponse<CorrectionRequestResponse>>(
        `${this.baseUrl}/correction-requests/${requestId}/decision`,
        decision,
        { withCredentials: true },
      )
      .pipe(unwrap);
  }
}
