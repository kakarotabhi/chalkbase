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
| `docs/requirements/` | What we are building. The source of truth for scope. |
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
3. **Every tenant-scoped table carries `school_id`**, and tenant filtering goes through
   `platform.tenancy`, never a hand-written `WHERE` or a `schoolId` request parameter. See ADR-0002.
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
    structural decision gets an ADR; a new module updates `docs/architecture/module-map.md`.

## Conventions

- Branches: `feat/<module>-<slug>`, `fix/<module>-<slug>`, `docs/<slug>`.
- Commits: `type(module): summary` — e.g. `feat(fee): add concession heads`.
- Formatting is automatic (Spotless for Java, Prettier for the frontend) and enforced in CI.
  Do not reformat files you did not otherwise change.
