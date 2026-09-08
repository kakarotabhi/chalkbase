#!/usr/bin/env node
// The CI guard ADR-0008 asks for: "the backend must not emit an id the frontend cannot resolve."
//
// This is deliberately not a build. It reads three already-committed text files — no `mvnw`, no
// `npm ci`, nothing compiled — which is why it lives in `tools/` as a plain script rather than as
// a frontend spec or a backend test: it needs both artefacts, and belongs in neither module.
//
//   backend ids  <- contracts/navigation-ids.json      (written by NavigationContractExportTests)
//   frontend ids <- frontend/src/app/core/navigation/nav-routes.ts  (the APP_NAV_ROUTES map)
//   exceptions   <- tools/navigation-contract/allowlist.json
//
// ## The three outcomes, and why each is what it is
//
// A backend id absent from the frontend is ADR-0008's *designed* behaviour when a screen has not
// shipped yet: `NavigationStore` drops the id and logs it, on purpose. So this direction cannot be
// a blanket error — but it cannot be silently fine either, because the exact same shape is what a
// typo produces: `settings.acess` is "a backend id absent from the frontend" in precisely the way
// `students.documents` (no screen yet) is. The check cannot tell those apart by looking at the id
// alone, so it does not try to: every backend-only id is an ERROR *unless* a human already said,
// by name, why it is expected to be missing. That is `allowlist.json`, and the reason is required
// — an allowlist entry with no reason is a check that has been switched off, not narrowed.
//
// A frontend id absent from the backend is the opposite shape: `students.import` (ADR-0021) and
// `schools` (kept for an operator who types the URL) are both routes this app owns ahead of, or
// past, what the backend currently emits. `NavigationStore` never looks the id up unless the
// server sends it, so a stale entry here is dead weight, not a broken menu — a WARNING, not a
// failure. It is still printed, because dead weight that nobody sees accumulates.
//
// An allow-listed id that the frontend has since caught up on is a THIRD failure mode: an
// allowlist entry that no longer does anything is worse than no comment, because it reads as
// documentation of a gap that has already been closed. Removing it is part of shipping the screen,
// so a stale entry is also an ERROR — the allowlist stays honest by construction rather than by
// someone remembering to prune it.
//
// ## Proof this catches a typo, not just a real gap
//
// `check.test.mjs` runs `settings.acess` (missing an "s") through `checkNavigationContract` and
// asserts it is reported as an error precisely because it is not on the allowlist — the same test
// asserts the genuine gap (`students.documents`, which *is* allow-listed) produces no error. That
// is the difference the design rests on, exercised as a fixture rather than only argued in prose.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

/**
 * Below this, treat the result as "the extraction broke" rather than "the app got smaller".
 * Chalkbase declares 17 ids as of this writing (dashboard, students×4, academics×4, attendance×3,
 * settings×4, audit) and the frontend registry maps or deliberately tracks a similar count. Ten is
 * comfortably below either today, and stays a meaningful floor as both grow — a script that read
 * zero ids because a marker string changed must never be allowed to read as "no ids, no problems".
 */
export const MINIMUM_EXPECTED_IDS = 10;

/**
 * Pure comparison: given the three inputs already parsed, decide what is an error and what is a
 * warning. Kept free of file I/O so `check.test.mjs` can feed it fixtures directly.
 *
 * @param {{backendIds: string[], frontendIds: string[], allowlist: {id: string, reason: string}[], minimumIds?: number}} input
 *   `minimumIds` defaults to {@link MINIMUM_EXPECTED_IDS} and exists as a parameter only so a test
 *   can exercise the comparison logic against small, readable fixtures without also having to
 *   satisfy the real-world floor — the floor itself is proved separately, below.
 * @returns {{errors: string[], warnings: string[]}}
 */
export function checkNavigationContract({ backendIds, frontendIds, allowlist, minimumIds = MINIMUM_EXPECTED_IDS }) {
  const errors = [];
  const warnings = [];

  if (backendIds.length < minimumIds) {
    errors.push(
      `Only ${backendIds.length} backend id(s) were read (expected at least ${minimumIds}). ` +
        'Refusing to treat that as a clean pass — the extraction likely broke rather than the ' +
        'catalogue shrinking that much.',
    );
  }
  if (frontendIds.length < minimumIds) {
    errors.push(
      `Only ${frontendIds.length} frontend id(s) were read (expected at least ${minimumIds}). ` +
        'Refusing to treat that as a clean pass — the extraction likely broke rather than the ' +
        'registry shrinking that much.',
    );
  }

  // A malformed allowlist entry is refused outright: it is either mis-typed (so it protects
  // nothing), duplicated (so which reason applies is ambiguous) or has no reason (so it is the
  // check switched off wearing a disguise). None of those three earns the entry a place in
  // `allowlistIds` below — an invalid entry must not *also* silently suppress the gap it names,
  // or a reason-less line would do exactly what requiring a reason exists to prevent.
  const seenAllowlistIds = new Set();
  const allowlistIds = new Set();
  for (const entry of allowlist) {
    const id = entry && entry.id;
    const reason = entry && entry.reason;
    if (typeof id !== 'string' || id.trim() === '') {
      errors.push(`allowlist.json has an entry with no usable "id": ${JSON.stringify(entry)}`);
      continue;
    }

    let valid = true;
    if (typeof reason !== 'string' || reason.trim() === '') {
      errors.push(
        `allowlist.json entry "${id}" has no "reason". A reason is required, not optional — an ` +
          'entry with none is the check switched off for that id rather than narrowed.',
      );
      valid = false;
    }
    if (seenAllowlistIds.has(id)) {
      errors.push(`allowlist.json lists "${id}" more than once.`);
      valid = false;
    }
    seenAllowlistIds.add(id);
    if (valid) {
      allowlistIds.add(id);
    }
  }

  const backendSet = new Set(backendIds);
  const frontendSet = new Set(frontendIds);

  const backendOnly = [...backendSet].filter((id) => !frontendSet.has(id)).sort();
  for (const id of backendOnly) {
    if (allowlistIds.has(id)) {
      continue;
    }
    errors.push(
      `Backend declares "${id}" but frontend/src/app/core/navigation/nav-routes.ts does not map ` +
        'it, and it is not on the allowlist. Either add the route (and label) the screen needs, ' +
        'or — if the screen genuinely has not shipped yet — add an allowlist entry with a reason.',
    );
  }

  // An allowlist entry that is no longer backend-only is stale: either the frontend caught up (the
  // entry should be deleted as part of that change) or the backend stopped emitting the id (same
  // remedy). Either way, an entry doing nothing is not "quiet" — it is misleading documentation.
  const backendOnlySet = new Set(backendOnly);
  for (const id of allowlistIds) {
    if (!backendOnlySet.has(id)) {
      errors.push(
        `allowlist.json lists "${id}", but it is not currently a backend id missing from the ` +
          'frontend (the frontend maps it now, or the backend no longer declares it). Remove the ' +
          'entry — an allowlist that no longer matches anything documents a gap that already closed.',
      );
    }
  }

  const frontendOnly = [...frontendSet].filter((id) => !backendSet.has(id)).sort();
  for (const id of frontendOnly) {
    warnings.push(
      `Frontend registers "${id}" but no backend NavigationProvider currently emits it. Harmless — ` +
        "NavigationStore only ever looks an id up when the server sends it — but worth a comment " +
        'in nav-routes.ts (as students.import and schools already carry) if there is not one, so a ' +
        'reader knows this is deliberate rather than left over.',
    );
  }

  return { errors, warnings };
}

