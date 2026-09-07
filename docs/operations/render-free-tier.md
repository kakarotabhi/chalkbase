# The Render dev environment on the free tier

The personal dev environment described in [status.md](../status.md) runs on Render's free plan:
`chalkbase-api` as a Docker web service, `chalkbase-web` as a static site, both in Singapore, with
the database still on Supabase in Seoul ([render.yaml](../../render.yaml) explains why). This page
is the operational half — what the tier actually costs, and the one thing you have to do before you
push a deploy.

None of it applies to the Coolify/VPS deployment that
[ADR-0015](../architecture/adr/0015-deployment-baseline.md) names as the production baseline. It is
here because it is a real environment people deploy to, and the failure mode below has already
wasted an evening.

## Wake the service before you trigger a backend deploy

**A backend deploy times out when the service has been asleep for hours, and succeeds when it is
warm.** Hitting `/actuator/health` first is a real workaround, not superstition.

Three deploys, all with an identical Docker build, measured from the Render deploy log:

| Deploy | State of the service before it | `==> Deploying...` → Spring Boot banner | Outcome |
|---|---|---|---|
| 19:05 | an instance already running | **24 s** | live |
| 04:02 | shut down 5 h 37 m earlier (last log line 22:25:41) | **13 min 14 s** | `==> Timed Out` at exactly 15 min 00 s |
| 04:35 | verified warm — health check answered 200 in 1 s immediately before | **24 s** | live, 5 min 11 s end to end |

The timeout is a fixed 15-minute deploy budget. On the 04:02 deploy Tomcat initialised **four
seconds** before Render gave up, so the container was moments from passing its health check.

**The Docker build is not the variable.** Every layer was `CACHED` and the build step finished in
14 s in all three cases. The only thing that differed was whether a machine was already running the
container.

### What is evidence and what is inference

Render emits **no log lines at all** during that 13-minute gap. Pulling the window with a 500-line
limit returned 79 lines, all clustered at the two ends. So nothing in any log directly shows what
Render was doing.

What is evidenced is the controlled comparison in the table: same commit, same cached image, same
15-minute budget, and the single variable is a running instance versus a cold one. The inference —
that a cold free instance spends the gap waiting on Render to schedule and pull, before the JVM ever
starts — is the only explanation consistent with those three runs, but it is an inference. Do not
repeat it as something a log proved.

### The consequence that bites

The deploy that timed out left **the backend on the previous commit while the frontend static site
had already gone live on the new one.** The two services deploy independently and neither knows the
other failed, so a timed-out backend deploy is a silent version skew: the app is serving new
JavaScript against an old API. Check both services after any deploy that did not report live, not
just the one you were watching.

### The routine

```bash
curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' \
  https://chalkbase-api.onrender.com/actuator/health
```

Repeat until it answers `200` quickly — the first call after a long idle takes 86–121 s (below).
Then trigger the deploy. Then confirm both services went live.

## Why startup takes about 257 s

Asked once, so written down. Spring context startup on a free instance, four measurements:
**257.2 s, 257.9 s, 256.8 s, 261.1 s.** Two things account for most of it.

**The tier gives the process a fraction of one CPU.** Render's own deploy log prints
`==> Setting WEB_CONCURRENCY=1 by default, based on available CPUs in the instance`. The Dockerfile
already concedes the point with `-XX:+UseSerialGC` — a parallel collector on one core costs more
than it saves. Everything below is wall-clock on that machine, not work that would take this long
anywhere else.

**Spring Modulith's runtime module scan is the largest single gap.** `spring-modulith-runtime` is
`scope=runtime` in [backend/pom.xml](../../backend/pom.xml), which puts ArchUnit on the production
classpath. At boot it builds an `ApplicationModules` model by scanning every class in the
application. In the log, ArchUnit's `PluginLoader` fires on thread `cTaskExecutor-1` and the next
line is `Tomcat initialized` **59 seconds later.**

That 59 s is the largest gap in the startup and it has a strong mechanism behind it, but it is a
correlation rather than a proven attribution: the scan runs on an async executor, so it is not
formally on the critical path. On a fraction of a CPU an async scan still costs wall-clock, because
there is no second core for it to run on.

The rest is ordinary: JVM start, Spring context, Hibernate, and the per-tenant Flyway pass below.

### The Modulith dependencies stay. This is settled.

Removing `spring-modulith-runtime`, `spring-modulith-actuator` and `spring-modulith-observability-*`
from the runtime classpath was proposed, and **the product owner decided they stay.** The module
artifacts remain and the ~59 s is a known and accepted cost of
running on a free instance. It is written down here so nobody re-proposes it as a discovery.

(An earlier version of this paragraph said the decision keeps `/actuator/modulith` working. It does
not: only `health` and `info` are exposed, so that endpoint answers 404 either way. The reason to
keep the dependencies is the module model itself, not an endpoint nobody can reach.)

