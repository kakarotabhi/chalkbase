import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiResponse } from '../api/models';

/**
 * Turns backend problem details into a single readable message.
 *
 * TODO(ux): route these into a toast service once the shared notification component exists, and
 * redirect to login on 401 once the identity module lands.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
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
      return throwError(() => error);
    }),
  );
