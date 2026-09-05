import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Icon } from '../icon/icon';

let nextSheetId = 0;

/**
 * A panel that rises from the bottom of the screen over the content behind it.
 *
 * This is the compact-width form of a dialog (ADR-0010): confirmations, pickers and the
 * navigation "More" menu all become one of these on a phone, because a centred modal on a 360px
 * screen is a full-screen dialog wearing a costume, and the bottom is where the thumb already is.
 *
 * It is modal, so it behaves like one:
 *
 * - focus is trapped inside it and moved into it on open, by the CDK's focus trap rather than by
 *   hand — writing our own is rebuilding, worse, the one part of a component library that is
 *   genuinely accessibility-relevant (ADR-0009);
 * - Escape closes it, and so does a tap on the scrim or the close button;
 * - it emits `closed` and nothing else. **Returning focus to whatever opened it is the caller's
 *   job**, because only the caller knows what that was and whether it still exists.
 *
 * The parent renders it behind an `@if`, so "not open" means "not in the DOM" — nothing to read
 * out, nothing to tab into, and no scrim over a page that is not covered.
 */
@Component({
  selector: 'cb-bottom-sheet',
  imports: [A11yModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bottom-sheet.html',
  styleUrl: './bottom-sheet.scss',
  host: {
    '(keydown.escape)': 'onEscape($event)',
  },
})
export class BottomSheet {
  /** The sheet's accessible name, shown as its heading. */
  readonly heading = input.required<string>();
  /** The close button's accessible name. Says what closes, for a screen reader out of context. */
  readonly closeLabel = input('Close');

  readonly closed = output<void>();

  /** Ties `aria-labelledby` to this instance's heading, so two open sheets cannot cross wires. */
  protected readonly headingId = `cb-bottom-sheet-${++nextSheetId}-heading`;

  protected onEscape(event: Event): void {
    // Stopped here so a sheet opened from inside another overlay does not close both at once.
    event.stopPropagation();
    event.preventDefault();
    this.closed.emit();
  }

  protected close(): void {
    this.closed.emit();
  }
}
