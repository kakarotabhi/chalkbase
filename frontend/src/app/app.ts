import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SessionBootstrap } from './core/auth/session-bootstrap';
import { BootState } from './layout/boot-state/boot-state';

/**
 * The root: a router outlet, and the one thing that can be on screen while the router has nothing
 * to show.
 *
 * The shell is a routed component wrapping the authenticated routes, so screens that must not have
 * navigation — sign-in, the forced password change — can sit outside it rather than hiding pieces
 * of it. That leaves a gap this component has to cover: `authGuard` blocks the shell route until
 * `GET /api/me` answers, and Angular renders nothing for a route it has not activated, so a bare
 * outlet is a blank document for however long that call takes. Anything drawn during the wait has
 * to be outside the outlet, which means here.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BootState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
})
export class App {
  private readonly bootstrap = inject(SessionBootstrap);

  /**
   * Whether the outlet currently holds a route.
   *
   * The boot state is for an empty document, and `/login` is not one: it has no guard, so it
   * activates immediately and is perfectly usable while `/api/me` is still in flight. Without this
   * check, someone who opened the sign-in screen directly on a slow connection would watch a
   * loading panel sit on top of a login form they could have been typing into.
   */
  protected readonly outletFilled = signal(false);

  protected readonly showBootState = computed(
    () => this.bootstrap.bootstrapping() && !this.outletFilled(),
  );
}
