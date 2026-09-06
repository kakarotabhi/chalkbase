import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

/**
 * The one button in Chalkbase.
 *
 * Loading and disabled are separate inputs on purpose. Loading means "your click was received and
 * is in flight"; disabled means "you cannot do this, and something next to me says why". Both stop
 * a second submit, but only loading shows progress, and neither ever hides the control — a button
 * that vanishes leaves the user with nothing to read.
 */
@Component({
  selector: 'cb-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './button.html',
  styleUrl: './button.scss',
  host: { '[class.is-block]': 'block()' },
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  /**
   * Put on the native `<button>`, not on the host.
   *
   * So `document.querySelector('#…')` finds something that can actually take focus. A screen that
   * moves focus back to the control a panel was opened from — the academics screens do, in half a
   * dozen places — otherwise has to know that `cb-button` is a wrapper and go looking inside it.
   */
  readonly id = input<string | null>(null);
  readonly disabled = input(false);
  readonly loading = input(false);
  /** Full width. Used for the single action at the bottom of a form on a phone. */
  readonly block = input(false);
  /** Replaces the label while loading, e.g. "Signing in…". Empty keeps the label and adds a spinner. */
  readonly loadingLabel = input('');

  protected readonly isInert = computed(() => this.disabled() || this.loading());

  // Built as one string rather than a static class plus bindings so there is exactly one source of
  // truth for what ends up on the element.
  protected readonly classes = computed(() => {
    const classes = ['btn', `btn--${this.variant()}`];
    if (this.block()) {
      classes.push('btn--block');
    }
    if (this.loading()) {
      classes.push('is-loading');
    }
    return classes.join(' ');
  });
}
