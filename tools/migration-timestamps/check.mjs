#!/usr/bin/env node
/**
 * Refuses a migration whose timestamp is in the future.
 *
 * WHY THIS EXISTS, AND WHY IT CHECKS ONLY ONE DIRECTION.
 *
 * Migrations are `V<yyyy_MM_dd_HHmm>__<module>_<what>.sql` and Flyway runs them in version order
 * with `outOfOrder=false`, so a version lower than one a database has already applied is refused.
 * assigning-work.md therefore says to name a migration with the timestamp of the moment it MERGES.
 *
 * That rule was documented, restated in every lane brief, and broken four times in one day — because
 * the natural instinct is to timestamp the moment you write the file, and nothing contradicts you.
 * The failure is invisible locally and invisible in CI, because CI starts from an empty container
 * where any order works. It only appears on a database that has real history.
 *
 * A STALE timestamp breaks its own migration, loudly, on the next deploy — bad, but self-announcing,
 * and this check cannot see it anyway: whether `0900` is too old depends on what some database has
 * already applied, which is not knowable from the repository.
 *
 * A FUTURE timestamp breaks SOMEBODY ELSE'S. A migration dated eight hours ahead sorts above every
 * migration merged in those eight hours, so the next lane's correctly-named file lands below it and
 * Flyway refuses that one, on every database that took the future-dated one first. The lane that
 * caused it sees nothing wrong. That asymmetry is why this check exists and why it checks the one
 * direction it can actually know about.
 */
import { readdirSync } from 'node:fs';
import { join } from 'node:path';

const DIRS = [
  'backend/src/main/resources/db/migration/shared',
  'backend/src/main/resources/db/migration/tenant',
];

/** `V2026_09_09_0110__fee_create_fee_structure.sql` → a Date, or null if the name is not a migration. */
export function timestampOf(filename) {
  const m = /^V(\d{4})_(\d{2})_(\d{2})_(\d{2})(\d{2})__/.exec(filename);
  if (!m) return null;
  const [, y, mo, d, h, mi] = m.map(Number);
  return new Date(Date.UTC(y, mo - 1, d, h, mi));
}

export function findFutureDated(files, now) {
  return files
    .map((f) => ({ file: f, at: timestampOf(f) }))
    .filter((x) => x.at && x.at.getTime() > now.getTime());
}

function main() {
  const now = new Date();
  // A little slack, because a migration named in the same minute it merges is correct and clocks
  // are not identical. Anything beyond this is somebody dating a file ahead of themselves.
  const slackMs = 15 * 60 * 1000;
  const cutoff = new Date(now.getTime() + slackMs);

  const files = [];
  for (const dir of DIRS) {
    let entries;
    try {
      entries = readdirSync(dir);
    } catch {
      console.error(`Cannot read ${dir} — has the migration layout moved?`);
      process.exit(1);
    }
    for (const e of entries) if (e.endsWith('.sql')) files.push({ dir, file: e });
  }

  // A check that reads nothing must not pass. If the naming convention or the layout changes, this
  // fails loudly rather than quietly approving everything.
  if (files.length < 10) {
    console.error(`Only found ${files.length} migrations. Expected at least 10 — the layout or the`);
    console.error('naming convention has probably moved, and this check is no longer looking at them.');
    process.exit(1);
  }

  const future = findFutureDated(files.map((f) => f.file), cutoff);
  if (future.length === 0) {
    console.log(`${files.length} migrations checked; none dated in the future.`);
    return;
  }

  console.error('These migrations are dated in the future:\n');
  for (const { file, at } of future) {
    console.error(`  ${file}`);
    console.error(`      names ${at.toISOString().slice(0, 16).replace('T', ' ')} UTC, it is now ${now.toISOString().slice(0, 16).replace('T', ' ')} UTC\n`);
  }
  console.error('Rename it to the moment it merges. A future timestamp does not break your migration —');
  console.error('it breaks the NEXT one, because that lane\'s real timestamp sorts below yours and Flyway');
  console.error('refuses it on every database that applied yours first. outOfOrder is off, deliberately.');
  process.exit(1);
}

if (import.meta.url === `file://${process.argv[1]}`) main();
