import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStore } from '../auth/session-store';
import { ApiResponse } from '../api/models';

/** Requests that must not bounce to the login screen when they fail with a 401. */
const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/logout'];

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
      // Login and logout are excluded: a 401 from login is wrong credentials, and bouncing the
      // login screen to itself would wipe the error the user needs to read.
      const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.includes(path));
      if (error.status === 401 && !isAuthEndpoint) {
        session.signedOut();
        void router.navigate(['/login'], { queryParams: { returnTo: router.url } });
      }

      return throwError(() => error);
    }),
  );
};
