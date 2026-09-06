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

/**
 * The HTTP status a failure came back with, or 0 if the request never got an answer.
 *
 * **A deliberate exception to "branch on the code, never the message"** (ADR-0007), and a narrow
 * one. Some failures never reach a controller and so never get an envelope to carry a code: an
 * upload larger than the servlet container's cap is refused by the container itself, and what
 * arrives is a bare `413` with a body this app cannot parse. A screen that only read `error.code`
 * would call that "something went wrong" and leave the user to guess, when the one thing they need
 * to be told is that the file is too big.
 *
 * Use it for exactly that kind of outcome. Anything the backend can put a code on should still be
 * branched on by code, because a status is shared by a dozen unrelated failures and a code is not.
 */
export function apiErrorStatus(error: unknown): number {
  return error instanceof HttpErrorResponse ? error.status : 0;
}
