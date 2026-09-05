import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, CreateSchoolRequest, School } from './models';
import { unwrap } from './unwrap';

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
