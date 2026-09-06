import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map } from 'rxjs';
import { SessionBootstrap } from '../auth/session-bootstrap';
import { SessionStore } from '../auth/session-store';
import { NavigationStore } from './navigation-store';

/**
 * Decides what "signed in, no particular destination" means for *this* user.
 *
 * The empty path used to be `redirectTo: 'students'`, and before that `redirectTo: 'schools'`.
 * Both were a constant standing in for a question only the server can answer — the auditor holds
 * `platform:audit:read` and nothing else, so `students` put a 403 in front of them as the first
 * screen after sign-in, and the constant before it did the same to everyone. Every role with a
 * different permission set breaks the next constant, so there is no constant here: the landing
 * target is the first item of the user's own navigation, which the server already filtered by
 * permission (ADR-0008). Nothing about the authorization model is re-derived here; the menu is
 * read, not reasoned about.
 *
 * ## It waits
 *
 * The menu arrives asynchronously from `GET /api/me`. `ensure()` is what the shell's own guard
 * subscribes to, so this joins the same in-flight call rather than starting a second one, and the
 * decision is made once there is a tree to read — racing it would land everyone on whatever the
 * empty store looked like, which is the bug in a new costume.
 *
 * ## When there is nowhere to go
 *
 * `true`, and the route's own component renders: a screen that says why it is empty. That case is
 * a user whose entire menu was dropped by the route registry — a backend that switched a module on
 * before this build shipped its screens — and every alternative is worse. A redirect to a fixed
 * route is the constant we just removed, and an empty shell is a product that looks broken without
 * saying so.
 */
export const landingGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
  const bootstrap = inject(SessionBootstrap);
  const session = inject(SessionStore);
  const navigation = inject(NavigationStore);
  const router = inject(Router);

  return bootstrap.ensure().pipe(
    map(() => {
      // A temporary password wins over everything, the same way it does at sign-in: there is
      // nothing useful to do with an account whose password is still on the slip the office handed
      // over. This also covers the reload — the login screen's redirect is gone by then, and
      // without this a refresh would quietly let the forced change be skipped.
      if (session.mustChangePassword()) {
        return router.parseUrl('/change-password');
      }

      const path = navigation.landingPath();
      return path ? router.parseUrl(path) : true;
    }),
  );
};
