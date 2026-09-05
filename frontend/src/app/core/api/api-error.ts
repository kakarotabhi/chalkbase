import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from './models';

/** What `apiErrorCode` returns when the failure never reached the backend, or carried no code. */
export const UNKNOWN_ERROR_CODE = 'UNKNOWN';

/**
 * Pulls the stable `error.code` out of a failed request.
 *
 * Screens branch on the result, never on `error.message` (ADR-0007): the message is user-facing
 * copy that gets reworded and eventually translated, while the code is part of the contract. A
 * network failure or a non-envelope response yields `UNKNOWN`, which callers should treat as
 * "something went wrong", not as a specific outcome.
 */
export function apiErrorCode(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const envelope = error.error as ApiResponse<never> | null;
    return envelope?.error?.code ?? UNKNOWN_ERROR_CODE;
  }
  return UNKNOWN_ERROR_CODE;
}
