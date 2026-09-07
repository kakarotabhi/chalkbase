import { HttpInterceptorFn } from '@angular/common/http';
import { timeout } from 'rxjs';

/**
 * Caps how long any request may hang before this app gives up and lets the caller retry.
 *
 * ## Why this exists
 *
 * Before this, no call through `HttpClient` had a timeout of any kind — nothing in `core/api` and
 * nothing configured on the client — so a request that never gets an answer (a dropped mobile
 * connection, a proxy that swallows the packet) waited until the browser's own limit gave up,
 * which is minutes, not seconds. A hung save on the student form spun its button indefinitely with
 * no way back (`docs/status.md`, Known gaps and debt). This interceptor is the "one number, one
 * place" that gap asked for.
 *
 * ## Why 150 seconds
 *
 * This app's only deployed environment today is a Render free instance
 * (`docs/operations/render-free-tier.md`), and free instances sleep: the first request after idle
 * measured 86 s, 96 s and 121 s to a plain 200. A save is exactly the kind of request someone
 * issues after leaving a tab open for a while, so the number has to outlast a cold wake-up, not
 * just the ordinary case — a shorter timeout would turn a slow-but-successful save into a false
 * failure on every cold start, which is worse than the gap it closes. 150 s clears the worst
 * measured wake (121 s) with margin for jitter, while still resolving well short of the ~257 s a
 * full Spring Boot restart takes, so a request against a JVM that is genuinely gone — not merely
 * asleep — still ends the wait instead of spinning for minutes. Revisit downward once the app is
 * only ever deployed to an always-on host (ADR-0015's Coolify VPS).
 *
 * ## The one exception
 *
 * `GET /api/me` is excluded, for the reason `SessionBootstrap`'s own doc comment already gives: a
 * timeout there would resolve `authGuard` as "not signed in" and send a genuinely slow-but-signed-
 * in user to a login screen served by the same slow connection, looping them between two screens
 * instead of waiting once. `SessionBootstrap` already renders a boot state that says what it is
 * waiting for and never throws on its own — a timeout on this one endpoint would only be a slower
 * way to reach the wrong answer, not a safer one.
 *
 * ## What the caller sees
 *
 * `timeout()` turns a hang into an ordinary error that is not an `HttpErrorResponse` — exactly
 * like a dropped connection already was. Every screen's own error handling already branches on
 * `apiErrorCode()` (`core/api/api-error.ts`), which answers `UNKNOWN` for anything that is not a
 * backend envelope and falls back to a generic "check your connection and try again" message (see
 * e.g. `StudentForm.failure`'s `default` case); none of them clear the form on a failure, only on
 * success, and all of them stop their own spinner in the same `subscribe`'s `error` callback. So a
 * timed-out save leaves the typed values on screen, stops the button, and offers the same submit
 * to try again — none of that is new here, it was only ever unreachable because nothing had timed
 * out yet.
 */
export const REQUEST_TIMEOUT_MS = 150_000;

/** Paths this interceptor leaves alone. See "The one exception" above. */
const NO_TIMEOUT_PATHS = ['/api/me'];

export const timeoutInterceptor: HttpInterceptorFn = (req, next) => {
  if (NO_TIMEOUT_PATHS.some((path) => pathOf(req.url) === path)) {
    return next(req);
  }
  return next(req).pipe(timeout(REQUEST_TIMEOUT_MS));
};

/** The address without its query string — a path is what identifies an endpoint, not its params. */
function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}
