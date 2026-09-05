import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ICON_GLYPHS, IconGlyph, IconName } from './icon-glyphs';

/**
 * One inline SVG icon from the Chalkbase set.
 *
 * Inline rather than a sprite or a font: an icon font renders as a stray glyph when the font fails
 * on a slow connection, and a sprite needs a second request before the shell can draw. Icons here
 * are decoration next to a label, so the `<svg>` is `aria-hidden` and the element it sits in
 * carries the name.
 */
@Component({
  selector: 'cb-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './icon.html',
  styleUrl: './icon.scss',
})
export class Icon {
  readonly name = input.required<IconName>();
  /** Edge length in px. Defaults to the 20px the designs use in navigation and menus. */
  readonly size = input(20);

  protected readonly glyph = computed<IconGlyph>(
    () => ICON_GLYPHS[this.name()] ?? ICON_GLYPHS.placeholder,
  );
}
