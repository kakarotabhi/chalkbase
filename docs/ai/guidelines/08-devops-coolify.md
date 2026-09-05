# DevOps and Coolify Guidelines

## Deployment Direction

The application is intended to run on a self-managed VPS using Coolify.

Default deployment components:

- Angular frontend container or static build served by Nginx.
- Spring Boot API container.
- PostgreSQL.
- Redis.
- S3-compatible object storage, such as MinIO, unless using an external provider.
- Backup job or VPS-level backup integration.

## Environment Rules

All environment-specific values must come from environment variables or Coolify-managed secrets.

Do not commit:

- Database passwords.
- JWT/session secrets.
- Payment gateway keys.
- SMS/WhatsApp credentials.
- SMTP credentials.
- Object storage secrets.
- Encryption keys.
- Production URLs with credentials.

Use required environment variable syntax in Compose where appropriate.

## Docker Rules

Backend container:

- Build reproducibly.
- Run as non-root where practical.
- Expose only the app port.
- Use health checks.
- Keep image small.
- Do not include local development secrets.

Frontend container:

- Build Angular assets.
- Serve static files through a web server.
- Configure API base URL safely.
- Support SPA fallback routing.
- Add cache headers deliberately.

## Docker Compose Rules

Compose should define:

- `api`
- `web`
- `postgres`
- `redis`
- `minio` if used
- optional `worker`
- optional `backup`

Rules:

- Use persistent volumes for PostgreSQL and object storage.
- Do not expose database, Redis, or MinIO admin ports publicly unless explicitly protected.
- Add health checks.
- Keep service names stable.
- Use environment variables, not committed secrets.
- Document required variables.

## Coolify Rules

When deploying through Coolify:

- Keep Compose as the source of truth for multi-container deployments.
- Let Coolify manage domains and TLS where possible.
- Use Coolify environment variables for secrets.
- Configure persistent storage before first production deploy.
- Confirm health checks work.
- Confirm logs are available.
- Confirm redeploy does not destroy volumes.
- Use staging before production.

Official docs:

- <https://coolify.io/docs/>
- <https://coolify.io/docs/knowledge-base/docker/compose>

## Database Migration Deployment

Migrations must run safely.

Recommended options:

- Run migrations during API startup only if startup failure behavior is acceptable.
- Or run a separate migration job/container before API rollout.

Rules:

- Do not run destructive migrations without backup.
- Do not edit already-applied migrations.
- Make risky migrations backward-compatible.
- Backup before production schema changes.

## Backup Requirements

Production backup must include:

- PostgreSQL database.
- Object storage files.
- Environment/configuration inventory.
- Docker Compose/deployment configuration.

Backup rules:

- Automate daily backups at minimum.
- Store offsite copy.
- Encrypt where practical.
- Monitor backup success.
- Test restore.
- Document restore commands.

Recovery goals for MVP:

- RPO: 24 hours or better.
- RTO: 4 to 8 hours or better.

## Observability

Add:

- Structured application logs.
- Request IDs.
- Health endpoints.
- Readiness checks.
- Job failure logs.
- Payment webhook failure alerts.
- SMS/email failure metrics.
- Backup success/failure alerts.

Use stdout/stderr logs for containers. Do not write important logs only inside ephemeral container files.

## Production Safety

Before production launch:

- HTTPS enabled.
- CORS restricted.
- Admin password changed.
- Default demo users disabled.
- Database not publicly exposed.
- Redis not publicly exposed.
- Object storage bucket privacy verified.
- Payment webhook URL configured.
- SMTP/SMS/WhatsApp tested.
- Backup and restore tested.
- Timezone configured.
- Server disk usage alerts configured.

## Rollback Rules

- Keep previous image available.
- Know whether migration is backward-compatible.
- Do not roll back app code blindly after irreversible migration.
- Preserve logs during incident response.
- Document manual correction steps if data was changed.

## VPS Sizing Guidance

For an MVP school installation:

- Start with enough RAM for PostgreSQL, API, frontend, Redis, and object storage.
- Monitor CPU, memory, disk, and I/O.
- Store uploaded files on a volume with room to grow.
- Plan offsite backup bandwidth.

Scale vertically first for single-school VPS deployments. Consider separate database/object storage only when load or backup needs justify it.

