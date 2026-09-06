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

/**
 * The per-field reasons a validation failure carried, or an empty map.
 *
 * The backend keys `details` by field name (ADR-0007), and those names are the request record's
 * own — which is what lets a form put each message under the control it belongs to, instead of one
 * sentence at the top and the user left to find the field.
 */
export function apiErrorDetails(error: unknown): Readonly<Record<string, string>> {
  if (error instanceof HttpErrorResponse) {
    const envelope = error.error as ApiResponse<never> | null;
    return envelope?.error?.details ?? {};
  }
  return {};
}
