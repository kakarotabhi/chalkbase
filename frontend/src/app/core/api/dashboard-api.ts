import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, Dashboard } from './models';
import { unwrap } from './unwrap';

/**
 * The landing screen's tiles.
 *
 * **One method, no parameters.** There is nothing to filter and nothing to page — the caller's own
 * session is the only input, the same way `GET /api/me` takes none. Which tiles come back is
 * decided on the server, per field, by the caller's own permissions (ADR-0008 applied to data); this
 * class does not ask which permissions the caller holds and must not start to.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/dashboard`;

  /** `withCredentials`, like every call behind a permission: the session is a cookie. */
  get(): Observable<Dashboard> {
    return this.http
      .get<ApiResponse<Dashboard>>(this.baseUrl, { withCredentials: true })
      .pipe(unwrap);
  }
}
