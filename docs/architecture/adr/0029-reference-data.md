# ADR-0029: Reference data — states are seeded public data, boards stay an enum, the audit action filter stays hardcoded

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0006](0006-configurability-model.md) (configurability tiers), [ADR-0011](0011-schema-per-tenant.md) (tenancy), [ADR-0014](0014-data-classification.md) (classification), [ADR-0018](0018-audit-log.md) (audit log)

## Context

Two `TODO(reference-data)` markers named the same gap: `frontend/src/app/features/schools/school-profile.ts`
carried a hardcoded list of Indian states and boards, and `frontend/src/app/features/audit/audit-actions.ts`
carried a hardcoded list of audit actions "exactly like the board and state lists on the school
profile". `module-map.md` already said global reference data belongs in `public`, seeded by
`db/migration/shared` — the code had not caught up.

Looked at together, the three lists turn out not to be one problem. Each earns a different answer.

## Options considered, per list

### States

1. **Move to a `public.state` table, read through an endpoint.** Matches `module-map.md`'s own
   description and ADR-0006 Tier 1 ("rows a school edits, with no branching in code" — here, rows
   nobody edits, but still just data). Removes the duplicate spelling risk the TODO named.
2. **Leave it a frontend constant.** Cheapest, but the list is wrong forever unless a frontend
   deploy fixes it, and it is the exact duplication ADR-0006 exists to avoid paying for twice.

### Boards

1. **Move `Board` to a `public.board` table too**, matching ADR-0006's own example list, which
   names boards as Tier 1 alongside states.
2. **Leave `Board` a Java enum**, and only stop the frontend keeping its own copy of the label
   text.

### The audit action filter

1. **`select distinct action from audit_event`, exposed as an endpoint**, replacing the hardcoded
   filter list.
2. **Leave it hardcoded**, with the existing fallback (unfiltered rows still list, label legibly,
   and are reachable by actor or date).

## Decision

**States move.** `public.state` (shared migration, no per-tenant copy), seeded from a Java list the
same way `PermissionCatalog` seeds `permission`, read through `GET /api/reference/states`, cached
in memory for the life of the process. See "States: data or a seed?" below for why seeding, not a
one-time data migration, is the right shape.

**Boards stay an enum.** `Board` already types three request records and two response records
across the `school` module (`CreateSchoolRequest`, `BootstrapSchoolRequest`,
`UpdateSchoolProfileRequest`, `SchoolResponse`, `SchoolProfileResponse`), and it backs a database
`check` constraint on two tables. Moving it to data means a migration touching all of those plus a
backfill of every existing school's stored value — real, non-trivial work — to remove a list that
changes only when a new curriculum body starts operating in India, which happens roughly once a
decade and never without months of notice. `docs/status.md` already records leaking a
non-obviously-exposed enum type as a live source of confusion in this codebase; multiplying that
by rebuilding a type other modules already name, for a list this stable, is the wrong trade this
lane was scoped to make. What does move: `Board` grows a `label()` so `GET /api/schools/boards`
(in `school`, not `platform` — see "Why boards live in `school`" below) can serve the same
`{value, label}` shape as states, and the frontend stops keeping its own copy of the label text.

**The audit action filter stays hardcoded**, for now, with a measured reason rather than an
unexamined one. See "The audit action filter: measured, not assumed" below.

## States: data or a seed?

Three shapes were possible for `public.state`:

1. **A migration that inserts the rows directly**, as literal `insert` statements.
2. **Immutable reference data**, presented as if it can never change.
3. **Seeded from code, upserted at every startup**, the `PermissionCatalog` → `permission` pattern.

(1) is wrong on inspection: migrations in this project are immutable once merged, and India has
renamed a state (Orissa → Odisha, 2011), reorganised one into a union territory split (Ladakh from
Jammu and Kashmir, 2019) and merged two union territories (Dadra and Nagar Haveli with Daman and
Diu, 2020) inside the last fifteen years. A table filled by literal `insert`s is a table nobody can
correct without a migration that edits a row a previous migration inserted — exactly the trap this
ADR was asked to avoid.

(2) is also wrong, just less obviously: "immutable" is a claim about the data, and the data is not
immutable — only the _mechanism that changes it_ needs to be safe. Calling it immutable would just
mean the next rename becomes someone's emergency.

(3) is what shipped. `IndianStates.ALL` is the source of truth, `ReferenceDataSeeder` copies it
into `public.state` at every startup with an `insert … on conflict (code) do update set name =
excluded.name`, and a rename is a one-line, reviewed, versioned change to a Java list — the same
shape `PermissionCatalog`/`PermissionSeeder` already use for the `permission` table, and the
precedent this ADR followed rather than invented.

One more decision rode along with this: **`state.code` is the primary key, not a generated id.**
`permission.code` sets this precedent already — a small, code-seeded catalogue keys itself on the
identifier the code assigns, the way `subject` (a school-authored row a user types into a screen and
might rename) does not. `code` is a short, permanent string this project assigns once and never
derives from `name`, so a rename updates `name` for the same `code` rather than becoming a new row.
It is not an external standard (ISO 3166-2 or otherwise) and does not claim to be one.

## Why boards live in `school`, not `platform`

