# ADR-0011: Schema per tenant

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Supersedes: [ADR-0002](0002-multi-tenancy-strategy.md)
- Related: [ADR-0004](0004-h2-now-postgresql-next.md), [ADR-0005](0005-authorization-model.md)

## Context

[ADR-0002](0002-multi-tenancy-strategy.md) chose a shared database with a `school_id` column on every
tenant-scoped table. Its volume analysis still holds and is worth keeping: only attendance, the
notification log and the audit log ever get large, and row-level tenancy would not have been a
throughput problem.

The decision is being changed on different grounds. Row-level tenancy puts the tenant boundary in
**application code** — every query must carry `school_id`, and the cost of forgetting is that a
school sees another school's children. Schema per tenant puts the boundary in the **database**: a
query that loses its tenant context hits a missing table, not somebody else's rows.

For a product holding children's records, moving that boundary out of code and into structure is
worth paying for.

## Decision

**One PostgreSQL schema per school, in one database, served by one application and one connection
pool.**

```
postgres  (one database, one pool)
├── public                     tenant registry, boards, states, districts, platform tables
├── greenfield                 school, student, guardian, fee_invoice, attendance, …
└── sunrise                    the same tables again, that school's rows
```

### How a request reaches the right schema

Hibernate's `SCHEMA` multi-tenancy strategy, with a `MultiTenantConnectionProvider` over the single
pool. The tenant comes from the authenticated session ([ADR-0003](0003-authentication-and-authorization.md)),
never from a request parameter.

```java
Connection getConnection(String schema) {
    Connection c = dataSource.getConnection();
    exec(c, "SET search_path TO " + validated(schema));
    return c;
}
void releaseConnection(String schema, Connection c) {
    exec(c, "SET search_path TO public");   // not optional — see below
    c.close();
}
```

Application code contains no tenancy at all: no `school_id` column, no filter to forget.

### Three rules that are load-bearing, not stylistic

1. **Reset `search_path` when the connection returns to the pool.** A pooled connection carries its
   `search_path` to whoever gets it next. Skip the reset and the next request reads the previous
   school's data — silently, with no error and nothing in a log. This was reproduced against the
   real database before the decision was made.
2. **Validate the schema name.** `SET search_path TO ?` is not valid SQL, so the identifier is
   concatenated. It comes from the tenant registry and is checked against a strict pattern — never
   passed through from a request.
3. **The connection must be in session mode.** `SET` does not survive a transaction-mode pooler.
   Supabase's port 5432 is session mode and 6543 is not. Tenancy now depends on that port; it is not
   a tuning knob.

### Migrations: two sets, and a loop we write ourselves

Flyway has no notion of "apply to every tenant". It migrates one schema and records it in one
history table, so the fan-out is ours.

```
db/migration/shared/   → once, against public: tenant registry, reference data
db/migration/tenant/   → against every tenant schema: student, fee_invoice, attendance, …
```

They cannot share a folder: the registry that lists the schemas cannot itself live inside one.

Each tenant schema keeps its **own** `flyway_schema_history`, so versions are tracked per school.
Spring Boot's automatic migration is switched off (`spring.flyway.enabled: false`) because it would
migrate a single schema at boot; a custom orchestrator runs instead.

### Migration runs at startup — deliberately, and with an expiry

Every tenant is migrated when the application starts, before it serves traffic. This is the simple
option and it is the right one now: it cannot drift, there is nothing extra to deploy, and at a
handful of schools it costs seconds.

It has a known expiry, recorded here so that reaching it is a decision and not an incident:

- **Startup time is linear in tenant count.** At 500 schools and ~2s each, boot takes ~17 minutes.
- **Every replica migrates.** Flyway's advisory lock keeps this safe, but instances queue behind
  each other and the slowest start wins.
- **A failing migration means the application does not start**, for every school, including the ones
  that migrated fine.

**Move migration out of startup into a deploy step when any of these is true:** startup exceeds
about a minute, there is more than one replica, or tenant count passes ~50. The orchestrator is the
same code either way — only its trigger changes.

### Onboarding a school

`CREATE SCHEMA`, then run the tenant migrations against it; a fresh schema receives every migration
in order and lands at the current release. Onboarding must be serialised against the migration run,
or a school created mid-deploy is left a version behind.

### Row-level security is no longer needed

Schemas do the isolating, so the RLS policies ADR-0002 required are dropped. One fewer mechanism to
build, and one fewer place for the boundary to be wrong.

## Consequences

- **Version skew is the failure mode to design for.** If migration 47 succeeds for 120 schools and
  fails on the 121st, code that assumes v47 is now live against schools on v46. The rule that
  follows: **migrations are expand/contract only** — never a destructive change in the same release
  as the code that depends on it. Adopt this from the first migration rather than after the first
  incident.
- **We now own tooling that row-level would not have needed**: the tenant registry, the migration
  orchestrator, a view of which school is on which version, and provisioning on onboarding. It is
  not difficult, but it exists before the first feature and it breaks during releases.
- **Cross-school reporting costs more.** Group dashboards for a multi-branch trust, UDISE+
  aggregates and product analytics become `UNION ALL` across schemas or a loop, where row-level made
  them one query.
- **There is a ceiling.** 60 tables × 500 schools is 30,000 tables; `pg_dump`, autovacuum and
  `information_schema` all feel it. Hundreds of schemas is comfortable, thousands is not. Revisit
  before then.
- **Per-school backup gets easier**, which also makes ADR-0002's hybrid escape hatch easier: moving
  one school to a dedicated database becomes `pg_dump -n <schema>` and a routing entry.
- **Tests must create schemas.** Integration tests provision at least two tenant schemas, and every
  module needs the negative test: tenant A cannot read tenant B — now by proving the query fails,
  not that it returns nothing.

## The tenant is a campus, not a group

**A multi-campus school group is one schema per campus.** FR-002 puts campuses under a group; each of
those campuses is a tenant in its own right, with its own schema, its own Flyway history and its own
row in the registry.

The alternative — one schema per group with a `campus_id` column on every table — was rejected
because it is row-level tenancy again, one level down, and it reintroduces exactly the failure this
ADR exists to remove: a forgotten `WHERE campus_id` showing one campus another campus's children.
Having chosen a structural boundary, putting a second, weaker boundary inside it would be the worst
of both.

A group is therefore a **grouping in `public`**, not a container:

```
public.school_group          the trust or society
public.school                one row per campus, its schema name, and its group
```

### What this costs

Group-wide reporting is a fan-out — query each campus's schema and merge — where one schema per
group would have made it a single query. That is the price of the isolation, and it is paid by the
few groups rather than by every school.

Two things follow from it, and they are cheap now:

- **Primary keys must be globally unique**, because ids from different schemas meet in a merged
  report. UUIDv7 is already the convention.
- **A group report is a read-side concern.** When one is needed, it fans out and merges — it does
  not justify a shared schema, and it does not justify a cross-schema foreign key. There are none.

### Not exercised by the MVP

Phase 0 scoped the first market to a single-campus CBSE day school in Delhi NCR, so nothing built
for the MVP will have more than one campus. This is settled now because it constrains every table
created from the first migration onward — not because a group is imminent.
