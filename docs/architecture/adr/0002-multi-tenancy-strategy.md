# ADR-0002: Multi-tenancy strategy

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Supersedes: none

## Context

Chalkbase starts as a single-school deployment, then multi-branch school groups, then possibly a
multi-school SaaS. The tenancy model has to be chosen now because it touches every table, every
query and every migration; retrofitting it later is a rewrite.

Three candidates were considered:

1. **Shared database, row-level tenancy** — every tenant-scoped table carries `school_id`.
2. **Schema per tenant** — one PostgreSQL database, one schema per school, `search_path` switching.
3. **Database per tenant** — a separate database (or cluster) per school.

The stated worry driving this ADR: *"schools have large data, row-level will become a bottleneck."*
That claim was tested against actual volumes before deciding.

## Volume analysis

Sizing from a typical Indian K-12 school of 1,200-1,500 students:

| Data | Rows per school per year | At 100 schools | At 500 schools |
|---|---|---|---|
| Attendance (daily, per student) | ~330K | 26M/yr | 132M/yr |
| Notification/SMS log | ~360K | 36M/yr | 180M/yr |
| Exam marks | ~72K | 7M/yr | 36M/yr |
| Fee transactions | ~15K | 1.5M/yr | 7.5M/yr |
| Students, staff, classes, timetable | < 20K total | ~2M | ~10M |

Only three tables are large: **attendance, notification log, audit log**. Everything else is small
enough to be uninteresting even at 500 schools.

A PostgreSQL btree index on `(school_id, student_id, attendance_date)` serves single-school queries
in the same number of page reads whether the table holds 300K rows or 300M — index depth grows
logarithmically. The three hot tables can be declaratively partitioned (by `school_id` hash, or by
academic session for time-series data), which restores per-school scan locality and makes retention
purges a `DROP PARTITION`.

**Conclusion: row-level tenancy does not become a performance bottleneck at any tenant count this
product will realistically reach.** The real risks of row-level tenancy are correctness (a forgotten
`WHERE school_id = ?` leaks data) and operations (per-school restore), not throughput.

## Costs of database-per-tenant

Isolation is genuinely better, but it is paid for up front and forever:

- **Migration fan-out.** Every deploy runs Flyway against N databases. At 200 schools and 30s per
  migration set, that is ~100 minutes per release, and a mid-run failure leaves tenants on different
  schema versions — which application code must then tolerate. This needs orchestration, per-tenant
  version tracking and a repair path before the first feature ships.
- **Connection pressure.** A pool per database does not fit: 200 tenants x 2 connections = 400
  against a default `max_connections` of 100. Requires PgBouncer or a routing DataSource with a
  shared pool, i.e. infrastructure work on day one.
- **Cross-tenant work becomes hard.** Group-level dashboards for a multi-branch trust, product
  usage analytics, and board/UDISE+ aggregate reporting all turn into fan-out-and-merge jobs.
- **Onboarding is provisioning.** Creating a school becomes "create a database, migrate it, seed
  it, register it" instead of one INSERT.
- **Zero benefit while there is one school**, which is the entire near-term roadmap.

Schema-per-tenant was rejected as the worst of both: it carries the same migration fan-out and
provisioning cost as database-per-tenant, gives weaker isolation, and PostgreSQL's catalog degrades
once schema counts reach the thousands.

## Decision

**Shared database with row-level tenancy, designed so that per-tenant databases can be introduced
later for specific schools without changing feature code.**

Concretely:

1. Every tenant-scoped table has a non-null `school_id uuid`, and every index that matters leads
   with `school_id`.
2. Reference data that is genuinely global — boards, states, districts, subject catalogue, ICD-style
   code lists — has no `school_id`.
3. The current tenant is resolved once per request into `platform.tenancy.TenantContext`. Feature
   code never reads a school id from a request parameter or path; passing `?schoolId=` as a filter
   from the client is forbidden.
4. Filtering is applied centrally by a Hibernate tenant filter, not by hand in each repository
   method. A repository method that takes a raw `schoolId` argument is a code smell and a review
   blocker.
5. **On PostgreSQL, enable Row Level Security** on tenant-scoped tables with a policy on the
   session's tenant GUC. This is the key point: RLS gives the database-enforced guarantee that is
   the main honest argument for physical separation, at almost no cost. A forgotten `WHERE` clause
   returns zero rows instead of another school's children.
6. Access goes through a `DataSource` indirection from the start, even though it resolves to a
   single pool today.

## Escape hatch (the hybrid end state)

Because every row already knows its school, moving one school onto its own database is an
extraction, not a redesign: dump that school's rows, load them into a dedicated database, and add a
routing entry. `TenantContext` already carries what the router needs.

Move a school to a dedicated database when any of these becomes true:

- The school contractually requires physical data separation (some trusts and government tie-ups
  will).
- One school's volume is materially degrading others (noisy neighbour), after partitioning has
  already been applied.
- Total tenant count passes ~300 and the shared instance's operational blast radius becomes
  unacceptable.

The expected end state is **hybrid**: a pooled shared database for the long tail of schools, plus
dedicated databases for the few large or compliance-bound ones. That is where mature school ERPs
land, and it is reachable from here without a rewrite. It is not reachable from a
database-per-tenant start without having already paid for all of it.

## Consequences

- Every new table needs a deliberate answer to "is this tenant-scoped?" — enforced in code review
  and, once the schema stabilises, by an automated check over `information_schema`.
- Per-school restore ("restore our data as of last Tuesday") is harder than with separate databases.
  Mitigation: nightly per-school logical exports in addition to cluster-level PITR, plus soft
  deletes so the common case is undelete rather than restore.
- H2 has no RLS, so during the H2 phase the Hibernate filter is the only guard. RLS policies land
  with the PostgreSQL migration (ADR-0004) and must not be deferred past it.
- Integration tests must include a negative test per tenant-scoped module: tenant A cannot read
  tenant B's rows.