One lead is worth checking someday and is **unverified**: Spring Modulith can in principle compute
the module metadata at build time rather than scanning at boot, which would keep every dependency
and remove the scan. Nobody has checked whether that is wired up in 2.1.x. Do not act on that
sentence without confirming it first.

## The numbers

All measured from Render logs on 2026-09-07, against the Supabase database in Seoul.

| | Measured |
|---|---|
| Spring context startup | **~257 s** — 257.2, 257.9, 256.8, 261.1 |
| Per-tenant Flyway pass at startup | **7,955 ms** and **8,081 ms**, one figure per school — `qa_sandbox` now exists alongside `demo_school`, so this runs twice |
| First request after idle | **86 s, 96 s, 121 s** to a 200 — call the range ~86–121 s, not "about ninety seconds" |
| Docker build, all layers cached | 14 s |
| Warm deploy, `Deploying...` to live | 5 min 11 s |
| Render's deploy timeout | 15 min 00 s |

The migration figure is the one that matters beyond this environment. At ~8 s per school, fifty
schools would be close to seven minutes of every cold start, which is what makes
[ADR-0011](../architecture/adr/0011-schema-per-tenant.md)'s recorded expiry — *move migration out of
startup into a deploy step* — a concrete date rather than a good intention. Startup already exceeds
the one-minute trigger the ADR names.

## The staging pair

A second API and web service, tracking the `staging` branch, added so a feature can be verified
*running* before it reaches `main`. Same free tier, same region, same `prod` profile — and
therefore the same wake and deploy behaviour as everything above. `ops/render/warm-and-deploy.sh
staging` is the routine, scripted.

| | |
|---|---|
| App | <https://chalkbase-web-staging.onrender.com> |
| API | <https://chalkbase-api-staging.onrender.com> |
| Database | a **second** Supabase project, `chalkbase-staging`, in `ap-southeast-1` |

### Why it has its own database

This is the part that is not optional, and it is the reason staging took a decision rather than a
checkbox.

The dev services above and every developer's `local` profile share one Supabase project.
`TenantMigrations` fans out at startup against every school's schema with Flyway's defaults —
`validateOnMigrate=true`, `outOfOrder=false`. A branch deployed against that database applies its
unmerged migration to the real `demo_school` and records its checksum; the moment the branch edits
that migration again, which is normal while the work is in progress, **every other backend refuses
to start on a checksum mismatch**, including yours, and the fix is manual.

[parallel-work.md](../development/parallel-work.md) records that as the larger of the two migration
hazards. Deploying branches would turn it from a hazard into a routine. So staging points at its
own project, and the dev environment is left alone.

It is in Singapore rather than Seoul, beside the Render services, because the per-tenant Flyway
pass at startup is latency-dominated — ~8 s per school against Seoul from a free instance. Staging
is deployed far more often than dev, so the round trip is worth shortening. It does mean staging is
*faster* than dev by construction: do not read performance numbers off it.

### Setting it up

The Blueprint in [render.yaml](../../render.yaml) defines all four services, so Render creates the
staging pair on the next Blueprint sync. Four values are `sync: false` and have to be entered on
`chalkbase-api-staging` once:

| Variable | Where it comes from |
|---|---|
| `SPRING_DATASOURCE_URL` | Supabase → the `chalkbase-staging` project → **Connect** → **Session pooler**, rewritten as `jdbc:postgresql://…`. **Not** port 6543 in transaction mode — ADR-0011 hands each connection a `search_path` per school, and a pooler that hands out a different backend per statement breaks tenancy. |
| `SPRING_DATASOURCE_USERNAME` | the user from that same connection string |
| `SPRING_DATASOURCE_PASSWORD` | the database password set when the project was created |
| `CHALKBASE_SETUP_KEY` | `openssl rand -base64 32`. Without it the service refuses to start, deliberately: `POST /api/schools` creates a PostgreSQL schema and is `permitAll`, and a staging URL is no less public than any other. |
| `CHALKBASE_ENCRYPTION_KEY` | `openssl rand -base64 32`, and a **different** key from the dev environment's (ADR-0022). A staging key is handled more loosely than a real one; sharing one makes that looseness the real key's problem. |

The database starts empty and does not need seeding by hand. The shared migrations create
`public.school` and the session store at first boot; a school is then created through
`POST /api/schools` with the `X-Chalkbase-Setup-Key` header, and the per-tenant migrations run for
it. Use a school code that cannot be confused with the dev one.

### Using it

```bash
git checkout staging && git merge --no-ff <feature-branch> && git push
ops/render/warm-and-deploy.sh staging
```

Then verify in a browser, and only then open the merge to `main`. Two things carry over from the
dev environment unchanged: **warm before you deploy**, and **check both services afterwards** — a
timed-out backend deploy beside a successful frontend one is a silent version skew, not a visible
failure.
