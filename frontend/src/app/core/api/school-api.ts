import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateSchoolRequest, School } from './models';

/**
 * HTTP access to the school module. Components never call HttpClient directly — they go through a
 * service in core/api so the URL surface stays in one place.
 */
@Injectable({ providedIn: 'root' })
export class SchoolApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/schools`;

  list(): Observable<School[]> {
    return this.http.get<School[]>(this.baseUrl);
  }

  getById(id: string): Observable<School> {
    return this.http.get<School>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateSchoolRequest): Observable<School> {
    return this.http.post<School>(this.baseUrl, request);
  }
}
