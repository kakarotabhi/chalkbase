import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * `raised` is the design's `.card` — `--cb-surface-raised` plus `--cb-shadow-1` — for a piece of
 * content sitting above the page. `surface` is flatter and un-shadowed, `--cb-surface`, for a
 * region that reads as part of the page's own chrome rather than a card floating over it: a filter
 * bar, a toolbar strip.
 */
export type CardTone = 'raised' | 'surface';

/** `none` when the card's own children (a `.section`, a sticky footer) carry their own padding. */
export type CardPadding = 'none' | 'sm' | 'md' | 'lg';

/**
 * The card surface: background, a 1px border, `radius-md`, and — the part that kept going missing
 * — a shadow.
 *
 * Every one of the six designed artboards draws `.card` the same way, shadow included. The build
 * had it hand-written in thirteen feature stylesheets, and thirteen independent copies is exactly
 * how `--cb-shadow-1` survived in only one of them (`school-profile.scss`): every other card in the
 * app was flat where the design has depth, not because anyone decided flat was better, but because
 * twelve copies each had to remember a line the thirteenth didn't need to.
 *
 * Deliberately a wrapper around whatever the caller puts inside it rather than a card with its own
 * header/body/footer slots: the thirteen originals disagreed on internal structure — some are a
 * single padded block, one (`school-profile`) is a stack of `.section`s each with its own padding
 * and a sticky action bar flush against the bottom — and `padding="none"` is what lets that last
 * shape keep working under this component instead of the other way round.
 *
 * `accent` is the "this one is current" treatment three screens had each written out in full: a
 * tinted surface, a rule down the leading edge, so a running academic session, an active enrolment
 * and a primary guardian all read the same way at a glance.
 */
@Component({
  selector: 'cb-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './card.html',
  styleUrl: './card.scss',
  host: {
    class: 'cb-card',
    '[class.cb-card--surface]': "tone() === 'surface'",
    '[class.cb-card--pad-none]': "padding() === 'none'",
    '[class.cb-card--pad-sm]': "padding() === 'sm'",
    '[class.cb-card--pad-lg]': "padding() === 'lg'",
    '[class.cb-card--accent]': 'accent()',
  },
})
export class Card {
  readonly tone = input<CardTone>('raised');
  readonly padding = input<CardPadding>('md');
  /** The one item in a list that is current, active, or primary. See the class doc comment. */
  readonly accent = input(false);
}
