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
 * A STALE timestamp was originally dismissed here as unknowable — "whether 0900 is too old depends on
 * what some database has already applied". That was wrong, and it cost three failed deploys the same
 * night this check was written. It is knowable, from a proxy that is good enough: **the highest
 * migration already on the base branch.** Anything merged to `main` will be applied to every database
 * that deploys from it, so a pull request adding a migration BELOW main's highest is out of order for
 * all of them the moment it lands.
 *
 * That is exactly what happened: the fee lane's migrations were renamed to 0109 — correct at the
 * moment of renaming — and then spent eighty minutes in CI while the admission lane merged 0128. By
 * the time fee merged, 0109 and 0110 sat below an applied 0128, and Flyway refused them on every
 * school: "Detected resolved migration not applied to database: 2026.09.09.0109."
 *
 * So the rule is not "name it when you write it" or even "name it when you open the pull request". It
 * is "name it when it MERGES", and the only way to enforce that is to check it at merge time against
 * what has already merged.
 *
 * A FUTURE timestamp breaks SOMEBODY ELSE'S. A migration dated eight hours ahead sorts above every
 * migration merged in those eight hours, so the next lane's correctly-named file lands below it and
 * Flyway refuses that one, on every database that took the future-dated one first. The lane that
 * caused it sees nothing wrong. That asymmetry is why this check exists and why it checks the one
 * direction it can actually know about.
 */
import { readdirSync, readFileSync } from 'node:fs';
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

/**
 * Migrations this branch adds that sort BELOW the highest one already on the base branch.
 *
 * `baseFiles` is the same directory as it stands on `main`. Anything already there will have been
 * applied by every deployment from `main`, so a new migration below the highest of them is refused
 * by Flyway with `outOfOrder=false` — on every database, immediately, and only after merging.
 */
export function findOutOfOrder(branchFiles, baseFiles) {
  const stamp = (f) => timestampOf(f)?.getTime();
  const baseTimes = baseFiles.map(stamp).filter(Boolean);
  if (baseTimes.length === 0) return [];
  const highest = Math.max(...baseTimes);
  const added = branchFiles.filter((f) => !baseFiles.includes(f));
  return added
    .map((f) => ({ file: f, at: timestampOf(f) }))
    .filter((x) => x.at && x.at.getTime() < highest)
    .map((x) => ({ ...x, highest: new Date(highest) }));
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

  // The workflow writes the base branch's migration filenames here, one per line. Absent on a
  // workflow_dispatch run, in which case only the future-dating half runs.
  let baseFiles = [];
  const basePath = process.env.MIGRATION_BASE_LIST;
  if (basePath) {
    try {
      baseFiles = readFileSync(basePath, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean);
    } catch {
      console.error(`Cannot read the base migration list at ${basePath}.`);
      process.exit(1);
    }
  }

  const names = files.map((f) => f.file);
  const stale = findOutOfOrder(names, baseFiles);
  if (stale.length > 0) {
    console.error('These migrations sort BELOW one already merged, so Flyway will refuse them:\n');
    for (const { file, at, highest } of stale) {
      console.error(`  ${file}`);
      console.error(`      names ${at.toISOString().slice(0, 16).replace('T', ' ')} UTC, but the base branch already has`);
      console.error(`      ${highest.toISOString().slice(0, 16).replace('T', ' ')} UTC applied on every deployment\n`);
    }
    console.error('Rename it above that. outOfOrder is off (ADR-0011), so a migration below one already');
    console.error('applied is refused on every database — after merging, never before. This is the half of');
    console.error('the rule that "name it when it merges" exists for: a name that was right when you wrote');
    console.error('it stops being right the moment another lane merges ahead of you.');
    process.exit(1);
  }

  const future = findFutureDated(names, cutoff);
  if (future.length === 0) {
    console.log(`${files.length} migrations checked; none dated in the future, none below the base branch.`);
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
