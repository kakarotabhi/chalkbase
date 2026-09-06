import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CreateSchoolRequest,
  School,
  SchoolProfile,
  UpdateSchoolProfileRequest,
} from './models';
import { unwrap } from './unwrap';

/**
 * HTTP access to the school module. Components never call HttpClient directly — they go through a
 * service in core/api so the URL surface stays in one place.
 *
 * Two addresses, and the difference is the whole tenancy model. `/api/schools` is the platform's
 * register of every campus. `/api/school/profile` is *this* school — singular, with no id, because
 * the session already says which one (ADR-0011).
 */
@Injectable({ providedIn: 'root' })
export class SchoolApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/schools`;
  private readonly profileUrl = `${environment.apiBaseUrl}/school/profile`;

  list(): Observable<School[]> {
    return this.http.get<ApiResponse<School[]>>(this.baseUrl).pipe(unwrap);
  }

  getById(id: string): Observable<School> {
    return this.http.get<ApiResponse<School>>(`${this.baseUrl}/${id}`).pipe(unwrap);
  }

  create(request: CreateSchoolRequest): Observable<School> {
    return this.http.post<ApiResponse<School>>(this.baseUrl, request).pipe(unwrap);
  }

  /**
   * The signed-in school's own profile.
   *
   * `withCredentials`, like the bootstrap call and for the same reason: this needs the session
   * cookie, and a browser will not attach it to a cross-origin request otherwise.
   */
  profile(): Observable<SchoolProfile> {
    return this.http
      .get<ApiResponse<SchoolProfile>>(this.profileUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  /** A full replacement, never a patch — the screen sends the whole form back. */
  updateProfile(request: UpdateSchoolProfileRequest): Observable<SchoolProfile> {
    return this.http
      .put<ApiResponse<SchoolProfile>>(this.profileUrl, request, { withCredentials: true })
      .pipe(unwrap);
  }
}
