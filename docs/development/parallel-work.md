# Running several agents at once

Written after a session that ran four agents in four git worktrees at the same time. The features
were independent; the merges were not. This page is about the second half — what actually collides,
so a work item can be handed out with the collision named in the brief.

It is not a plan and it does not restate
[the roadmap](../requirements/06-roadmap-and-mvp.md). For what is next, read
[status.md](../status.md); for how to hand one of them out, read
[assigning work](assigning-work.md).

## The boundary that works is module ownership

[module-map.md](../architecture/module-map.md) gives one module ownership of a table, and everything
else reaches it through that module's `api`. That is also the parallelism boundary. Two agents in
different backend modules touch disjoint Java packages, disjoint migrations and disjoint
`features/` folders, and the platform's SPI design keeps it that way on purpose: a module registers
its permissions and its menu by adding **its own** `*Permissions.java` and `*Navigation.java` bean,
which `PermissionCatalog` and `NavigationCatalog` collect at startup. Nobody edits a central list.
The same is true of error codes — each module owns its own `ErrorCode` enum — and of the frontend
API services, one `core/api/<module>-api.ts` per module.

So the rule is one module per agent, and the rest of this page is the exceptions.

## What serialises regardless of module

Found by inspection and by counting how often each file appears in the last forty commits.

| File | What conflicts | How to sequence |
|---|---|---|
| `contracts/openapi.json`, `contracts/api-types.ts` | Both are generated wholesale and sorted, so any two endpoint changes rewrite overlapping regions. CI fails on a `contracts/` diff, in both workflows. | **Never hand-merge.** Take either side, push, and let [`contracts.yml`](../../.github/workflows/contracts.yml) regenerate both files on the branch. That is what removed the serialisation this row used to describe: regeneration needed `./mvnw verify`, which cannot run twice at once on this machine (below), so the second agent to merge had to wait for the first. It now runs in Actions, per branch, concurrently. |
| `frontend/src/app/core/api/models.ts` | The hand-written aliases onto `contracts/api-types.ts`, 515 lines, grouped by feature banner. Changed in 12 of the last 40 commits — the most contended source file in the repo. | Add your block under your own banner comment, not at the end of the file. Two agents that both append before the last line conflict on the same three lines. |
| `frontend/src/app/app.routes.ts` | New feature routes all append inside the same `children: [...]`. **Order is load-bearing** — `students/guardians` and `students/import` must stay ahead of `students/:id`, and Angular matches in declaration order. | A textual merge here can silently reorder routes. Whoever merges second re-reads the array rather than trusting the merge. |
| `frontend/src/app/core/navigation/nav-routes.ts` | One `Map` literal, id → path and icon. | Additive; insert in your module's group. |
| `frontend/src/app/core/navigation/nav-labels.ts` | One `NAV_LABELS` object. Every new screen adds a key. | Additive, same group rule. An id with no label still renders — `navLabel` guesses from the key — so a lost entry is a cosmetic bug, not a crash, and will not be caught by a test. |
| `frontend/src/app/shared/components/icon/icon-glyphs.ts` | One `GLYPHS` object of path geometry. A new top-level menu section needs a new glyph. | Additive. Two agents inventing a glyph for the same concept is more likely than a merge conflict. |
| `frontend/src/app/core/auth/permissions.ts` | One `as const` object; new codes go at the bottom, before the closing brace, which is exactly where the other agent puts theirs. | Additive but adjacent. Expect a conflict; keep both. |
| `backend/…/identity/domain/RoleTemplates.java` | **The worst one.** A new permission that a shipped role should hold is edited *into* one of twelve existing `new RoleTemplate(...)` argument lists. Two agents granting to `PRINCIPAL` conflict inside one call. | Do this in a separate, tiny commit at the end of the work item, so the conflict is a three-line resolve rather than a rebase across a feature. `RoleTemplateInstaller` refuses to start if a template names a permission the catalogue does not have, so a bad merge fails loudly. |
| `frontend/src/styles/_tokens.scss` | A new token, plus its dark value. | Additive; conflicts are trivial. |
| `docs/status.md` | Changed in **25 of the last 40 commits** — the single most contended file in the repository. | See below. |
| `docs/architecture/module-map.md`, `docs/architecture/overview.md` | One row per module in a table, and a module inventory. | Additive rows. Low severity, but both agents will hit it, so neither should be surprised. |
| `docs/api/README.md` | The error-code list. | Additive. |

Two things the list does **not** contain, and both are worth knowing because they get assumed:

- **`SecurityConfig.java` is not a per-feature file.** Endpoint authorization is `@PreAuthorize` on
  the controller, checked by `ControllerAuthorizationTests`. `SecurityConfig` carries only URL-level
  `permitAll` exemptions, and a normal feature adds none. Do not brief an agent to edit it.
- **The permission and navigation registries are not shared files.** They are bean collections. This
  is the one place the architecture already solved the problem, and it is why the frontend's
  `permissions.ts` and `RoleTemplates.java` stand out as the two that did not get that treatment.

## Migrations

