# Architecture overview

```
                     ┌──────────────────────────┐
  Browser / tablet ──▶  Angular 22 (frontend/)   │  static bundle, served by nginx
                     └────────────┬─────────────┘
                                  │  /api  (JSON, RFC 9457 errors)
                     ┌────────────▼─────────────┐
                     │  Spring Boot 4 (backend/) │  one deployable, many modules
                     │  ┌─────────────────────┐  │
                     │  │ platform (shared)   │  │  tenancy · security · errors · config
                     │  ├─────────────────────┤  │
                     │  │ school · identity   │  │  each module owns its tables
                     │  │ admission · fee · … │  │  talks via api/ or domain events
                     │  └─────────────────────┘  │
                     └────────────┬─────────────┘
                                  │  JDBC
                     ┌────────────▼─────────────┐
                     │  PostgreSQL 17            │  one schema per school (ADR-0011)      
                     └──────────────────────────┘
```

## Shape

- **One repository, one backend deployable, one frontend app.** Deployed to a VPS with Coolify.
- **Modular monolith** (ADR-0001). Modules are compile-time isolated, so any of them can become a
  service later without untangling reach-ins.
- **One PostgreSQL schema per school** (ADR-0011), in one database behind one connection pool. The
  tenant boundary is structural rather than a `WHERE` clause, and a dedicated database stays
  available later for a school that needs physical isolation.
- **Flyway owns the schema.** Hibernate validates it and never creates it.
- **One response envelope** for every endpoint, with a trace id on every response (ADR-0007).

## Where things are decided

| Decision | Record |
|---|---|
| Why a modular monolith | [ADR-0001](adr/0001-modular-monolith.md) |
| How multi-tenancy works | [ADR-0011](adr/0011-schema-per-tenant.md) — supersedes [ADR-0002](adr/0002-multi-tenancy-strategy.md) |
| Authentication | [ADR-0003](adr/0003-authentication-and-authorization.md) |
| H2 now, PostgreSQL next | [ADR-0004](adr/0004-h2-now-postgresql-next.md) |
| Permissions, roles and scopes | [ADR-0005](adr/0005-authorization-model.md) |
| What is configurable, and where | [ADR-0006](adr/0006-configurability-model.md) |
| The API response envelope | [ADR-0007](adr/0007-api-response-envelope.md) |
| Where navigation comes from | [ADR-0008](adr/0008-server-driven-navigation.md) |
| Hand-built UI components | [ADR-0009](adr/0009-hand-built-component-library.md) |
| Responsive and adaptive layout | [ADR-0010](adr/0010-responsive-and-adaptive-layout.md) |
| Schema per tenant | [ADR-0011](adr/0011-schema-per-tenant.md) |
| Identity: per-school accounts | [ADR-0017](adr/0017-identity-model.md) |
| No API versioning | [ADR-0016](adr/0016-no-api-versioning.md) |
| The fee ledger is append-only | [ADR-0012](adr/0012-fee-ledger-model.md) |
| Payment and messaging provider ports | [ADR-0013](adr/0013-external-provider-ports.md) |
| Data classification and DPDP handling | [ADR-0014](adr/0014-data-classification.md) |
| Deployment baseline | [ADR-0015](adr/0015-deployment-baseline.md) |
| The audit log: names, not values | [ADR-0018](adr/0018-audit-log.md) |
| Classes and sections are structural | [ADR-0019](adr/0019-classes-and-sections.md) |
| One student name field; guardians are shared | [ADR-0020](adr/0020-student-and-guardian-model.md) |
| Bulk import: validate first, all-or-nothing | [ADR-0021](adr/0021-bulk-import.md) |
| Encryption at rest: env-var key, marked on the entity | [ADR-0022](adr/0022-encryption-at-rest.md) |

## Module inventory

See [module-map.md](module-map.md). Spring Modulith also generates component diagrams into
`backend/target/spring-modulith-docs/` on every `./mvnw verify`.