/**
 * Pulls every id out of `APP_NAV_ROUTES` in `nav-routes.ts` without compiling or type-checking the
 * file — this script has no TypeScript dependency and does not want one for a two-column map.
 *
 * Line comments are stripped first specifically so a comment that happens to *mention* an id in an
 * example (`students.import`) is not double counted or, worse, mistaken for a live entry once the
 * real line is edited. Extraction is then scoped to the text between the map's own opening and
 * closing markers, so prose above or below the map cannot leak an unrelated bracketed string in.
 *
 * Throws rather than returning an empty or partial list when the map's shape has moved — a parser
 * that degrades quietly is exactly the failure mode the brief for this check warned against.
 */
export function extractFrontendIds(source) {
  const withoutLineComments = source
    .split('\n')
    .map((line) => {
      const index = line.indexOf('//');
      return index === -1 ? line : line.slice(0, index);
    })
    .join('\n');

  const startMarker = 'new Map<string, NavRoute>([';
  const start = withoutLineComments.indexOf(startMarker);
  if (start === -1) {
    throw new Error(
      `could not find "${startMarker}" in nav-routes.ts — has APP_NAV_ROUTES changed shape?`,
    );
  }
  const bodyStart = start + startMarker.length;
  const bodyEnd = withoutLineComments.indexOf(']);', bodyStart);
  if (bodyEnd === -1) {
    throw new Error('found the start of APP_NAV_ROUTES but not its closing "]);" — has it changed shape?');
  }
  const body = withoutLineComments.slice(bodyStart, bodyEnd);

  const ids = [];
  const entryPattern = /\[\s*'([a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)*)'\s*,/g;
  let match;
  while ((match = entryPattern.exec(body)) !== null) {
    ids.push(match[1]);
  }
  if (ids.length === 0) {
    throw new Error(
      'found APP_NAV_ROUTES but extracted zero ids from it — the entry pattern no longer matches, ' +
        'not that the registry is empty',
    );
  }
  return ids;
}

/** Parses `contracts/navigation-ids.json`: a flat JSON array of strings, nothing more. */
export function parseBackendIds(jsonText) {
  const parsed = JSON.parse(jsonText);
  if (!Array.isArray(parsed) || !parsed.every((id) => typeof id === 'string')) {
    throw new Error('contracts/navigation-ids.json is not a flat JSON array of strings');
  }
  return parsed;
}

function main() {
  const [, , backendIdsPath, navRoutesPath, allowlistPath] = process.argv;
  if (!backendIdsPath || !navRoutesPath || !allowlistPath) {
    console.error(
      'usage: node check.mjs <navigation-ids.json> <nav-routes.ts> <allowlist.json>',
    );
    process.exit(2);
  }

  const backendIds = parseBackendIds(readFileSync(backendIdsPath, 'utf8'));
  const frontendIds = extractFrontendIds(readFileSync(navRoutesPath, 'utf8'));
  const allowlist = JSON.parse(readFileSync(allowlistPath, 'utf8'));

  const { errors, warnings } = checkNavigationContract({ backendIds, frontendIds, allowlist });

  console.log(`Backend ids:  ${backendIds.length}`);
  console.log(`Frontend ids: ${frontendIds.length}`);
  console.log(`Allowlisted:  ${allowlist.length}`);
  console.log('');

  for (const warning of warnings) {
    console.log(`WARNING: ${warning}`);
  }
  for (const error of errors) {
    console.error(`ERROR: ${error}`);
  }

  if (errors.length > 0) {
    console.error(`\n${errors.length} error(s), ${warnings.length} warning(s). Failing.`);
    process.exit(1);
  }
  console.log(`\nNo errors. ${warnings.length} warning(s).`);
}

// Only run as a CLI, so `check.test.mjs` can import the functions above without this executing.
if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  main();
}
