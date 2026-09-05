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

## Before the first real deployment

- [ ] `SecurityConfig` no longer permits every request ([ADR-0003](../../docs/architecture/adr/0003-authentication-and-authorization.md))
- [ ] PostgreSQL replaces H2, with RLS policies in place ([ADR-0004](../../docs/architecture/adr/0004-h2-now-postgresql-next.md))
- [ ] Automated backups configured and a restore actually tested
- [ ] `management.endpoints.web.exposure.include` trimmed to `health,info`
- [ ] TLS terminated by Coolify's proxy, HTTP redirected
