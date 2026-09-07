# ADR-0028: Expired sessions are already purged — no second job

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0003](0003-authentication-and-authorization.md) (server-side sessions in
  PostgreSQL), [ADR-0011](0011-schema-per-tenant.md) (schema-per-tenant, and why
  `public.spring_session` is the one table that sits outside it), [ADR-0026](0026-audit-retention-purge.md)
  (the purge job this ADR was expected to imitate)

## Context

`docs/status.md` has carried "Expired sessions are never purged" under Known gaps and debt since
sessions shipped: `public.spring_session` grows without bound, one row per login, and nothing in
this codebase ever deletes one. [ADR-0026](0026-audit-retention-purge.md) landed a purge job for
`audit_event` shortly before this was picked up, and the obvious plan was to copy its shape —
`TaskScheduler`, a cron property, an enable/disable tripwire, batched deletes — for sessions too.

Two things about `spring_session` make that copy wrong before a line of it is written:

1. **It is not per tenant.** `spring_session` lives in `public`
   (`V2026_09_05_1900__identity_create_session_store.sql`), one table shared by every school,
   because a session is bound to an HTTP connection that has not yet chosen a schema. Fanning out
   over `TenantRegistry.activeSchemas()` the way `AuditRetentionPurgeJob` does would run the same
   delete once per school against the same rows — harmless beyond the wasted work, but not what the
   audit job's shape is _for_.
2. **Spring Session already ships a purge**, and it does not need `chalkbase`'s scheduling
   infrastructure at all to run. Reading `JdbcIndexedSessionRepository` (via
   `spring-session-jdbc`'s bytecode, since the dependency ships no ADR-legible source in this
   repository's toolchain) rather than assuming from the property name: unless
   `spring.session.jdbc.cleanup-cron` is `-`, `afterPropertiesSet()` builds its own
   `ThreadPoolTaskScheduler`, initializes it, and schedules `cleanUpExpiredSessions()` — a
   transactional `DELETE ... WHERE expiry_time < ?` against whichever `table-name` is configured —
   with a `CronTrigger` built from that same property. `JdbcSessionAutoConfiguration` wires the
   property onto the repository through a `SessionRepositoryCustomizer`, and `DisposableBean` tears
   the scheduler down on shutdown. None of this touches `@EnableScheduling` or the application's own
   `TaskScheduler` bean (`AuditRetentionSchedulingConfiguration`) — it is Spring Session's own
   machinery, self-contained, on by default.

The default for both the Boot property and the class's own constant is `0 * * * * *` — every
minute — and nothing in this application's configuration set it to `-` or to anything else. So the
purge this gap describes has been running since the session store shipped. **The line in
`docs/status.md` was wrong, not merely stale**, per the exception `docs/development/parallel-work.md`
carves out for a status row that states a fact about the project rather than a preference.

## Options considered

1. **Build a session-retention job that mirrors `AuditRetentionPurgeJob`.** Rejected: it would
   duplicate a delete Spring Session already runs, on the same table, and — done carelessly — the
   `TenantRegistry` fan-out that job's shape encourages would run it once per school against a table
   that has no per-school rows to distinguish.
2. **Do nothing beyond correcting the docs.** Close, but leaves the actual schedule — every minute,
   forever — undiscoverable without reading vendor bytecode, which is exactly the situation that
   produced this ADR's mistaken starting assumption in the first place.
3. **Make the existing default explicit in `application.yml`.** Chosen.

## Decision

**No second job.** `backend/src/main/resources/application.yml` now states
`spring.session.jdbc.cleanup-cron: '0 * * * * *'` explicitly, next to `table-name` — the value
changes nothing at runtime, since it is the vendor default already in effect, but it turns "ask the
`spring-session-jdbc` jar" into "read this file" for the next person who hits the same gap, the same
reason [ADR-0026](0026-audit-retention-purge.md) §1 put the audit retention period in configuration
rather than a constant. The comment records both facts this ADR exists to preserve: that the
cleanup is Spring Session's own, running on its own scheduler independent of
`AuditRetentionSchedulingConfiguration`, and that it needs no tenant fan-out because
`public.spring_session` needs none.

Setting it to `-` remains the operator's way to disable it without a redeploy, the same tripwire
shape `chalkbase.audit.retention.enabled` gives the audit purge — no `chalkbase.*` property is added
for it, because it is not this codebase's job to run or to own.

## Consequences

- `docs/status.md`'s "Expired sessions are never purged" line is corrected, not merely reworded: the
  fact was false, and the gap it described does not exist.
- `spring_session` still has no application-level test proving the delete actually fires — this ADR
  is based on reading `JdbcIndexedSessionRepository`'s bytecode against the configured property, not
  on an integration test watching a row disappear. That is a legitimate follow-up if a future
  incident ever makes this doubted again, but is not treated as a live gap: the mechanism is
  vendor-owned, exercised across the whole Spring Session user base, and outside what this
  codebase's own test suite exists to prove.
- Anyone reaching for `AuditRetentionSchedulingConfiguration` as a template for a future scheduled
  job should still read it that way — this ADR does not weaken that precedent. It only says a
  session-specific copy of it was never needed.
