import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStore } from '../auth/session-store';
import { ApiResponse } from '../api/models';

/**
 * Requests that must not bounce to the login screen when they fail with a 401.
 *
 * Login and logout, because a 401 from login is wrong credentials rather than a lost session.
 * `/api/me` too: a 401 there is the ordinary signed-out answer to the question "is anyone signed
 * in?", and `SessionBootstrap` is already turning it into a redirect the guard controls. Letting
 * this interceptor navigate as well would race the guard's own `UrlTree` and lose the `returnTo`
 * the user needs.
 */
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/logout', '/api/me'];

/**
 * Logs every API failure with its trace id, and sends an expired session back to the login screen.
 *
 * TODO(ux): route these into a toast service once the shared notification component exists.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(SessionStore);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const envelope = error.error as ApiResponse<never> | null;
      const apiError = envelope?.error;
      const traceId = envelope?.traceId ?? error.headers?.get('X-Request-Id') ?? 'unknown';
      // The boot-time /api/me is how the app asks "is anyone signed in?", so a 401 from it is an
      // answer, not a fault. Logging it as an error on every visit to the login page trains people
      // to ignore the console, which is where the traceId has to be readable when something is
      // genuinely wrong.
      const isExpectedSignedOutProbe = error.status === 401 && isEndpoint(req.url, '/api/me');
      if (isExpectedSignedOutProbe) {
        return throwError(() => error);
      }

      // The trace id is the whole point of logging this: it is what ties a user's report to the
      // backend log line for the same request.
      console.error(
        `[api] ${req.method} ${req.url} -> ${error.status} ${apiError?.code ?? 'UNKNOWN'} ` +
          `(traceId ${traceId}): ${apiError?.message ?? error.message}`,
        apiError?.details ?? '',
      );

      // A 401 anywhere else means the session went away — it expired, or an administrator signed
      // the user out. Failing silently would leave the screen showing stale data it can no longer
      // refresh, so clear what we hold and send them to sign in again.
      //
      // The endpoints above are excluded: a 401 from login is wrong credentials, and bouncing the
      // login screen to itself would wipe the error the user needs to read.
      const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => isEndpoint(req.url, path));
      if (error.status === 401 && !isAuthEndpoint) {
        session.signedOut();
        void router.navigate(['/login'], { queryParams: { returnTo: router.url } });
      }

      return throwError(() => error);
    }),
  );
};

/**
 * Whether a request URL is that endpoint, rather than merely containing its text.
 *
 * A substring test would make `/api/me` swallow `/api/members`, which is the sort of match that
 * works until the day somebody adds the longer path.
 */
function isEndpoint(url: string, path: string): boolean {
  const withoutQuery = url.split(/[?#]/)[0];
  return withoutQuery === path;
}
