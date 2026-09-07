# Running several agents at once

Written after a session that ran four agents in four git worktrees at the same time. The features
were independent; the merges were not. This page is about the second half — what actually collides,
so a work item can be handed out with the collision named in the brief.

It is not a plan and it does not restate
[the roadmap](../requirements/06-roadmap-and-mvp.md). For what is next, read
[status.md](../status.md).

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
| `contracts/openapi.json`, `contracts/api-types.ts` | Both are generated wholesale and sorted, so any two endpoint changes rewrite overlapping regions. CI fails on a `contracts/` diff, in both workflows. | **Never hand-merge.** Take either side, then `cd backend && ./mvnw verify` and `cd frontend && npm run contracts:types`. Because that first command cannot run twice at once on this machine (below), regeneration is the serialised step — the second agent to merge regenerates. |
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
Push the branch and read the Actions run. The knock-on is the contracts row above: `./mvnw verify`
is what regenerates `contracts/openapi.json`, so on this machine contract regeneration is a
one-agent-at-a-time operation, whoever else is running.

**A fresh worktree has no `node_modules`.** A frontend agent runs `npm ci` before anything else, and
must be told to, because `ng` will not be on the path and the failure looks like a broken checkout.

## What can run in parallel right now

Ground truth as of today, not the roadmap. Phase 1 still has **subjects, documents, export and
dashboards** unbuilt. Roles and user management are backend read-only — `/api/access` is three
`@GetMapping`s and nothing else, with no write endpoints and no screen; `nav-routes.ts`
deliberately does not map `settings.access` for that reason.

| Item | Modules | Contends on | Must not touch | Blocked by |
|---|---|---|---|---|
| ADR-0008 staleness rule: a `403` refetches `/api/me` | frontend only | nothing generated | `core/auth/session-store.ts` if another agent is in identity | nothing |
| Static rule for a `CONFIDENTIAL` accessor in a logger argument | backend build only | nothing | no production code | nothing |
| Session re-validation: disabled and locked accounts | `identity` | `contracts/*` and `models.ts` if it adds an endpoint | `RoleTemplates`, `AccessController` | nothing |
| Subjects | `academics`, frontend `features/academics` | every row in the shared-files table | `student` | nothing |
| Roles and user management: write endpoints plus a screen | `identity`, new frontend feature | `contracts/*`, `models.ts`, `permissions.ts`, `RoleTemplates`, `app.routes.ts`, `nav-*` | — | must not overlap session re-validation |
| Encryption at rest (ADR-0022) | `platform/classification`, `student`, `school` | a tenant migration, `contracts/*` | — | **runs alone** |
| Export | `platform/classification` plus every module that exports | `contracts/*` | — | ADR-0014 masking, which encryption at rest builds |
| Documents | new module | the shared-files table | — | no file-storage port exists (ADR-0013) |
| Dashboards | reads across every module's `api` | every module's `api` package | — | the modules it would summarise |

Three of these run together cleanly and are the set to hand out first: **the ADR-0008 staleness
rule, the accessor logging rule, and session re-validation.** The first is frontend-only and adds no
endpoint, so it never touches `contracts/`. The second adds a build-time check and no production
code. The third is confined to `identity` and to files the other two do not open. Nothing in that
trio regenerates the contract, which is what makes them genuinely concurrent rather than merely
independent.

**Subjects can run alongside them, as the only full-stack lane.** It is a new table, a new endpoint
set, a new screen and a new menu entry, so it touches every row of the shared-files table at once.
One such lane at a time is the limit — a second one doubles the merge cost on `models.ts`,
`app.routes.ts`, `nav-routes.ts`, `nav-labels.ts`, `permissions.ts` and `RoleTemplates.java`
simultaneously, and every one of those merges is manual.

**Roles and user management cannot run beside session re-validation.** Both live in
`identity/application` and both change how a grant is resolved; the second to merge is rewriting the
first's work rather than merging with it. Pick one, and it should be session re-validation, because
an admin screen that disables an account is worth very little while the disabled account's session
keeps working.

**Export and dashboards are not blocked by collisions but by their inputs.** Export needs the
classification masking that encryption at rest builds, and dashboards need modules that do not exist
to summarise. Documents needs a file-storage decision under ADR-0013, which is a question for the
product owner and not an implementation task.

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
