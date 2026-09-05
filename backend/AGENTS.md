# Backend — agent instructions

Spring Boot 4.1 · Java 21 · Spring Modulith 2.1 · JPA/Hibernate · Flyway · H2 today, PostgreSQL next
(ADR-0004). Read with the root `AGENTS.md`.

## Layout

Each direct sub-package of `in.chalkbase` is one Spring Modulith application module.

```
in.chalkbase
├── platform/            OPEN module — shared kernel. Tenancy, security, error handling, config.
│                        Nothing with school domain meaning goes here.
└── <module>/            e.g. school, identity, admission, student, attendance, fee, exam…
    ├── api/             @NamedInterface — the ONLY package other modules may import. DTOs, events,
    │                    SPI interfaces, controllers.
    ├── application/     Services. Transaction boundaries live here.
    ├── domain/          Entities, value objects, domain logic.
    └── infrastructure/  Repositories, external clients, adapters.
```

## Adding a module

1. Create `in.chalkbase.<module>` with a `package-info.java` carrying `@ApplicationModule`.
2. Add `api/package-info.java` with `@NamedInterface("api")`.
3. Write the Flyway migration before the entity.
4. Add the module to `docs/architecture/module-map.md`.
5. `./mvnw verify` — `ModularityTests` must stay green.

## Rules

- **Migrations**: `src/main/resources/db/migration/V<yyyy_MM_dd_HHmm>__<module>_<what>.sql`.
  Timestamp versions, module prefix. Never edit a merged migration. Keep SQL portable across H2 and
  PostgreSQL until ADR-0004 is executed; PostgreSQL-only syntax needs a vendor-specific location.
- **`ddl-auto` stays `validate`.** If Hibernate complains about a missing table, the migration is
  missing — do not relax the setting.
- **Tenancy is structural** (ADR-0011). One PostgreSQL schema per school; **no `school_id` column
  anywhere**. Entities and queries carry no tenancy at all — the schema is selected per connection
  from `platform.tenancy.TenantContext`. A repository method taking a `schoolId` argument is a
  review blocker. Global reference data (boards, states, subjects) lives in `public`.
- **Two migration folders**: `db/migration/shared` runs once against `public`; `db/migration/tenant`
  runs against every school's schema. Putting a tenant table in `shared` (or the registry in
  `tenant`) breaks onboarding. Spring's auto-migration is off — the orchestrator fans out at startup.
- **Migrations are expand/contract only.** A fan-out can fail partway and leave schools on different
  versions, so a release must never contain a destructive change the same release's code depends on.
- **Controllers** live in `api/`, are thin, and return records. Path prefix `/api/v1/` — frozen and
  not a version number; the API is not versioned (ADR-0016). Never create a `v2` package.
- **Add fields, never remove or rename them.** A breaking change is allowed, but it changes the
  frontend in the same pull request.
- **Navigation is data, not markup** (ADR-0008). A module contributes navigation nodes as
  stable ids plus label keys. Never send a URL, a component name, or anything visual.
- **Responses**: controllers return `ApiResponse<T>` via `ApiResponse.success(...)` (ADR-0007).
- **Errors**: throw `ChalkbaseException` with a module `ErrorCode`; `platform.error.GlobalExceptionHandler`
  maps it. Never build an error response inside a controller, and never throw
  `IllegalArgumentException` to mean "bad request" — it is what the JDK throws for real bugs.
- **New constraint, new message**: a migration adding a unique or check constraint also adds a
  `ConstraintMapping` in that module, or the violation surfaces as a generic conflict.
- **Transactions**: `@Transactional(readOnly = true)` on the service class, `@Transactional` on the
  writes. Never on a controller or a repository.
- **Cross-module writes** go through the other module's `api/` or a domain event — never its
  repository.

## Tests

- `ModularityTests` — boundaries. Never weaken it.
- One `@SpringBootTest` slice per module covering the happy path and one validation failure.
- Every tenant-scoped module needs a negative test: tenant A cannot read tenant B's rows.
- Fixtures use invented schools and people. Never real student data.

## Formatting

Spotless with palantir-java-format (4-space indent, 120 columns) runs on `verify` and in the
pre-commit hook. Run `./mvnw spotless:apply` rather than hand-aligning anything.
