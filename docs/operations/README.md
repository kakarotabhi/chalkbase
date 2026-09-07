# Operations

Deployment target: a self-hosted VPS running [Coolify](https://coolify.io/docs/). See `ops/`.

| Page | Read it when |
|---|---|
| [The Render dev environment on the free tier](render-free-tier.md) | deploying to Render, or explaining why a cold start takes four minutes |
| [The encryption key](encryption-key.md) | generating, setting, or backing up `CHALKBASE_ENCRYPTION_KEY` (ADR-0022) |

## Runbooks to write before the first school goes live

- [ ] Deploy and rollback (Coolify application + database service)
- [ ] Backup and restore — cluster PITR **plus** per-school logical export
      ([ADR-0011](../architecture/adr/0011-schema-per-tenant.md) makes per-school export a
      `pg_dump -n <schema>`)
- [ ] Encryption key backup — see [the encryption key](encryption-key.md); unlike the rest of this
      list, there is no restore procedure if this one is skipped
- [ ] Database migration failure recovery
- [ ] Certificate renewal
- [ ] Incident response and parent/school communication
- [ ] Data subject requests under the DPDP Act — export and erasure

## Health

| Endpoint | What |
|---|---|
| `/actuator/health` | liveness and readiness |
| `/actuator/info` | build and version |

Those two are all that is exposed — `management.endpoints.web.exposure.include` is `health,info`,
and the application logs `Exposing 2 endpoints` at startup. `/actuator/metrics` and
`/actuator/modulith` answer **404**; they were listed here while the application was still
scaffolding and were never enabled. Adding one is a deliberate act, because an actuator endpoint on
a public URL is an information disclosure until it is authenticated.

Authentication and authorization are built ([ADR-0003](../architecture/adr/0003-authentication-and-authorization.md),
[ADR-0005](../architecture/adr/0005-authorization-model.md)) and a public deployment is documented
in [status.md](../status.md). This page previously said `SecurityConfig` "still permits every
request" and warned against deploying publicly; that stopped being true some time ago. What
`SecurityConfig` does now is match `/api/**` as `authenticated()` **before** the closing
`anyRequest().permitAll()`, so that last rule reaches only static assets, the error dispatch and
the OpenAPI files — nothing under `/api` gets to it. Per-endpoint permissions are `@PreAuthorize`
on the controller, and `ControllerAuthorizationTests` fails the build for an endpoint that carries
no annotation.

The genuine caveats are narrower and are recorded in [status.md](../status.md): a session survives
its account being disabled or locked. `/api/schools/**` is not one of them any more — `GET`/`POST
/api/schools` require `school:school:create`, which no shipped role holds, so they were never
reachable; `POST /api/schools/bootstrap`
([ADR-0024](../architecture/adr/0024-bootstrap-deployment.md)) is the one endpoint under that path
genuinely open by design, with `SetupKeyFilter` guarding it on `prod` and its own refusal once a
school already has an administrator.

The database is PostgreSQL 17 ([ADR-0004](../architecture/adr/0004-h2-now-postgresql-next.md)).
Development uses a hosted Supabase instance in `ap-northeast-2`; production belongs in `ap-south-1`
(Mumbai) and that move means a new project, so decide before there is data worth migrating.
