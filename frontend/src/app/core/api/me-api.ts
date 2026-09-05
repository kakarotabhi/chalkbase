import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, MeResponse } from './models';
import { unwrap } from './unwrap';

/**
 * The bootstrap call: `GET /api/me`.
 *
 * It needs a session and no particular permission — it is what the app asks to find out whether
 * there is a session at all. `withCredentials` for the same reason as the auth endpoints: the
 * session is an HttpOnly cookie the server set, and the browser will not attach it to a
 * cross-origin XHR otherwise.
 *
 * A 401 here is the normal signed-out answer, not an incident: the caller
 * (`SessionBootstrap`) turns it into "send them to sign in".
 */
@Injectable({ providedIn: 'root' })
export class MeApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/me`;

  get(): Observable<MeResponse> {
    return this.http.get<ApiResponse<MeResponse>>(this.url, { withCredentials: true }).pipe(unwrap);
  }
}
