import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ProblemDetail } from '../api/models';

/**
 * Turns backend problem details into a single readable message.
 *
 * TODO(ux): route these into a toast service once the shared notification component exists, and
 * redirect to login on 401 once the identity module lands.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const problem = error.error as ProblemDetail | null;
      const message = problem?.detail ?? problem?.title ?? error.message;
      console.error(`[api] ${req.method} ${req.url} failed: ${message}`, problem?.errors ?? '');
      return throwError(() => error);
    }),
  );
