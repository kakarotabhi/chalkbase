import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map } from 'rxjs';
import { SessionBootstrap } from './session-bootstrap';
import { SessionStore } from './session-store';

/**
 * Sends a session that still owes its forced password change to the one screen that can fix it,
 * whichever shell route was actually asked for.
 *
 * ## Why this exists
 *
 * Before this guard, the check lived in two places, and both of them run *before* a particular
 * route is chosen rather than in front of routing itself: the login screen sends someone signing
 * in with a temporary password straight to `/change-password`, and `landingGuard` repeats the
 * check for a reload of `/`. Neither one is in front of `/students/:id` or any other address typed
 * or bookmarked directly, so a deep link got past both — not a security hole, since
 * `SessionStandingFilter` re-checks `must_change_password` on every API call server-side and
 * answers `AUTH_008` (ADR-0023), but a bad experience: the screen renders and then every request it
 * makes fails with a refusal about a temporary password, instead of landing the person on the
 * screen that actually helps.
 *
 * A guard on the shell route closes it for every child at once, which is why this file exists
 * instead of a third copy of the same `if` living somewhere else in `core/auth`.
 *
 * ## Order in `app.routes.ts`
 *
 * This runs second in the shell route's `canActivate` array, after `authGuard`. That order is load
 * -bearing, not incidental: `authGuard` answers "is anyone signed in", and only once that is `true`
 * does "does *this* session still owe a password change" become a question worth asking.
 * `canActivate` guards on one route resolve in array order and a `UrlTree` from an earlier one
 * short-circuits the rest, so if `authGuard` sends someone to `/login` this guard never runs at
 * all — the two cannot disagree about where a signed-out visitor goes.
 *
 * ## Why this cannot trap `/change-password` itself
 *
 * `/change-password` is declared as a sibling of the shell route in `app.routes.ts`, not as one of
 * its children, so it never runs through this `canActivate` at all — there is no child route for
 * this guard to redirect *to* that then bounces back through the same check.
 *
 * ## `landingGuard`'s own check
 *
 * `landingGuard` still asks `session.mustChangePassword()` for the reload-of-`/` case. That branch
 * is now unreachable in practice — this guard, on the parent route, already redirects before any
 * child guard runs — but it is left alone here rather than edited: `core/navigation` belongs to a
 * different lane while several are running in parallel (see `docs/development/parallel-work.md`),
 * and removing dead code there is a smaller and safer change to make later, in that file's own
 * lane, than to risk here.
 */
export const passwordChangeGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
  const bootstrap = inject(SessionBootstrap);
  const session = inject(SessionStore);
  const router = inject(Router);

  return bootstrap.ensure().pipe(
    map((signedIn) => {
      // `authGuard` already answered "is anyone signed in" and redirected to `/login` if not — see
      // the doc comment above for why that makes `!signedIn` unreachable here. Checked anyway,
      // because "runs after" is a fact about `app.routes.ts`, not a guarantee this file can enforce
      // on its own.
      if (!signedIn || !session.mustChangePassword()) {
        return true;
      }
      return router.parseUrl('/change-password');
    }),
  );
};
