# Running Chalkbase on your machine

Two processes: the backend on `:8080` and the Angular dev server on `:4200`. You use `:4200` — it
proxies `/api` to the backend, so the browser sees one origin and the session cookie works.

## What you need

| | Version | Why that one |
|---|---|---|
| JDK | **21** | Spring Boot 4.1 needs 17+; the build targets 21 |
| Node | **24** (≥ 22.22.3) | Angular 22 refuses to build on Node 20, which is end-of-life |
| PostgreSQL | 17, or the Supabase dev project | Schema-per-tenant uses `set_config` and partial indexes; H2 cannot express them |

Check with `java -version` and `node --version`. If `node` is old, use nvm: `nvm use 24`.

## First run

### 1. The database

Copy the example config and fill in the password:

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml
```

Then edit it, or better, export the password instead of typing it into the file:

```bash
export CHALKBASE_DB_PASSWORD='...'
```

`application-local.yml` is gitignored. The host and username are placeholders in the example — get
the real ones from the Supabase dashboard, or point it at a local PostgreSQL 17.

> If you'd rather run Postgres locally than use Supabase, any PostgreSQL 17 works. Point the URL at
> it and give the user permission to create schemas — Chalkbase makes one per school.

### 2. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

On first start it runs the shared migrations against `public`, then the tenant migrations against
every school, then **seeds a demo school** (`local` profile only). Watch the log for the block
naming the school code, the usernames and the password — that is how you sign in.

To skip the demo data, set `chalkbase.dev.seed-demo-school: false` in `application-local.yml`.

> **Two things to know about the demo school.**
>
> **It is probably already there.** The example config points at a Supabase project shared by
> everyone working on Chalkbase, so `DEMO-001` exists already and your seeder will log
> *"already registered; leaving it alone"* and skip. That is the intended outcome — you get the data
> without the wait, and the seeder never touches a school it did not create.
>
> **If you are the one creating it, the first run is slow** — around fifteen minutes against a
> database in Seoul, because the seeder drives the real REST API and that is several hundred round
> trips. Against a PostgreSQL on your own machine it is seconds. It happens once; every later start
> skips.
>
> To rebuild it from scratch: `drop schema demo_school cascade;` and
> `delete from public.school where code = 'DEMO-001';` then restart.

### 3. Start the frontend

```bash
cd frontend
nvm use 24
npm install      # first time only
npm start
```

Open **http://localhost:4200** and sign in with the credentials from the backend log.

Do not open `:8080` in a browser expecting the app — that is the API only. `proxy.conf.json` is what
makes `/api` calls from `:4200` reach it.

## IntelliJ IDEA (backend)

1. **Open** the `backend` folder as the project — not the repository root. IntelliJ imports the
   Maven project and downloads dependencies.
2. **File → Project Structure → Project SDK** → a JDK 21.
3. Open `ChalkbaseApplication.java` and press the green arrow beside the class. That creates a run
   configuration.
4. **Edit the run configuration** and confirm *Active profiles* is empty or `local` — `application.yml`
   defaults to `local`, so an empty field is correct.
5. If the password comes from your shell rather than the file, add it under
   *Environment variables*: `CHALKBASE_DB_PASSWORD=...`. IntelliJ does not inherit your terminal's
   exports.

**Debugging:** the same arrow with the bug icon. Breakpoints in a `@Transactional` service are the
useful ones; remember Hibernate picks the tenant schema when the transaction opens, so inspect
`TenantContext` at the boundary rather than deeper in.

**Running one test:** the arrow beside a test class or method. `StudentImportApiTests` and the other
`*ApiTests` start Testcontainers and want Docker running — the first run pulls the PostgreSQL 17
image, which takes a minute.

## WebStorm (frontend)

1. **Open** the `frontend` folder as the project — again, not the repository root.
2. **Settings → Languages & Frameworks → Node.js**, set the interpreter to your Node 24. If you use
   nvm, it is under `~/.nvm/versions/node/v24.x.x/bin/node`.
3. Run `npm install` once from the built-in terminal.
4. In the **npm** tool window, double-click `start`. WebStorm makes a run configuration for it.
5. Open http://localhost:4200.

**Tests:** double-click `test` in the npm window, or right-click a `.spec.ts` and run it. They use
Vitest with a real DOM, no browser needed.

**A trap worth knowing:** the specs run in parallel workers. A test that replaces a browser global —
`vi.stubGlobal('URL', ...)` — leaks into whatever other spec files share that worker, and which files
share a worker differs between machines. That has already cost one green local run and one red CI
run. Patch the property you need and put it back; do not replace the global.

## Both at once

Two terminals is the simplest thing that works:

```bash
# terminal 1
cd backend && ./mvnw spring-boot:run

# terminal 2
cd frontend && npm start
```

The frontend tolerates the backend not being up yet — it shows a failed bootstrap and a retry — so
the start order does not matter.

## What is in the demo school

| | |
|---|---|
| School | `DEMO-001` — Chalkbase Demo Public School, CBSE, Nagpur |
| Session | the current Indian school year, April–March, marked current |
| Ladder | Nursery, LKG, UKG, Class 1–8 — 11 classes, sections A and B each |
| Students | 60, spread across the sections with roll numbers, ages matching the rung |
| Guardians | 52 records for 60 children — **because siblings share one** |

The guardian numbers are the interesting part. Sunil Kulkarni is one record linked to three children;
Amitava Bose to three; Latha Nair and two others to two each. One student, Kavya Iyer, has both a
father and a mother; one, Ansh Mishra, has no guardian at all. Some numbers are stored `+91 98450
10002` and others `98450 10001`, so the phone search has both shapes to match.

## Checking it worked

Sign in as the principal and you should see the menu build itself from the server: Schools,
Students, Academics, Settings. Then:

- **Students → the list** has the seeded children with their class and section.
- **Open one** and the guardians are there. At least three families share one guardian record between
  siblings — change that guardian's phone on one child and it changes for the others, which is
  [ADR-0020](../architecture/adr/0020-student-and-guardian-model.md) §5 working.
- **Academics → Classes** shows the ladder in order, with move-up and move-down.
- **Sign in as the auditor** instead and the Audit log appears in the menu — it is not there for the
  principal, because reading who did what is oversight rather than a convenience.
- **Sign in as the account with a forced password change** and you are sent to the change screen
  before anything else loads.

## When it does not work

**`FATAL: password authentication failed`** — the password is wrong or not exported. If you are using
Supabase, note that the direct host `db.<ref>.supabase.co` resolves to **IPv6 only**, which most
machines including WSL2 cannot reach. Use the pooler host on port 5432 as the example config does.

**`Node.js version v20.x detected`** — Angular 22 needs ≥ 22.22.3. `nvm use 24`.

**Testcontainers tests fail immediately** — Docker is not running.

**The app starts but the menu is empty** — you signed in as an account with no role grant. The demo
seeder grants roles; a hand-made account has none.

**Port 8080 already in use** — something is still running from last time.
`ss -lptn 'sport = :8080'` names the process. Do not `pkill -f spring-boot` — the pattern matches
your own shell and kills the terminal.
