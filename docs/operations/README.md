# Operations

Deployment target: a self-hosted VPS running [Coolify](https://coolify.io/docs/). See `ops/`.

## Runbooks to write before the first school goes live

- [ ] Deploy and rollback (Coolify application + database service)
- [ ] Backup and restore — cluster PITR **plus** per-school logical export
      ([ADR-0002](../architecture/adr/0002-multi-tenancy-strategy.md) makes per-school restore the
      hard case)
- [ ] Database migration failure recovery
- [ ] Certificate renewal
- [ ] Incident response and parent/school communication
- [ ] Data subject requests under the DPDP Act — export and erasure

## Health

| Endpoint | What |
|---|---|
| `/actuator/health` | liveness and readiness |
| `/actuator/metrics` | JVM and HTTP metrics |
| `/actuator/modulith` | module structure at runtime |

Nothing here is production-ready yet: `SecurityConfig` still permits every request
([ADR-0003](../architecture/adr/0003-authentication-and-authorization.md)). Do not expose a
deployment publicly until that is closed.

The database is PostgreSQL 17 ([ADR-0004](../architecture/adr/0004-h2-now-postgresql-next.md)).
Development uses a hosted Supabase instance in `ap-northeast-2`; production belongs in `ap-south-1`
(Mumbai) and that move means a new project, so decide before there is data worth migrating.
