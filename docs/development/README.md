# Development

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) | Spring Boot 4.1 needs 17+; 21 is what CI uses. |
| Maven | wrapper (`./mvnw`) | Do not install Maven separately. |
| Node | 24 (Active LTS) | Angular 22 requires ≥ 22.22.3. Use `nvm use 24`. |

## Setup

```bash
tools/setup-dev.sh
```

That points git at `.githooks`, installs frontend dependencies and warms the backend build.

## Running

```bash
cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm start                  # http://localhost:4200 (proxies /api → :8080)
```

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | API explorer |
| http://localhost:8080/h2-console | H2 console (JDBC URL `jdbc:h2:mem:chalkbase`, user `sa`, no password) |
| http://localhost:8080/actuator/modulith | Live module structure |

H2 is in-memory: **data disappears on restart.** That is deliberate while the schema churns — see
[ADR-0004](../architecture/adr/0004-h2-now-postgresql-next.md).

## Checks before pushing

```bash
cd backend  && ./mvnw verify              # tests + module boundaries + format gate
cd frontend && npm test -- --watch=false && npm run build
```

CI runs exactly these.

## Pipelines

Two independent workflows, so a backend change never waits on a frontend build and vice versa.

| Workflow | Triggers on changes to | Does |
|---|---|---|
| [`.github/workflows/backend.yml`](../../.github/workflows/backend.yml) | `backend/**`, its Dockerfile | `./mvnw verify` (tests + module boundaries + format gate), uploads the jar; on `main` also builds the Docker image |
| [`.github/workflows/frontend.yml`](../../.github/workflows/frontend.yml) | `frontend/**`, its Dockerfile and nginx config | Prettier check, tests, build, uploads the bundle; on `main` also builds the Docker image |

Both run on **every push to any branch** and on pull requests, and can be started by hand from the
Actions tab.

The image jobs build but do not push — Coolify builds from the repository itself. They exist so a
broken Dockerfile fails in CI instead of during a deploy. If Coolify is later switched to pulling
prebuilt images, these are where the push to GHCR goes.

**Caveat on branch protection:** because both workflows are path-filtered, a docs-only PR runs
neither. If these are made *required* status checks, such a PR will sit forever waiting for a check
that will never start. Either leave them optional, or add a no-op job that always runs.

## Coding standards

Formatting is not a matter of taste here — it is automated:

| | Formatter | Style | When it runs |
|---|---|---|---|
| Java, SQL, pom | Spotless + palantir-java-format | 4-space, 120 cols | pre-commit hook, `mvn verify`, CI |
| TS, HTML, SCSS, JSON | Prettier | 2-space, 100 cols, single quotes | pre-commit hook, CI |
| Everything | `.editorconfig` | LF, UTF-8, final newline | your editor |

`.githooks/pre-commit` formats staged files and re-stages them, so style never reaches review.
`.claude/settings.json` runs the same formatters after an AI agent edits a file. If a hook is in your
way, `git commit --no-verify` exists — CI will still catch it.

The rules that formatters cannot enforce (module boundaries, tenancy, DTOs, tokens) are in
`AGENTS.md`, `backend/AGENTS.md` and `frontend/AGENTS.md`, and they apply to humans too.

## Branches and commits

- `feat/<module>-<slug>`, `fix/<module>-<slug>`, `docs/<slug>`
- `type(module): summary` — e.g. `feat(fee): add concession heads`
- One module per PR where possible. A PR that touches the schema states the migration in its
  description.
