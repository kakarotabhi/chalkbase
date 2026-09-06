/**
 * The icon set, as geometry rather than markup.
 *
 * Shapes are data instead of a string of SVG so nothing has to be pushed through `innerHTML` and
 * a sanitiser bypass, and so a typo is a compile error rather than a blank square. Every glyph is
 * drawn on the same 24×24 grid with a 1.75 stroke, taken from the approved designs
 * (`docs/artifacts/*.dc.html`) — they are stroked, never filled, so they take their colour from
 * `currentColor` and invert with the rest of the interface in dark mode.
 */

export interface IconCircle {
  readonly cx: number;
  readonly cy: number;
  readonly r: number;
}

export interface IconRect {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly rx: number;
}

export interface IconGlyph {
  readonly paths?: readonly string[];
  readonly circles?: readonly IconCircle[];
  readonly rects?: readonly IconRect[];
}

const GLYPHS = {
  dashboard: {
    rects: [
      { x: 3.5, y: 3.5, width: 7, height: 7, rx: 1.6 },
      { x: 13.5, y: 3.5, width: 7, height: 7, rx: 1.6 },
      { x: 3.5, y: 13.5, width: 7, height: 7, rx: 1.6 },
      { x: 13.5, y: 13.5, width: 7, height: 7, rx: 1.6 },
    ],
  },
  students: {
    circles: [{ cx: 9, cy: 8, r: 3.2 }],
    paths: [
      'M3.5 19.5c0-3 2.5-5 5.5-5s5.5 2 5.5 5',
      'M16 6.2a3 3 0 0 1 0 5.6',
      'M17.5 14.8c2 .6 3.2 2.4 3.2 4.7',
    ],
  },
  fees: {
    paths: [
      'M5.5 3.5h13v17l-2.2-1.6-2.1 1.6-2.2-1.6-2.2 1.6-2.1-1.6-2.2 1.6z',
      'M9 8h6',
      'M9 11.5h6',
    ],
  },
  attendance: {
    rects: [{ x: 3.5, y: 5, width: 17, height: 15.5, rx: 2 }],
    paths: ['M8 3v4', 'M16 3v4', 'M8.5 13.5 11 16l4.5-4.5'],
  },
  exams: {
    circles: [{ cx: 12, cy: 9.5, r: 5.5 }],
    paths: ['M8.5 14.2 7 21l5-2.4L17 21l-1.5-6.8'],
  },
  communication: {
    paths: [
      'M20.5 12.5c0 3.9-3.8 7-8.5 7-1 0-2-.15-2.9-.42L4 20.5l1.5-3.7C4.3 15.6 3.5 14.1 3.5 12.5c0-3.9 3.8-7 8.5-7s8.5 3.1 8.5 7z',
    ],
  },
  transport: {
    rects: [{ x: 3.5, y: 4.5, width: 17, height: 12, rx: 2 }],
    circles: [
      { cx: 7.5, cy: 19, r: 1.6 },
      { cx: 16.5, cy: 19, r: 1.6 },
    ],
    paths: ['M3.5 11h17'],
  },
  reports: {
    paths: ['M4 20V9', 'M10 20V4', 'M16 20v-7', 'M3 20h18'],
  },
  settings: {
    circles: [{ cx: 12, cy: 12, r: 3 }],
    paths: [
      'M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7.9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H2a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 3.7 7.9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H8a1.6 1.6 0 0 0 1-1.5V2a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V8a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z',
    ],
  },
  school: {
    paths: ['M12 3.5 21 8l-9 4.5L3 8z', 'M6.5 10v6.5c0 1.7 2.5 3 5.5 3s5.5-1.3 5.5-3V10'],
  },
  more: {
    circles: [
      { cx: 5, cy: 12, r: 1.6 },
      { cx: 12, cy: 12, r: 1.6 },
      { cx: 19, cy: 12, r: 1.6 },
    ],
  },
  close: {
    paths: ['M6 6l12 12M18 6L6 18'],
  },
  /**
   * The magnifier inside a search box, taken from the search fields in the approved designs
   * (`docs/artifacts/Students1280.dc.html`). Decoration beside a real, labelled control: the box
   * carries the accessible name, never this.
   */
  search: {
    circles: [{ cx: 11, cy: 11, r: 6.5 }],
    paths: ['M16 16l4.5 4.5'],
  },
  /**
   * Oversight: the audit log. A shield for "this is the record that protects the school", with the
   * tick that says the record was kept — drawn here rather than pulled from an icon library, which
   * ADR-0009 does not allow. The name matches the hint the backend sends for the item, though the
   * registry is still what decides it (ADR-0008).
   */
  'shield-check': {
    paths: [
      'M12 2.9 4.6 6.1v5.7c0 4.3 3 7.6 7.4 9.3 4.4-1.7 7.4-5 7.4-9.3V6.1z',
      'M8.9 12.1l2.3 2.3 4.2-4.4',
    ],
  },
  /**
   * Academics: the school's own academic model — its years, and its ladder of classes and
   * sections. An open book, drawn as a spine with one leaf either side of it.
   *
   * Hand-drawn on the same 24×24 grid as the rest rather than taken from an icon set, which
   * ADR-0009 does not allow. The two leaves mirror about the spine, so the glyph stays symmetrical
   * at 20px, which is the size navigation actually renders it at — a book with a thicker page on
   * one side reads as a smudge there.
   *
   * It deliberately does not reuse `school` (a mortarboard, which is the institution) or `exams`
   * (a medal, which is a result). This is the shape of what is taught, not of the school and not
   * of a grade.
   */
  academics: {
    paths: [
      'M12 7.4v12.1',
      'M12 7.4C10.3 6 8.2 5.3 5.6 5.3H3.6v12.1h2c2.6 0 4.7.7 6.4 2.1',
      'M12 7.4c1.7-1.4 3.8-2.1 6.4-2.1h2v12.1h-2c-2.6 0-4.7.7-6.4 2.1',
    ],
  },
  /**
   * The stand-in for an item whose registry entry names an icon that does not exist. It renders
   * rather than throwing, because a menu that disappears over a typo in an icon name is a worse
   * failure than one item drawn as a plain square.
   */
  placeholder: {
    rects: [{ x: 4, y: 4, width: 16, height: 16, rx: 3 }],
  },
} as const satisfies Record<string, IconGlyph>;

export type IconName = keyof typeof GLYPHS;

export const ICON_GLYPHS: Readonly<Record<IconName, IconGlyph>> = GLYPHS;