`platform` is the shared kernel every module depends on (`backend/AGENTS.md`: "Nothing with school
domain meaning goes here"). `Board` is `school.domain`'s own enum. A `platform` class importing it
would be a dependency running backwards — the shared kernel reaching into a feature module — which
is exactly what `ModularityTests` exists to catch. So `GET /api/schools/boards` lives in
`SchoolController`, next to the module that owns `Board`, reusing `platform.reference
.ReferenceItemResponse` as a shape (a plain `{value, label}` record, not a service) rather than
`platform.reference.ReferenceDataService`, which only ever talks about states.

The two endpoints are consequently on different prefixes — `/api/reference/states` and
`/api/schools/boards` — rather than both under `/api/reference/*`. That is a visible seam, and it is
the correct one: it is ownership showing through the URL rather than being hidden by it.

## The audit action filter: measured, not assumed

`audit_event` carries three indexes, from `V2026_09_06_0900__platform_create_audit_event.sql`:
`(occurred_at desc)`, `(actor_id, occurred_at desc)`, `(entity_type, entity_id, occurred_at desc)`.
**None of them lead with `action`.** `select distinct action from audit_event` — or the loose
index-scan trick that makes a `distinct` over a low-cardinality _indexed_ column cheap — has nothing
to ride on here: without an index on `action`, PostgreSQL must read every row (or every heap page a
seq scan visits) to produce the distinct set, however few distinct values come out the other end.
The table is append-only, holds a row for every audited write and every login across a school's
whole life, and is purged only after seven years (ADR-0026) — it is a table designed to grow without
bound over a school's lifetime, and unlike a normal listing query, it has no page size to bound the
scan.

That cost does not disappear by caching the _result_: a school's audit log gains new action values
only when a new module ships, so the honest cache lifetime is "until the next deploy" — exactly the
`state` cache's shape — but the _first_ read after each such cache miss still pays a full scan, and
that scan gets more expensive every year the school stays on the platform, on the one screen whose
entire job is staying fast and safe to open under an incident. Building the query now would trade a
list that is honestly incomplete (documented, and already degrading gracefully — an unfiltered row
still lists, labels legibly, and is reachable by actor or date) for one that is complete but
occasionally slow in a way that gets worse with every year of the product's success, on the screen
an administrator opens _during_ an incident. That is a worse failure mode than the one being fixed.

**Decision: leave the audit action filter hardcoded.** The honest fix is an index on `action`
first — after that, a `distinct` becomes a query the planner can loose-index-scan cheaply regardless
of table size, and only then does exposing "the distinct actions in this school's log" (per
`docs/status.md`'s own description of what would close this) stop being a trade against the audit
screen's worst-case latency. That index is a schema change to a table this lane was not scoped to
touch, and adding one to land a filter convenience is the wrong order of operations: measure with
the index in place, then decide, rather than assume the index and ship the query. This is recorded
as a decision with a reason, not a gap left unexamined — the next lane that opens `audit_event` for
another reason should add `idx_audit_event_action` and revisit this ADR, not silently ship a full
scan behind a dropdown.

## Amendment, 2026-09-08: `GET /api/schools/boards` needed an exemption from `SetupKeyFilter`

Landing this endpoint under `/api/schools/**` was the right call for the reason given above — but it
did not account for `platform.config.SetupKeyFilter` (`@Profile("prod")`), which guards that whole
prefix behind a shared setup key regardless of what `@PreAuthorize` says underneath. On `prod` — the
only profile where the filter runs, which is why `test`-only CI never caught it — a signed-in school
administrator's Board picker on the school-profile form got the same `NF_002` a stranger's
unauthorized write would. `test`-profile coverage (`ReferenceDataApiTests`, this ADR's own test file)
proved the endpoint worked and missed the one profile where it did not.

Fixed in [ADR-0032](0032-school-timezone.md), which named the endpoint an explicit exemption in
`SetupKeyFilter` rather than moving it off this prefix — moving it would have undone the "ownership
shows through the URL" reasoning above for no endpoint this ADR did not already place deliberately.
See that ADR's own section on the bug for the two options weighed and why the exemption, not a move,
is what shipped. `SetupKeyFilterTests` now pins `GET /api/schools/boards` against the `prod` profile,
which is the coverage this bug shows every endpoint under this prefix needs and did not have before.

## Consequences

- `public.state`, `platform.reference.*` (`IndianStates`, `ReferenceDataSeeder`, `State`,
  `StateRepository`, `ReferenceDataService`, `ReferenceItemResponse`, `ReferenceDataController`),
  and `GET /api/reference/states` — `isAuthenticated()`, no permission, mirroring
  `identity.api.MeController` and `platform.dashboard.DashboardController`.
- `Board` grows `label()`; `SchoolService#boards` and `SchoolController#boards` (`GET
/api/schools/boards`, also `isAuthenticated()`) are additive to an existing module.
- The school-profile form reads both lists from the API, with a load failure that disables the
  affected pickers and says so, rather than making the whole form unusable — the same reasoning
  ADR-0007's envelope already applies to every other read this screen makes.
- The audit screen's action filter is unchanged. `idx_audit_event_action` plus this decision is
  the next lane's starting point if the filter's incompleteness becomes a real complaint rather
  than a documented one.
- A future admin screen that lets someone edit a state or a board through the API would need to
  evict `ReferenceDataService`'s in-memory cache at write time; nothing today does, because nothing
  today writes.
