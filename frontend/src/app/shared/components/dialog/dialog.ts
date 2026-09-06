import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button, ButtonVariant } from '../button/button';

let nextDialogId = 0;

/**
 * A modal that asks one question and offers two answers.
 *
 * ## Why this exists rather than `window.confirm`
 *
 * `unsaved-changes-guard.ts` has carried a TODO asking for this since the school profile shipped,
 * and the first screen that genuinely could not be served by the native prompt is "make this
 * academic session current" — a change that moves every user at the school on to a different year.
 * Three things the native prompt cannot do, and each of them matters for that question:
 *
 * - **Its confirm button says "OK".** The one thing a confirmation is for is making the consequence
 *   legible at the moment of clicking, and "OK" is the least legible label available. Here the
 *   caller supplies the verb — "Make it current" — so the button says what it will do.
 * - **A browser can switch it off.** After a couple of prompts Chrome and Firefox offer "prevent
 *   this page from creating additional dialogs", and from then on `confirm()` returns false without
 *   showing anything. A confirmation step the browser can silently disable is not a confirmation
 *   step, and the failure is invisible from our side.
 * - **It blocks the main thread**, and it cannot show a busy state while the request it guards is
 *   in flight, so the caller has to choose between a frozen page and no feedback.
 *
 * ## What it is, and what it is not
 *
 * Declarative, like `cb-bottom-sheet`: the caller renders it behind an `@if`, so "not open" means
 * "not in the DOM" — nothing to read out, nothing to tab into, no scrim over an uncovered page.
 * It is **not** an imperative `DialogService`; opening a dialog from a router guard needs an
 * app-level overlay and an async `canDeactivate`, which is a different piece of work and is why
 * `unsavedChangesGuard` still uses the native prompt. This component is the presentational half of
 * that when someone builds it.
 *
 * Behaviour comes from the CDK (ADR-0009): focus is trapped and captured by `cdkTrapFocus`, not by
 * hand. Escape and the scrim both mean "cancel". Like the bottom sheet, it emits and never closes
 * itself — the caller owns whether it is open, and **returning focus to whatever opened it is the
 * caller's job**, because only the caller knows what that was and whether it still exists.
 *
 * ## Shape
 *
 * Full width along the bottom edge on a phone and a centred card from tablet portrait up
 * (ADR-0010). One element, moved by CSS: a second component for the compact shape would be a
 * second thing to keep accessible.
 */
@Component({
  selector: 'cb-dialog',
  imports: [A11yModule, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dialog.html',
  styleUrl: './dialog.scss',
  host: {
    '(keydown.escape)': 'onEscape($event)',
  },
})
export class Dialog {
  /** The question, shown as the heading and used as the dialog's accessible name. */
  readonly heading = input.required<string>();
  /** The verb on the button that goes ahead. Never "OK" — say what will happen. */
  readonly confirmLabel = input.required<string>();
  readonly cancelLabel = input('Cancel');
  /** `danger` for something the user would want back. The word still carries the meaning. */
  readonly confirmVariant = input<ButtonVariant>('primary');
  /**
   * The request this dialog guards is in flight. The confirm button shows progress and both
   * answers stop responding — the dialog stays open and stays readable, because closing it on
   * click would leave the user with nothing on screen to explain the wait.
   */
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  /** Ties `aria-labelledby` and `aria-describedby` to this instance, so two cannot cross wires. */
  protected readonly headingId = `cb-dialog-${++nextDialogId}-heading`;
  protected readonly bodyId = `cb-dialog-${nextDialogId}-body`;

  protected onEscape(event: Event): void {
    if (this.busy()) {
      return;
    }
    // Stopped here so a dialog opened over another overlay does not dismiss both at once.
    event.stopPropagation();
    event.preventDefault();
    this.cancelled.emit();
  }

  protected cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }

  protected confirm(): void {
    if (!this.busy()) {
      this.confirmed.emit();
    }
  }
}
