import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, ReferenceItem } from './models';
import { unwrap } from './unwrap';

/**
 * The two global lists a school-facing form needs and cannot invent: states and boards
 * (ADR-0006, ADR-0029).
 *
 * Two endpoints on two prefixes, not one — `states` is `platform`'s (seeded once, the same list
 * for every school) and `boards` is `school`'s own enum, exposed from the module that owns it.
 * See the doc comment on {@link ReferenceItem} for why the split is deliberate rather than an
 * inconsistency.
 *
 * Both need a session but no permission (`isAuthenticated()` on the backend, like `/api/me`), so
 * every signed-in user can populate a form's pickers regardless of what they otherwise hold.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly statesUrl = `${environment.apiBaseUrl}/reference/states`;
  private readonly boardsUrl = `${environment.apiBaseUrl}/schools/boards`;

  states(): Observable<ReferenceItem[]> {
    return this.http
      .get<ApiResponse<ReferenceItem[]>>(this.statesUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  boards(): Observable<ReferenceItem[]> {
    return this.http
      .get<ApiResponse<ReferenceItem[]>>(this.boardsUrl, { withCredentials: true })
      .pipe(unwrap);
  }
}
