# ADR-0015: One VPS in Mumbai, everything on it

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0011](0011-schema-per-tenant.md), [ADR-0004](0004-h2-now-postgresql-next.md),
  [ADR-0014](0014-data-classification.md)

## Context

Development has been running against a Supabase PostgreSQL project in `ap-northeast-2` (Seoul),
roughly 150–200 ms from Delhi. That was fine for a project with no users and is not fine for a
product whose first customer is a Delhi CBSE school, where teachers mark attendance on phones on
school wifi and every request pays that latency twice.

Two other things now constrain the answer. [ADR-0011](0011-schema-per-tenant.md) made tenancy a
PostgreSQL schema per school, so the database creates schemas at onboarding and migrations fan out
across all of them — the database has to be one we fully control. And
[ADR-0014](0014-data-classification.md) holds children's data under DPDP, which makes "where does
this physically live" a question the school will ask out loud.

## Options considered

1. **Managed Postgres in India plus a VPS.** Backups, PITR and version upgrades stop being ours,
   which is worth real money to a small team. Two to three times the cost, an extra network hop on
   every query, and a provider that may not welcome schema creation at the rate schema-per-tenant
   needs. Rejected for now, not permanently.

2. **Hetzner in Germany.** By far the best hardware per rupee. But ~130 ms to Delhi undoes the whole
   reason for moving off Seoul, and it makes the data-location question awkward — DPDP imposes no
   hard localisation rule, so this is a trust and latency call rather than a legal one, and it still
   loses on both.

3. **Two VPS, application and database separated.** Cleaner scaling, independent resizing. Premature
   at one school: an extra hop, a private network to configure and a second machine to patch, for
   load that does not exist.

4. **One VPS in Mumbai running everything.**

## Decision

**Option 4. Hostinger KVM 2 — 2 vCPU, 8 GB RAM, 100 GB NVMe — in Mumbai, running Coolify.**

Roughly 20–30 ms to Delhi NCR, against 150–200 ms today. Data sits in India, which is the answer the
principal wants.

### On the box

| Service | Notes |
|---|---|
| Coolify | Deploys, environment variables, TLS via Traefik, health checks |
| PostgreSQL 17 | One database, one schema per school ([ADR-0011](0011-schema-per-tenant.md)) |
| Spring Boot backend | `prod` profile; Flyway fans migrations across every tenant schema at startup |
| Angular build | Static, served by the reverse proxy |
| MinIO | S3-compatible object storage behind a `StorageService` port |

### Sizing, honestly

KVM 2 is deliberately the smaller of the plans considered. It is enough for one pilot school, and
Hostinger resizes in place, so the cost of being wrong is an upgrade rather than a migration.

**The known pressure point is bulk PDF generation** — report cards for a whole school at end of term,
where the JVM heap and PostgreSQL's shared buffers contend on 8 GB. Mitigations, in order: generate
report cards as a queued background job rather than a request, cap batch size, and only then buy
RAM. Watch it; do not pre-buy for it.

Schema-per-tenant means each additional school adds real weight — its own tables, indexes and
connections — so the second and third school will move this decision sooner than a shared-schema
design would have.

### Storage

**MinIO on the same box, behind a `StorageService` port, one bucket per school** so the storage
boundary mirrors the tenant boundary and a school's files can be exported or erased as a unit
([ADR-0014](0014-data-classification.md)).

Access is always a **short-lived pre-signed URL issued after a permission check**. Never a public
bucket, never a guessable path, and no student document served directly by the application. Because
MinIO speaks S3, moving to Cloudflare R2 or S3 Mumbai later is configuration, not code — and that
move is the natural first step if the box ever needs rebuilding.

### Backups, and the part that is usually skipped

- Nightly `pg_dump` plus continuous WAL archiving, **off the box**, to separate object storage. A
  backup on the same machine as the database is not a backup.
- MinIO contents backed up on the same schedule.
- Per-school restore is a schema-level restore — a genuine benefit of
  [ADR-0011](0011-schema-per-tenant.md), since one school can be rolled back without touching
  another.
- **A scheduled restore drill.** The MVP acceptance criteria require backups to be restore-tested,
  and an untested backup is a belief, not a backup.
- Backups contain `RESTRICTED` data and are encrypted at rest with a key stored outside the VPS.

### Environments

`local` (H2 or Docker PostgreSQL) → `test` (Testcontainers) → `prod` (this box). No separate staging
environment for now; when one is needed it is a second Coolify project on the same host, accepting
that it shares resources.

## Consequences

**Easier.** Latency drops by an order of magnitude for the users who matter. Everything is in one
place, so a deploy is one Coolify action and there is one machine to reason about. No cross-network
hop between application and database. Costs on the order of ₹600–800 per month, which a single
school's subscription covers many times over. The data-location question has a clean answer.

**Harder.** One machine is one failure domain: it is also the single point of failure, and its
maintenance — OS patching, PostgreSQL upgrades, disk pressure — is ours. There is no high
availability and no automatic failover, so an outage is measured in however long a restore takes,
which is exactly why the restore drill is not optional. A noisy neighbour on shared vCPU can affect
tail latency.

**To revisit.** Move the database to managed Postgres, or split the box, when either the second
school lands or the report-card batch starts contending — whichever comes first. Move object storage
off-box before the disk fills, which will be driven by student documents rather than by the database.
If uptime ever becomes contractual, this decision is the first thing that has to change.
