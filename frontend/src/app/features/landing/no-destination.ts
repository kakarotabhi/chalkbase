import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NavigationStore } from '../../core/navigation/navigation-store';

/**
 * What a signed-in user sees when their menu has nowhere to send them.
 *
 * `landingGuard` sends everyone to the first item of their own navigation (ADR-0008). This screen
 * is the one case where there is no such item: a menu that came back empty, or one whose every id
 * was dropped by the route registry because this build has no screen for it — which is exactly
 * what a backend deploy that switches a module on ahead of the frontend produces.
 *
 * It exists because the alternatives are worse. Redirecting to a fixed route is the hardcoded
 * landing page this whole change removed, and it would hand this user the 403 it handed the
 * auditor. Rendering nothing leaves the shell with an empty nav rail and a blank page, which reads
 * as a product that is broken rather than one that has been told something.
 *
 * The two wordings are a real distinction and not a hedge. "We asked, and you have nothing" is an
 * account or a release problem and the office can fix it; "we could not ask" is this tab's network
 * and reloading fixes it. Telling them apart is the difference between someone waiting for a
 * permission they already have and someone phoning the office about their Wi-Fi.
 */
@Component({
  selector: 'cb-no-destination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './no-destination.html',
  styleUrl: './no-destination.scss',
})
export class NoDestination {
  private readonly navigation = inject(NavigationStore);

  /**
   * Whether a menu was actually received. Empty-because-we-were-told is not the same state as
   * empty-because-the-call-failed, and `NavigationStore` keeps the two apart precisely so a screen
   * like this one does not have to guess.
   */
  protected readonly menuLoaded = this.navigation.loaded;
}
