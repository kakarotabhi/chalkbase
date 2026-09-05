import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from './session-store';

/**
 * Keeps the app shell closed to anyone who is not signed in, and remembers where they were going.
 *
 * TODO(me): this guard trusts the client. `SessionStore` is memory that the login response filled
 * in, so it answers "did this tab sign in?", not "is there a session?" — a reload empties it, and
 * the real session is an HttpOnly cookie this code cannot see. Once `GET /api/me` exists (the next
 * slice; it does not exist today, which is why nothing is called here) this becomes an async guard
 * that asks the server, seeds `SessionStore` and the server-driven navigation from the answer, and
 * only then resolves — which also fixes the reload case, where a perfectly valid session is
 * currently bounced to sign-in.
 *
 * Either way this is convenience, never access control: enforcement is the API, where every
 * endpoint is authenticated and a 401 clears the store and returns here via `apiErrorInterceptor`.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  const router = inject(Router);

  if (session.isSignedIn()) {
    return true;
  }

  // `state.url` is what was asked for, already serialised with its query and fragment. Sending
  // someone who followed a deep link to sign in and then dropping them on the dashboard is a small
  // betrayal repeated every morning, so the destination is carried through. `/` is where sign-in
  // lands anyway, so it is left off rather than cluttering the address bar.
  const queryParams = state.url && state.url !== '/' ? { returnTo: state.url } : {};

  return router.createUrlTree(['/login'], { queryParams });
};
