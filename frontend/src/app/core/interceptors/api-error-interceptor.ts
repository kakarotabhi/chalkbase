import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AUTH_ERROR } from '../api/auth-api';
import { SessionBootstrap } from '../auth/session-bootstrap';
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

/** Where a session that still owes its forced password change is sent. */
const CHANGE_PASSWORD = '/change-password';

/**
 * Logs every API failure with its trace id, and sends an expired session back to the login screen.
 *
 * TODO(ux): route these into a toast service once the shared notification component exists.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(SessionStore);
  const bootstrap = inject(SessionBootstrap);

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
      //
      // **The path, never the query string.** `GET /api/students?q=Aarav%20Sharma` failing would
      // otherwise print a child's name into the console, and a name is Confidential under
      // ADR-0014 — never logged, at any level. The query adds nothing here anyway: the trace id
      // is what leads to the backend's own record of the same request.
      console.error(
        `[api] ${req.method} ${pathOf(req.url)} -> ${error.status} ${apiError?.code ?? 'UNKNOWN'} ` +
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

      // ADR-0008's staleness rule: a 403 means this tab's menu and permissions might be built from
      // a role that no longer applies, so before the error reaches the screen, refetch `/api/me`
      // and let it re-render navigation. `/api/me` needs only a session, not a permission (see
      // `MeApi`), so it should never itself answer 403 — the endpoint check below is what stops
      // this from recursing into itself if it somehow did anyway. `refreshAfterForbidden` shares
      // one in-flight refetch across concurrent 403s and never throws, so this branch always
      // reaches the `throwError` at the end and the user always sees the failure that sent it here.
      if (error.status === 403 && !isEndpoint(req.url, '/api/me')) {
        const versionBeforeRefetch = session.permissionsVersion();

        return bootstrap.refreshAfterForbidden().pipe(
          switchMap(() => {
            if (!session.isSignedIn()) {
              // The refetch discovered the session is actually gone (a 401 on `/api/me`), not
              // merely stale — `SessionBootstrap` has already cleared it. Same destination as an
              // ordinary 401 elsewhere in this interceptor: showing "you do not have permission"
              // to someone who is not signed in at all would be the wrong error entirely.
              void router.navigate(['/login'], { queryParams: { returnTo: router.url } });
              return throwError(() => error);
            }

            // Whether the refetch actually changed the permissions version decides nothing about
            // *whether* the error is shown — it is shown either way — only what it would mean to
            // reword it, so this is where that is decided rather than left to whichever screen
            // happens to render next:
            //
            // - Version changed: the menu really was stale, and is now correct. This 403 was the
            //   right answer for the permission set as it stood at the moment of the call, which
            //   was already the moment the request was sent — no different in kind from any other
            //   correct refusal.
            // - Version unchanged: nothing was stale. The 403 is a plain, current denial.
            //
            // Either way the server just refused this exact call, which is true information the
            // user needs regardless of which case produced it. Every screen already renders its
            // own copy for a 403 keyed off `apiError.code` (PERM_001 and friends), never off
            // `error.message` (ADR-0007), and that copy is accurate in both cases: "you do not
            // have permission to do that" is correct whether the permission was lost five minutes
            // ago or never held at all. A "your access just changed, try again" variant would only
            // be true in the first case, and there is no cheap way for a screen to know which one
            // happened without threading a new field through every 403 in the app for a distinction
            // that does not change what the user should do next — ask their school, or go back.
            // The corrected, re-rendered menu is what tells the truth about *future* attempts;
            // this one already failed and says so. So the only place the distinction earns its
            // keep is the console, for whoever is debugging a report of "the menu changed on me" —
            // never the screen, and never anything closer to a permission name than a version id.
            if (session.permissionsVersion() !== versionBeforeRefetch) {
              console.info('[api] permissions changed mid-session; navigation was refreshed');
            }
            redirectIfPasswordChangeRequired(apiError, router);
            return throwError(() => error);
          }),
        );
      }

      // The account is still on the password its school issued it, and the server has just refused
      // this call because of it. This is a redirect the server asked for, not a rule re-derived
      // here: there is deliberately no client-side guard checking `mustChangePassword` before
      // navigating, because that would be a second copy of the authorization model living on the
      // wrong side of the wire (ADR-0008). The client reacts to what it is told.
      //
      // The session is NOT cleared. Unlike a 401, it is real and still usable for the one thing
      // that matters — replacing the password — and signing the user out here would send them to
      // a login screen where the same temporary password lets them straight back in, into the same
      // refusal. A reload arrives here having lost the temporary password from memory, and the
      // change-password screen sends that case on to sign in for itself.
      //
      // This is the fallback for a 403 from `/api/me` itself, which the branch above excludes from
      // the staleness refetch to avoid recursion. `/api/me` is documented as never answering this
      // code in the first place (see `AUTH_ERROR.PASSWORD_CHANGE_REQUIRED`), so in practice this
      // line is unreachable — kept rather than assumed, because "unreachable given the current
      // backend" is not the same guarantee as "cannot happen".
      redirectIfPasswordChangeRequired(apiError, router);

      return throwError(() => error);
    }),
  );
};

/**
 * Sends a session that still owes its forced password change to the screen that can fix it.
 *
 * Pulled out of the main pipeline because ADR-0008's staleness rule now needs to run it from two
 * places: after a successful (or merely attempted) refetch for an ordinary 403, and as the
 * fallback for the one 403 that refetch never runs for — see the call sites.
 */
function redirectIfPasswordChangeRequired(
  apiError: ApiResponse<never>['error'],
  router: Router,
): void {
  if (
    apiError?.code === AUTH_ERROR.PASSWORD_CHANGE_REQUIRED &&
    pathOf(router.url) !== CHANGE_PASSWORD
  ) {
    void router.navigateByUrl(CHANGE_PASSWORD);
  }
}

/**
 * Whether a request URL is that endpoint, rather than merely containing its text.
 *
 * A substring test would make `/api/me` swallow `/api/members`, which is the sort of match that
 * works until the day somebody adds the longer path.
 */
function isEndpoint(url: string, path: string): boolean {
  return pathOf(url) === path;
}

/**
 * The address without its query string.
 *
 * A search box sends what the user typed as `?q=…`, and on these screens that is a child's or a
 * guardian's name (ADR-0014). It may travel to the server, because typing it was the user's own
 * choice; it may not be written to a log, and the browser console is a log.
 */
function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}
