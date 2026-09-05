import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * The root is a bare outlet. The shell is a routed component wrapping the authenticated routes,
 * so screens that must not have navigation — sign-in, the forced password change — can sit outside
 * it rather than hiding pieces of it.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<router-outlet />',
})
export class App {}
