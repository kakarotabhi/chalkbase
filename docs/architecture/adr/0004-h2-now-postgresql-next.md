# ADR-0004: H2 during scaffolding, PostgreSQL before real data

- Status: Accepted
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

## Consequences

- Portable-SQL discipline costs a little expressiveness now and saves a migration rewrite later.
- Tests on H2 are fast but not proof. Once Testcontainers is in place, CI runs on PostgreSQL and H2
  becomes a local convenience only.
