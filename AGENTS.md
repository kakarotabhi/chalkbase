# Chalkbase — agent instructions

School management system for Indian K-12 schools. Spring Boot 4 + Angular 22 + PostgreSQL,
self-hosted on a VPS via Coolify. One repo, one deployable backend, one frontend app.

Keep this file short. Detail belongs in `docs/`, and is read on demand.

## Repo map

| Path | What |
|---|---|
| `backend/` | Spring Boot modular monolith. See `backend/AGENTS.md`. |
| `frontend/` | Angular app. See `frontend/AGENTS.md`. |
| `contracts/` | OpenAPI spec shared by both sides. |
| `docs/status.md` | What is done, what is next, what is blocked. **Read this first.** |
| `docs/requirements/` | What we are building. The source of truth for scope. |
| `docs/requirements/07-phase-0-decisions.md` | The 13 Phase 0 answers: board, state, workflows, providers, hosting. |
| `docs/architecture/adr/` | Decisions and their reasons. Read before contradicting one. |
| `docs/architecture/module-map.md` | Which module owns which tables and endpoints. Start here. |
| `docs/manual/` | End-user help, per role. |
| `docs/ai/guidelines/` | Deep reference for this stack. Read the one that matches your task. |
| `ops/` | Docker, Compose, Coolify. |

## Commands

```bash
tools/setup-dev.sh                       # one-time setup, enables the shared git hooks
cd backend  && ./mvnw verify             # tests + module boundary check + format gate
cd backend  && ./mvnw spring-boot:run    # :8080  (H2 console at /h2-console, docs at /swagger-ui.html)
cd frontend && npm start                 # :4200, proxies /api to :8080
cd frontend && npm test -- --watch=false
```

## Rules

1. **Never edit the schema by hand.** Every change is a new Flyway migration. Migrations are
   immutable once merged — fix forward.
2. **Never cross a module boundary except through its named interface or a domain event.**
   `ModularityTests` enforces this; a failure means fix the dependency, not the test.
3. **Tenancy is a PostgreSQL schema per school, not a column.** No `school_id` anywhere. The
   current tenant comes from `platform.tenancy` and is applied as `search_path`, never from a
   `schoolId` request parameter. See ADR-0011.
4. **Never expose a JPA entity** over HTTP or across a module boundary. Use a record DTO.
5. **The frontend never invents an API shape.** If the endpoint does not exist yet, say so.
6. **Do not add a UI component library.** Components are hand-designed; build them in
   `frontend/src/app/shared/components` from the tokens in `frontend/src/styles/_tokens.scss`.
7. **Do not hardcode a colour, spacing value or font size** in a component style. Use a token.
8. **Ask before adding a dependency.** Prefer what the framework already gives us.
9. **Personal data of children is the default assumption.** No student, parent or staff data in
   logs, error messages, test fixtures committed to the repo, or third-party calls. See
   `docs/ai/guidelines/06-security-privacy-compliance.md`.
10. **Update the docs in the same change.** A user-facing change updates `docs/manual/`; a
    structural decision gets an ADR; a new module updates `docs/architecture/module-map.md`; and
    anything that moves the project forward updates `docs/status.md`.
11. **Every endpoint that changes data calls `AuditService.recordChange`** in the same transaction
    as the change, passing the NAMES of the changed fields and never their values (ADR-0018). Nothing
    in the compiler forces this, which is exactly why it is written down. Never pass a password, a
    hash, a session id or any value of a Restricted or Confidential field to any audit method.

## Conventions

- Branches: `feat/<module>-<slug>`, `fix/<module>-<slug>`, `docs/<slug>`.
- **`main` is protected.** Never push to it directly — open a pull request and let both CI
  checks pass. This applies to agents too.
- Commits: `type(module): summary` — e.g. `feat(fee): add concession heads`.
- Formatting is automatic (Spotless for Java, Prettier for the frontend) and enforced in CI.
  Do not reformat files you did not otherwise change.
- Database: `snake_case`, **singular** table names (`student`, `fee_charge`). FKs `<table>_id`,
  timestamps `created_at`/`updated_at` as `timestamptz`, indexes `idx_<table>_<cols>`, constraints
  `uq_`/`ck_`/`fk_`, migrations `V<n>__<snake_case>.sql`.
- A boolean column **reads as a predicate**: `active`, `is_current`, `must_change_password`. Prefix
  with `is_`/`has_` only where the bare word would be ambiguous or collide with SQL — `current` is
  why `is_current` is not just `current`.
- **Primary keys are UUIDv7**, because schema-per-tenant (ADR-0011) means ids from different schools
  meet during any cross-school rollup or export, where sequences would collide.
- **Money is `numeric(12,2)`** in the database and `BigDecimal` in Java. Never a float, anywhere.
- Lists use **offset pagination** — `?page=0&size=25&sort=name,asc`, returning `PageResponse<T>`
  inside the ADR-0007 envelope.
- **A module's error codes are part of its contract, not an implementation detail.** When a module
  adds an `ErrorCode`, the screens that can trigger it need the code and the condition, or they fall
  back to wording a bare `CONF_001` for whatever write they happened to be doing — which reads as a
  guess, because it is one. This has been missed three times: if you are writing a brief for the
  other side of the wire, list the codes alongside the endpoints.
- **A null field is omitted from the JSON, not sent as `null`** (`default-property-inclusion:
  non_null`). So in `frontend/.../models.ts` an optional response field is `x?: T`, never
  `x: T | null` — the second is a lie the compiler cannot catch, and code that reads it with
  `=== null` takes the wrong branch in production while passing every mocked spec. Prefer
  truthiness or `??` when reading anything that came off the wire. A list is returned empty rather
  than null so it is always safe to iterate.
- **Fees are append-only** (ADR-0012). Never update a charge or a ledger entry; write a reversal.
- **Every record under a `*/api/` package carries `@Classification` on every component**
  (ADR-0014), and `toString()` returning `Classified.describe(this)`. Both are enforced by
  `ClassificationTests`, which fails the build naming the offending field. Add the annotation in the
  same change that adds the field; pick the more protective tier when it is arguable.
- **That protects `log.info("saving {}", dto)`. It does not protect
  `log.info("saving {}", dto.fullName())`** — nothing does yet. Restricted and Confidential values
  are never logged, never put in an error message, and never sent to a third party, and for the
  accessor case that remains a rule you follow by reading it.
