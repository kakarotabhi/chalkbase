import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, CreateSchoolRequest, School } from './models';

/** Unwraps the response envelope so callers work with the payload, not the transport shape. */
const unwrap = map(<T>(response: ApiResponse<T>): T => {
  if (!response.success || response.data === undefined) {
    // The interceptor turns non-2xx into an error, so reaching here means a 2xx that does not
    // match the contract — worth failing loudly rather than handing `undefined` to a template.
    throw new Error(`Malformed API response: ${response.error?.code ?? 'no data'}`);
  }
  return response.data;
});

/**
 * HTTP access to the school module. Components never call HttpClient directly — they go through a
 * service in core/api so the URL surface stays in one place.
 */
@Injectable({ providedIn: 'root' })
export class SchoolApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/schools`;

  list(): Observable<School[]> {
    return this.http.get<ApiResponse<School[]>>(this.baseUrl).pipe(unwrap);
  }

  getById(id: string): Observable<School> {
    return this.http.get<ApiResponse<School>>(`${this.baseUrl}/${id}`).pipe(unwrap);
  }

  create(request: CreateSchoolRequest): Observable<School> {
    return this.http.post<ApiResponse<School>>(this.baseUrl, request).pipe(unwrap);
  }
}
