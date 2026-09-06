# Development

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) | Spring Boot 4.1 needs 17+; 21 is what CI uses. |
| Maven | wrapper (`./mvnw`) | Do not install Maven separately. |
| Node | 24 (Active LTS) | Angular 22 requires ≥ 22.22.3. Use `nvm use 24`. |
| Docker | any recent | Integration tests start a PostgreSQL container. |

> **Just want to see it running?** [Running locally](running-locally.md) has the step-by-step,
> including IntelliJ and WebStorm setup, what the demo school contains, and what to check once you
> are signed in. The rest of this page is standards and workflow.
>
> **Changing the API?** The contract between the two sides is generated, and the two files in
> `contracts/` are committed. [`contracts/README.md`](../../contracts/README.md) has the recipe for
> adding a field, a record or an endpoint — the nullable-response case is the one with a trap in it.

## Setup

```bash
tools/setup-dev.sh
```

That points git at `.githooks`, installs frontend dependencies, warms the backend build and
creates `backend/src/main/resources/application-local.yml` from the committed example.

### Database credentials

`application-local.yml` is **gitignored** and is the only file that may hold a real password. This
repository is public; a connection string committed to it is scraped within minutes.

Preferred: leave the placeholder in place and export the password instead.

```bash
export CHALKBASE_DB_PASSWORD='…'   # add to ~/.bashrc
```

## Running

```bash
cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm start                  # http://localhost:4200 (proxies /api → :8080)
```

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | API explorer |
| http://localhost:8080/actuator/health | Health |
| http://localhost:8080/actuator/modulith | Live module structure |

## Profiles

| Profile | Database | Where it is configured |
|---|---|---|
| `local` (default) | Shared Supabase PostgreSQL 17 | `application-local.yml`, gitignored, created from `.example` |
| `test` | PostgreSQL 17 in a Testcontainers container | `src/test/resources/application-test.yml` |
| `prod` | Environment variables only | `application-prod.yml` + `ops/coolify/.env.example` |

`application.yml` holds only what is true everywhere and deliberately contains **no datasource**, so
no environment can inherit another's database by accident.

Two things about the development database that are easy to get wrong, both explained in
[ADR-0004](../architecture/adr/0004-h2-now-postgresql-next.md): use the **pooler** host, not
`db.<ref>.supabase.co` (that one is IPv6-only and unreachable from WSL), and use **port 5432**, not
6543, because the tenant schema is selected with `SET search_path`, which needs a session.

The development database is **shared and persistent.** It is not a scratch space — tests never run
against it, and a migration you push is a migration everyone gets.

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

Both workflows are path-filtered **on push** so branch pushes stay fast, but run **unfiltered on
pull requests** — a required status check that never starts would block a docs-only PR forever.

The image jobs run only on `main`, so they are not required checks.

## Branching

`main` is protected. Nobody pushes to it directly, including administrators.

```bash
git switch -c feat/fee-concession-heads
# … work …
git push -u origin HEAD
gh pr create --fill
```

A pull request can be merged when:

- **Backend build and test** and **Frontend build and test** are both green,
- the branch is up to date with `main`,
- and any review conversations are resolved.

Two layers enforce this, and only one of them is real:

| Layer | What it does | Can it be bypassed? |
|---|---|---|
| `.githooks/pre-push` | Refuses a direct push to `main` with a message explaining what to do instead | Yes — `--no-verify`, or a clone that never ran `tools/setup-dev.sh` |
| GitHub branch protection | Rejects the push server-side and blocks merges until checks pass | No |

The hook exists to catch the honest mistake early, with a useful error, instead of after a failed
push. It is not the control.

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
