# Architecture overview

```
                     ┌──────────────────────────┐
  Browser / tablet ──▶  Angular 22 (frontend/)   │  static bundle, served by nginx
                     └────────────┬─────────────┘
                                  │  /api/v1  (JSON, RFC 9457 errors)
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
                     │  H2 → PostgreSQL          │  shared DB, row-level tenancy (ADR-0002)
                     └──────────────────────────┘
```

## Shape

- **One repository, one backend deployable, one frontend app.** Deployed to a VPS with Coolify.
- **Modular monolith** (ADR-0001). Modules are compile-time isolated, so any of them can become a
  service later without untangling reach-ins.
- **Shared database with row-level tenancy** (ADR-0002), with a documented path to per-tenant
  databases for schools that need physical isolation.
- **Flyway owns the schema.** Hibernate validates it and never creates it.
- **One response envelope** for every endpoint, with a trace id on every response (ADR-0007).

## Where things are decided

| Decision | Record |
|---|---|
| Why a modular monolith | [ADR-0001](adr/0001-modular-monolith.md) |
| How multi-tenancy works | [ADR-0002](adr/0002-multi-tenancy-strategy.md) |
| Authentication | [ADR-0003](adr/0003-authentication-and-authorization.md) |
| H2 now, PostgreSQL next | [ADR-0004](adr/0004-h2-now-postgresql-next.md) |
| Permissions, roles and scopes | [ADR-0005](adr/0005-authorization-model.md) |
| What is configurable, and where | [ADR-0006](adr/0006-configurability-model.md) |
| The API response envelope | [ADR-0007](adr/0007-api-response-envelope.md) |
| Where navigation comes from | [ADR-0008](adr/0008-server-driven-navigation.md) |
| Hand-built UI components | [ADR-0009](adr/0009-hand-built-component-library.md) |
| Responsive and adaptive layout | [ADR-0010](adr/0010-responsive-and-adaptive-layout.md) |

## Module inventory

See [module-map.md](module-map.md). Spring Modulith also generates component diagrams into
`backend/target/spring-modulith-docs/` on every `./mvnw verify`.
