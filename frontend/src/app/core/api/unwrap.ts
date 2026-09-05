import { map } from 'rxjs';
import { ApiResponse } from './models';

/**
 * Unwraps the response envelope so callers work with the payload, not the transport shape.
 *
 * Every service in `core/api` pipes through this, which is what lets components receive plain
 * payloads and never see `success` / `timestamp` / `traceId` (ADR-0007).
 */
export const unwrap = map(<T>(response: ApiResponse<T>): T => {
  if (!response.success || response.data === undefined) {
    // The interceptor turns non-2xx into an error, so reaching here means a 2xx that does not
    // match the contract — worth failing loudly rather than handing `undefined` to a template.
    throw new Error(`Malformed API response: ${response.error?.code ?? 'no data'}`);
  }
  return response.data;
});

/**
 * The same, for endpoints whose success payload is empty (`ApiResponse<void>`) — logout and
 * password change. `data` is legitimately absent there, so only `success` is checked.
 */
export const unwrapVoid = map((response: ApiResponse<void>): void => {
  if (!response.success) {
    throw new Error(`Malformed API response: ${response.error?.code ?? 'not successful'}`);
  }
});
