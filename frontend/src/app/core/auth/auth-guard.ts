import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map } from 'rxjs';
import { SessionBootstrap } from './session-bootstrap';

/**
 * Keeps the app shell closed to anyone who is not signed in, and remembers where they were going.
 *
 * The question is answered by the server, not by this tab's memory. `SessionBootstrap` asks
 * `GET /api/me`; the real session is an HttpOnly cookie this code cannot see, so anything else
 * would be guessing — which is exactly how a reload used to bounce a perfectly valid session back
 * to the login screen.
 *
 * The wait is only ever for the first navigation. The call starts at application boot and an
 * answer already held is reused rather than re-asked, so this does not put a request in front of
 * every route change.
 *
 * What it does put in front of the first one is the entire shell: the router activates nothing
 * until this guard resolves. That is the right trade — drawing an authenticated shell for someone
 * who may turn out not to be signed in would be worse than a wait — but it is a wait, and on a
 * slow connection a long one. `App` renders a boot state outside the router outlet for as long as
 * it lasts, which is what keeps a blocked guard from being a blank page.
 *
 * Either way this is convenience, never access control: enforcement is the API, where every
 * endpoint is authenticated and a 401 clears the store and returns here via `apiErrorInterceptor`.
 */
export const authGuard: CanActivateFn = (_route, state): Observable<boolean | UrlTree> => {
  const bootstrap = inject(SessionBootstrap);
  const router = inject(Router);

  return bootstrap.ensure().pipe(map((signedIn) => signedIn || loginTree(router, state.url)));
};

function loginTree(router: Router, url: string): UrlTree {
  // `url` is what was asked for, already serialised with its query and fragment. Sending someone
  // who followed a deep link to sign in and then dropping them on the dashboard is a small
  // betrayal repeated every morning, so the destination is carried through. `/` is where sign-in
  // lands anyway, so it is left off rather than cluttering the address bar.
  const queryParams = url && url !== '/' ? { returnTo: url } : {};

  return router.createUrlTree(['/login'], { queryParams });
}
