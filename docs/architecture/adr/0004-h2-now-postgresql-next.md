# ADR-0004: H2 during scaffolding, PostgreSQL before real data

- Status: Accepted — **executed on 2026-09-05**
- Date: 2026-09-05
- Deciders: Raja

## Context

Development starts before any database infrastructure exists. H2 in-memory removes that setup step.
PostgreSQL is the production target (ADR-0002 depends on Row Level Security, which H2 does not have).

The risk is the usual one: code written against H2 that quietly depends on H2 behaviour, discovered
only at the switch.

## Decision

Run H2 **in PostgreSQL compatibility mode**:

```
jdbc:h2:mem:chalkbase;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
```

Flyway owns the schema from the first table, and `ddl-auto` stays `validate` — so migrations are
exercised on every run rather than written retrospectively.

Constraints while on H2:

- Migrations use portable SQL only. No `jsonb`, no partial indexes, no `GENERATED … AS IDENTITY`
  quirks, no PostgreSQL-only functions.
- When PostgreSQL-specific DDL becomes necessary, that is the signal to switch — not a reason to
  add a workaround.

## Switch criteria — do this before, not after, these happen

Move to PostgreSQL when **any** of the following is true, whichever comes first:

- The first tenant-scoped table ships (RLS from ADR-0002 cannot be deferred past it).
- A feature needs `jsonb`, full-text search, partitioning or a partial index.
- Any data must survive a restart, i.e. the first demo with real content.

## Execution

1. `ops/compose/docker-compose.dev.yml` brings up PostgreSQL 17 locally.
2. Switch the datasource via an `application-local.yml` profile; keep H2 only for fast tests.
3. Replace `@SpringBootTest` datasource with Testcontainers PostgreSQL (already a test dependency)
   so tests run against the real engine.
4. Add the RLS policies from ADR-0002 in the same change.

## Outcome — executed 2026-09-05

H2 is gone entirely, earlier than the criteria required, because a hosted PostgreSQL 17 became
available. Development runs against Supabase; tests run against a Testcontainers PostgreSQL 17
container; the portable-SQL constraint above is lifted.

The single migration written under H2 was rewritten as PostgreSQL-native rather than followed with
an `ALTER`. That is a deliberate one-time exception to the immutable-migration rule: H2 was
in-memory, so no database ever persisted the schema and no Flyway history existed to conflict with.
From the first PostgreSQL deployment onwards, migrations are immutable.

### Two things that cost time, recorded so they do not cost it again

**The direct connection host does not work from most machines.** `db.<ref>.supabase.co` resolves to
an IPv6 address only. WSL2, many corporate networks and a lot of CI runners have no IPv6 egress, so
the connection fails with `Network is unreachable` rather than anything about IPv6. Use the
Supavisor pooler host, which is dual-stack:

```
jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
username: postgres.<project-ref>
```

**Port 5432 on the pooler, not 6543.** 5432 is session mode; 6543 is transaction mode. Row-level
security (ADR-0002) sets a per-session variable for the current tenant, and transaction mode gives
no stable session to set it on.

### Layout

Chalkbase owns a dedicated `chalkbase` schema rather than `public`. On Supabase the `postgres`
database also holds managed `auth`, `storage`, `realtime`, `graphql` and `vault` schemas; keeping
our tables out of `public` means Flyway, backups and a `pg_dump -n chalkbase` all have an
unambiguous target.

Verified on the live instance before committing: schema creation, table creation, and
`set_config`/`current_setting` for the RLS session variable. The connecting role is **not** a
superuser, which matters — a superuser silently bypasses RLS, so policies would appear to work in
development while protecting nothing.

### Still open

The Supabase project is in `ap-northeast-2` (Seoul), roughly 150-200 ms from India. That is
irrelevant for development and wrong for production; production belongs in `ap-south-1` (Mumbai).
Moving region means creating a new project, so decide before there is data worth migrating.

## Consequences

- Tests need Docker. This is already true in CI and on the development machine.
- Test runs are slower than in-memory H2 by the container start, and are now actually evidence:
  they exercise the engine production uses.
- The shared development database is shared. It is not a scratch space, and it is not where tests
  run.