Migrations are `V<yyyy_MM_dd_HHmm>__<module>_<what>.sql` in `db/migration/shared/` (runs once
against `public`) and `db/migration/tenant/` (runs against every school's schema). Spring's own
Flyway is disabled; `TenantMigrations` fans out at startup with Flyway's defaults, which means
`validateOnMigrate=true` and `outOfOrder=false`.

The filename collision is the small risk — minute-granularity timestamps rarely tie. Two larger ones
do not depend on the filename at all:

1. **Out of order.** Agent A writes `…_0900`, agent B writes `…_1000`, B merges first. Every
   database that has already applied `1000` then refuses `0900`, because out-of-order is off. It
   passes CI, which starts from an empty Testcontainer, and fails on the shared dev database and on
   Render. **Name your migration with the timestamp of the moment you merge, not the moment you
   started.** If your branch has been open a while, rename the file before you merge — it is not
   merged yet, so the immutability rule does not apply.
2. **The dev database is shared and persistent.** Every agent's `local` profile points at the same
   Supabase project, so the first agent to run the backend applies their migration to the real
   `demo_school` schema and records its checksum. If they then edit that file — which is normal
   while the work is in progress — every other agent's backend refuses to start on a checksum
   mismatch, and the fix is manual. While several agents are running, only one of them should be
   pointing a backend at the shared database with an unmerged migration in its tree.

The checksum is validated against every school's schema, and there are now two (`demo_school` and
`qa_sandbox`), so this gets worse rather than better.

## Two facts about this machine

Both learned the hard way, both change how work is assigned.

**The full test suite does not run here, so CI is the signal.** Concurrent builds get OOM-killed,
and a ten-file `ng test` run produced 47 spurious 5000 ms timeouts in files that pass individually.
Push the branch and read the Actions run. That used to have a knock-on, because `./mvnw verify` is
what regenerates `contracts/openapi.json` — so contract regeneration was a one-agent-at-a-time
operation, whoever else was running. [`contracts.yml`](../../.github/workflows/contracts.yml) now
does it per branch in Actions, which is what makes several endpoint-changing lanes concurrent
rather than merely independent.

**A fresh worktree has no `node_modules`.** A frontend agent runs `npm ci` before anything else, and
must be told to, because `ng` will not be on the path and the failure looks like a broken checkout.

## What can run in parallel right now

Ground truth as of today, not the roadmap. Every item this section used to list has been built —
subjects, roles and user management with their screens, encryption at rest, session re-validation,
the accessor logging rule, guardian import, the student record's remaining sections, dashboards and
the audit retention purge all merged, along with a deployment bootstrap ([ADR-0024](../architecture/adr/0024-bootstrap-deployment.md))
that was not on any list until a real deployment proved onboarding did not work.

| Item | Modules | Contends on | Must not touch | Blocked by |
|---|---|---|---|---|
| Export | `platform`, `student`'s api records | `contracts/*`, `models.ts`, `permissions.ts`, `RoleTemplates`, a frontend action row | `platform/storage`, `document` | nothing — the ADR-0014 masking it needed is built |
| Documents: the S3 adapter and the screen | `document`, `platform/storage`, one frontend section | `contracts/*`, `models.ts`, `backend/pom.xml` | `platform/classification` | nothing — Supabase Storage and the SDK are approved |
| The users screen's missing navigation id | `identity` | nothing generated | — | nothing; a one-line change |
| The role-edit impact preview | frontend `features/access` | `models.ts` | — | nothing |
| Design drift: nav tint, page gutter, a shared card and badge | frontend only, ~19 SCSS files | `_tokens.scss` | — | nothing |
| Coolify on the Mumbai VPS | `ops/` | nothing generated | — | nothing |

**The two running lanes are export and documents, and they were chosen to be disjoint**: export is
`platform` plus `student`'s api records, documents is `document` plus `platform/storage`. They meet
only in `contracts/`, `models.ts` and `docs/status.md`, all three of which have a known resolution —
regenerate, add under your own banner, keep both rows.

### What the last two waves actually cost

Written down because the estimate and the reality differed in one direction only.

Eleven lanes ran across two waves. **Not one produced a semantic merge conflict.** Every conflict was
`docs/status.md`, `contracts/`, or a test asserting a shipped role template's exact permission list —
`AccessControlTests` and `MeApiTests` fail whenever any lane adds a permission, which happened four
times. That test is doing its job; it is just also the most reliable predictor of which lane merges
second.

What did cost real time was none of the above:

- **A pull request that conflicts with its base runs no checks at all**, silently. Three lanes waited
  on CI that GitHub was never going to dispatch.
- **Migration timestamps.** Two lanes wrote a migration whose timestamp was older than one already
  applied on a live database. Both would have passed CI, which starts from an empty container, and
  then refused to start on staging and on the shared dev database. Renaming at merge is not
  bookkeeping; it is the only thing standing between a green build and a broken environment.
- **A six-second "failure" is usually not one.** `cancel-in-progress` supersedes a run and reports it
  as failed. Read the annotation before believing the label.

## What must run alone

**Encryption at rest (ADR-0022).** It adds `@Encrypted` to entity fields, a converter, a
build-failing test binding storage to `@Classification`, and then the Restricted columns
[ADR-0020](../architecture/adr/0020-student-and-guardian-model.md) §2 deliberately left out — which
means a tenant migration plus edits to the student and guardian entities and their DTOs. Any other
agent editing those entities at the same time conflicts on the same fields, and the binding test
means a half-merged state fails the build for everyone rather than only for the branch that caused
it.

Run it with no other backend work open. It is also the top item in [status.md](../status.md), so
this is the ordering, not a compromise.

## status.md when several agents are running

The convention is that docs change in the same pull request as the code, and `status.md` is in a
quarter of all commits, so it is the file most likely to conflict and the one least worth losing.

- **Add, do not restructure.** New entries go at the bottom of the **Done** table. A new gap goes at
  the bottom of its list.
- **Edit only the bullet your work is about.** Rewording a neighbouring line to read better turns an
  additive merge into a manual one.
- **The numbered "What to do next" list is the exception**, because closing item 2 renumbers items 3
  to 5. Whoever merges second renumbers; nobody should be editing those numbers on a branch that has
  been open for days.
- A `status.md` conflict is always a content conflict and never a semantic one. Keep both sides.
