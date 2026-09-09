import { test } from 'node:test';
import assert from 'node:assert/strict';
import { timestampOf, findFutureDated } from './check.mjs';

const NOW = new Date(Date.UTC(2026, 8, 9, 1, 9)); // 2026-09-09 01:09 UTC

test('parses a migration filename into its timestamp', () => {
  const at = timestampOf('V2026_09_09_0110__fee_create_fee_structure.sql');
  assert.equal(at.toISOString(), '2026-09-09T01:10:00.000Z');
});

test('ignores anything that is not a migration filename', () => {
  assert.equal(timestampOf('README.md'), null);
  assert.equal(timestampOf('V1__no_timestamp.sql'), null);
});

test('catches the exact mistake this check exists for: eight hours ahead', () => {
  // The real one, from the fee lane: named 0900 while it was 0109.
  const found = findFutureDated(['V2026_09_09_0900__fee_create_fee_head.sql'], NOW);
  assert.equal(found.length, 1);
  assert.match(found[0].file, /fee_create_fee_head/);
});

test('accepts a migration named in the past', () => {
  assert.deepEqual(findFutureDated(['V2026_09_08_2056__attendance_create_attendance_mark.sql'], NOW), []);
});

test('accepts a migration named in the same minute it merges', () => {
  assert.deepEqual(findFutureDated(['V2026_09_09_0109__fee_create_fee_head.sql'], NOW), []);
});

test('reports every future-dated migration, not just the first', () => {
  const found = findFutureDated(
    [
      'V2026_09_09_0900__fee_create_fee_head.sql',
      'V2026_09_08_1754__document_create_document.sql',
      'V2026_09_09_0930__fee_create_fee_structure.sql',
    ],
    NOW,
  );
  assert.equal(found.length, 2, 'both future-dated files should be named, so one fix does not hide another');
});
