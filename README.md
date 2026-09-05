# Chalkbase

School management system for Indian K-12 schools — pre-primary through senior secondary.

Spring Boot 4.1 (Java 21) · Angular 22 · PostgreSQL 17 · self-hosted on a VPS with Coolify.

> **Status: scaffold.** One vertical slice (`/api/schools`) proves the stack end to end.
> Authentication is not implemented yet, so do not expose a deployment publicly.

## Quick start

```bash
tools/setup-dev.sh                        # one-time: git hooks, npm ci, warm the build

cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm start                  # http://localhost:4200
```

Requires JDK 21 and Node 24. More in [docs/development](docs/development/README.md).

## Repository

```
backend/     Spring Boot modular monolith (Spring Modulith)
frontend/    Angular app — hand-built components, no UI library
contracts/   OpenAPI spec shared by both sides
docs/        requirements, architecture + ADRs, development, operations, user manual, AI guidelines
ops/         Docker, Compose, Coolify
tools/       developer scripts
```

## Documentation

| | |
|---|---|
| [**Where the project is**](docs/status.md) | Done, next, and waiting on a decision |
| [What we are building](docs/requirements/README.md) | Full requirement pack |
| [How it is built](docs/architecture/overview.md) | Architecture and decision records |
| [Module map](docs/architecture/module-map.md) | Which module owns what |
| [Development](docs/development/README.md) | Setup, standards, workflow |
| [User manual](docs/manual/README.md) | End-user help, per role |
| [AGENTS.md](AGENTS.md) | Rules for AI agents and humans alike |

## Decisions worth knowing before you read the code

- [ADR-0001](docs/architecture/adr/0001-modular-monolith.md) — modular monolith, boundaries enforced by a test
- [ADR-0011](docs/architecture/adr/0011-schema-per-tenant.md) — one PostgreSQL schema per school (supersedes [ADR-0002](docs/architecture/adr/0002-multi-tenancy-strategy.md))
- [ADR-0003](docs/architecture/adr/0003-authentication-and-authorization.md) — server-side sessions, in-app identity, pluggable credentials
- [ADR-0004](docs/architecture/adr/0004-h2-now-postgresql-next.md) — H2 now, PostgreSQL before real data
- [ADR-0005](docs/architecture/adr/0005-authorization-model.md) — permissions are code, roles are data, grants carry a scope
- [ADR-0006](docs/architecture/adr/0006-configurability-model.md) — four tiers of configurability
- [ADR-0007](docs/architecture/adr/0007-api-response-envelope.md) — one response envelope, error codes as contract
- [ADR-0008](docs/architecture/adr/0008-server-driven-navigation.md) — the server decides the menu, the client decides the pixels
- [ADR-0009](docs/architecture/adr/0009-hand-built-component-library.md) — build the components, adopt the CDK for behaviour
- [ADR-0010](docs/architecture/adr/0010-responsive-and-adaptive-layout.md) — bottom bar, rail, sidebar; mobile is the default
- [ADR-0012](docs/architecture/adr/0012-fee-ledger-model.md) — the fee ledger is append-only; a balance is a sum
- [ADR-0013](docs/architecture/adr/0013-external-provider-ports.md) — payments and messaging are ports; v1 is offline and email
- [ADR-0014](docs/architecture/adr/0014-data-classification.md) — four data tiers, enforced by a build-failing test
- [ADR-0015](docs/architecture/adr/0015-deployment-baseline.md) — one Mumbai VPS running everything, via Coolify
- [ADR-0016](docs/architecture/adr/0016-no-api-versioning.md) — the API is not versioned and paths carry no version segment
