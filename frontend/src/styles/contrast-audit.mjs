/**
 * Verifies every colour pair in _tokens.scss against WCAG 2.1.
 *   node src/styles/contrast-audit.mjs
 * Exits non-zero on any failure, so it can become a CI step once the palette settles.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const src = readFileSync(join(here, '_tokens.scss'), 'utf8');

const darkStart = src.indexOf('@mixin cb-dark-tokens');
const parse = (text) =>
  Object.fromEntries(
    [...text.matchAll(/(--cb-[a-z-]+):\s*(#[0-9a-fA-F]{6})/g)].map((m) => [m[1], m[2]]),
  );

const light = parse(src.slice(0, darkStart));
const dark = { ...light, ...parse(src.slice(darkStart)) };

const channel = (c) => (c / 255 <= 0.03928 ? c / 255 / 12.92 : ((c / 255 + 0.055) / 1.055) ** 2.4);
const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
};
const ratio = (a, b) => {
  const [x, y] = [luminance(a), luminance(b)].sort((m, n) => n - m);
  return (x + 0.05) / (y + 0.05);
};

// 4.5 for text, 3.0 for non-text UI (WCAG 1.4.3 and 1.4.11).
const pairs = [
  ['text', 'bg', 4.5],
  ['text', 'surface', 4.5],
  ['text', 'surface-raised', 4.5],
  ['text-muted', 'bg', 4.5],
  ['text-muted', 'surface', 4.5],
  ['primary', 'bg', 4.5],
  ['primary', 'surface', 4.5],
  ['primary', 'primary-surface', 4.5],
  ['primary-contrast', 'primary', 4.5],
  ['accent', 'bg', 4.5],
  ['danger', 'bg', 4.5],
  ['danger', 'danger-surface', 4.5],
  ['warning', 'bg', 4.5],
  ['warning', 'warning-surface', 4.5],
  ['success', 'bg', 4.5],
  ['success', 'success-surface', 4.5],
  ['info', 'bg', 4.5],
  ['info', 'info-surface', 4.5],
  ['border-strong', 'bg', 3.0],
  ['border-strong', 'surface', 3.0],
  ['focus', 'bg', 3.0],
  ['focus', 'surface', 3.0],
];

let failures = 0;
for (const [name, palette] of [
  ['light', light],
  ['dark', dark],
]) {
  console.log(`\n${name}`);
  for (const [fg, bg, need] of pairs) {
    const a = palette[`--cb-${fg}`];
    const b = palette[`--cb-${bg}`];
    if (!a || !b) {
      console.log(`  MISSING TOKEN ${fg} or ${bg}`);
      failures++;
      continue;
    }
    const r = ratio(a, b);
    const ok = r >= need;
    if (!ok) failures++;
    console.log(`  ${ok ? 'pass' : 'FAIL'}  ${fg} on ${bg}  ${r.toFixed(2)} (needs ${need})`);
  }
}

console.log(`\n${failures} failure(s)`);
process.exit(failures === 0 ? 0 : 1);
