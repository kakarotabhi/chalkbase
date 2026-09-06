# Coolify deployment

Two applications plus one database service on a single VPS.

| Service | Source | Port |
|---|---|---|
| `backend` | `ops/docker/Dockerfile.backend`, context = repo root | 8080 |
| `frontend` | `ops/docker/Dockerfile.frontend`, context = repo root | 80 (public) |
| `postgres` | Coolify's managed PostgreSQL 17 | 5432, internal only |

The frontend proxies `/api/` to `backend:8080`, so only the frontend is exposed publicly and the
browser sees a single origin — no CORS configuration needed.

## Environment

Copy `.env.example` into the Coolify application's environment. Never commit real values.

`CHALKBASE_SETUP_KEY` is not optional: the `prod` profile refuses to start without it, on this
deployment as much as on Render's. It is the stopgap guarding `POST /api/schools` until
platform-operator accounts land — see `SetupKeyConfiguration`.

Render's free tier is a second, separate deployment of the same `prod` profile, described in
`render.yaml` at the repository root. It changes nothing here; ADR-0015 still names this one as the
baseline.

## Before the first real deployment

- [ ] `SecurityConfig` no longer permits every request ([ADR-0003](../../docs/architecture/adr/0003-authentication-and-authorization.md))
- [ ] PostgreSQL replaces H2, with RLS policies in place ([ADR-0004](../../docs/architecture/adr/0004-h2-now-postgresql-next.md))
- [ ] Automated backups configured and a restore actually tested
- [ ] `management.endpoints.web.exposure.include` trimmed to `health,info`
- [ ] TLS terminated by Coolify's proxy, HTTP redirected
