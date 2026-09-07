import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';

/**
 * How long the app waits before admitting it is waiting, in milliseconds.
 *
 * A warm `/api/me` answers in well under 200ms, and on that load anything drawn here would be a
 * flash of "Loading Chalkbase" between two frames of nothing — worse than the blank frame it
 * replaces, and the reason a boot state has to be held back rather than shown eagerly. 600ms is
 * roughly three times the warm answer, which leaves room for a mid-range Android to do its first
 * paint as well, and it is still inside the ~1s at which a person stops believing their tap did
 * anything. So a fast load never sees this, and a load that is going to be slow says so before the
 * user starts doubting it.
 */
const SHOW_AFTER_MS = 600;

/**
 * How long before the wait is acknowledged as unusual.
 *
 * Ten seconds is the point at which attention goes elsewhere and a progress indicator on its own
 * stops being information — an unexplained spinner for two minutes is the blank page again in a
 * nicer colour. What changes at this threshold is the words, not the mechanism.
 */
const SLOW_AFTER_MS = 10_000;

/** Nothing drawn yet · the ordinary wait · the wait that has gone on too long to leave unsaid. */
type BootPhase = 'quiet' | 'waiting' | 'slow';

/**
 * What the app shows while it is asking the server who is signed in.
 *
 * ## Why anything is here at all
 *
 * `authGuard` guards the shell route, and the router renders nothing until its guards resolve —
 * so between application boot and `/api/me` answering, a bare `<router-outlet />` is an empty
 * document. On a warm connection that is two frames nobody sees. On a cold API or a school's bad
 * link it was measured at 121 seconds of white page with no console error: the app looked broken
 * when it was working exactly as designed. The guard is right to block — showing an authenticated
 * shell to someone who may not be signed in would be worse — so the thing that fills the wait has
 * to live outside the outlet, which is what this component is.
 *
 * ## The delay is inside the component, not around it
 *
 * `App` puts this in the DOM the moment the bootstrap starts and this component decides when it
 * has anything to say. That is deliberate for the screen reader: a live region has to exist before
 * its contents change for the change to be announced, so an element that appears already holding
 * its text is announced unreliably. Here the region is present from the first frame and empty,
 * and each phase mutates it.
 *
 * ## One mechanism, two thresholds
 *
 * Appearing and escalating are the same state machine on the same clock — the phase only ever
 * moves forward, and the component is destroyed the moment the answer lands, which is what
 * cancels both timers. Two independent mechanisms would have to agree about the case where the
 * answer arrives between them.
 *
 * There is no third phase and no failure phase on purpose. This component never gives up: the app
 * sets no HTTP timeout, because timing out would resolve the guard as "not signed in" and land the
 * user on a login screen that talks to the same slow server, so they would loop. Waiting honestly
 * is the better behaviour, and the wait is what this says out loud.
 */
@Component({
  selector: 'cb-boot-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './boot-state.html',
  styleUrl: './boot-state.scss',
})
export class BootState {
  private readonly currentPhase = signal<BootPhase>('quiet');

  protected readonly phase = this.currentPhase.asReadonly();

  constructor() {
    const timers = [
      setTimeout(() => this.currentPhase.set('waiting'), SHOW_AFTER_MS),
      setTimeout(() => this.currentPhase.set('slow'), SLOW_AFTER_MS),
    ];

    // The usual case is that the answer lands first and this component is destroyed with both
    // timers still pending. Leaving them running would set a signal on a destroyed component and,
    // on the fast path, do it after the shell is already on screen.
    inject(DestroyRef).onDestroy(() => timers.forEach(clearTimeout));
  }
}
