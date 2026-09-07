# ADR-0026: Audit retention — the period, and how the purge runs

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0018](0018-audit-log.md) (the audit log itself, and the "retention is unset" gap this
  closes), [ADR-0014](0014-data-classification.md) (a period per category, and why one number
  satisfies it), [ADR-0011](0011-schema-per-tenant.md) (the tenant fan-out this purge follows)

## Context

ADR-0018 built the audit log and left retention as its one open item: "Indian financial-record
convention suggests seven years, but this needs confirming against DPDP obligations and the board's
own requirements." `docs/status.md` carried that forward as a legal question rather than an
engineering one.

**The product owner has now answered it: seven years, one period for every classification tier.**
ADR-0014 asks for a period per category (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`); a
schedule per category was considered and rejected in favour of a single uniform number, because a
uniform number errs long and long is the safe direction for a log whose purpose is proving what
happened. A per-category schedule stays possible later — nothing in the schema or the purge assumes
there is only ever one period, so splitting it is additive.

With the number settled, what was missing was the purge itself: `audit_event` has grown without
bound since ADR-0018 shipped, and nothing removed a row from it.

## Decision

### 1. Seven years, configurable, not a literal

`chalkbase.audit.retention.years` in `application.yml`, defaulting to `7`. Visible in configuration
rather than buried in a constant, so changing it is a deploy configuration and not a code change —
and so the default itself is discoverable by reading the file rather than the source.

### 2. A scheduled platform job, never an endpoint

`AuditController` has no write method, by design (ADR-0018 §6): "an audit log an administrator can
edit is a log that says whatever the last person to be embarrassed by it wanted it to say." A purge
endpoint — even one gated behind a `platform:audit:purge` permission — would be exactly that shape:
a caller asking the log to remove rows on demand. `AuditRetentionPurgeJob.purgeExpiredEvents()` is
package-private; the only caller in production is Spring's scheduler.

`chalkbase.audit.retention.enabled` (default `true`) is the tripwire an operator has instead of an
endpoint — set to `false` in the environment, without a redeploy, to hold a run back.

### 3. It runs per tenant, the same way `TenantMigrationRunner` does

`TenantRegistry.activeSchemas()` is the same list `TenantMigrationRunner` fans out over at startup.
Each schema is bound with `TenantContext.callWith` immediately before that school's rows are touched,
and released immediately after — never inferred from whatever schema happens to be on a pooled
connection. Getting this wrong would not fail loudly: it would delete the wrong school's history, or
audit a purge that ran against nobody's schema.

Unlike the startup migration runner, one school's failure does not stop the run: a purge is
housekeeping, not a version every school must share to be servable, so most schools succeeding while
one is investigated is a fine outcome here. The failure is logged with the schema name, never with
any row content — there is none to log, since `audit_event` never held field values to begin with
(ADR-0018 §2).

### 4. Deletion is batched, 500 rows at a time

`AuditEventRepository.deleteBatchOlderThan` deletes up to `chalkbase.audit.retention.batch-size`
(default `500`) of the oldest expired rows per call, via `delete ... where id in (select id ... order
by occurred_at limit ?)` — native SQL, because JPQL has no `LIMIT` inside a subquery. Each call runs
in its own `REQUIRES_NEW` transaction (`AuditRetentionBatchExecutor`, mirroring
`AuditEventWriter`'s reason for existing as a separate bean: the transactional method has to sit one
proxy hop away from the code that binds the tenant, or a self-invocation would bypass the annotation
silently). `AuditRetentionPurgeJob` loops these calls until a batch returns fewer rows than the
batch size.

**Why 500 and not one unbounded `delete`.** A first run against a school with years of history can
be tens of thousands of rows. One transaction holding a lock on `audit_event` for however long that
delete takes is a cost paid by every other write to that schema, for the length of the purge, on a
connection pool every school shares (ADR-0011) — the same reasoning ADR-0011 already applies to
startup migration time. `idx_audit_event_occurred` (`occurred_at desc`) serves the inner `order by`,
so each batch is an index scan bounded to 500 rows rather than a sequential scan of the table; 500
finishes in a fraction of a second on that index and is large enough that an ordinarily active school
does not need thousands of round trips to catch up.

### 5. The purge audits itself, once per school per run, and cannot be recursive

`AuditAction.AUDIT_LOG_PURGED` — one row, naming how many rows were removed (`record_count`, the
same "a count is not a value" column ADR-0018 §2b already established for bulk imports) and the
cutoff instant used, never which rows. Written only when a school actually had something to purge; a
school with nothing expired gets no summary row for that run.

The actor is `AuditActor.system("Audit retention purge", schema)`, a new factory alongside the
existing `AuditActor.unauthenticated` — distinct from it, because "nobody is authenticated" and "a
trusted scheduled job is acting with no HTTP request behind it" are different facts and should not
collapse into the same blank actor.

It cannot be recursive: the summary row is written with `occurred_at` at the moment the run
completes, which is later than every row the run just deleted and earlier than the next run's
cutoff, so it is never eligible for the sweep that created it. It ages out correctly, on some future
run, once it is old enough itself — seven years from now, by default.

**If writing the summary row fails after the batches have already committed**, the deletion is not
retried and is not rolled back — it already happened, in its own already-committed transactions —
and the gap is logged at `ERROR` with the schema and the count, distinct from a genuine deletion
failure. This is a real, accepted gap rather than a solved one: the alternative (deleting inside the
same transaction as the summary write) would mean either an unbounded transaction again, or a
summary row per 500-row batch, both worse than an occasional unrecorded run that the error log still
explains.

### 6. `@EnableScheduling`, added for the first time

Nothing in this codebase used `@Scheduled` before this — `docs/status.md` named the absence, and
there was no `TaskScheduler` bean to use even if something had. `AuditRetentionSchedulingConfiguration`
adds both: `@EnableScheduling`, and a named single-thread `TaskScheduler`
(`chalkbase-scheduler-`) sized for the one job that exists today.

**This is application-wide, not scoped to this package.** `@EnableScheduling` turns on Spring's
`@Scheduled` machinery for the whole application context; any `@Scheduled` method added anywhere
else in the codebase from now on will run, against whichever `TaskScheduler` bean it resolves to.
One thread is enough for the one job here. A second scheduled job with materially different timing
needs should bring its own named `TaskScheduler` rather than share this one — sharing a single
thread across unrelated jobs is how one job's overrun silently delays another's next tick.

The default schedule is `0 30 2 * * *` (02:30 UTC daily), configurable via
`chalkbase.audit.retention.cron`, chosen to fall outside the school day in every timezone this
product ships to.

## Consequences

- `audit_event` finally has an upper bound on its size — roughly seven years of activity per school,
  where it was previously unbounded.
- The application now has scheduling infrastructure that did not exist before. The next scheduled
  job — export reminders, a digest, anything else time-driven — reuses `@EnableScheduling` for free
  and should bring its own `TaskScheduler` if its timing needs differ from this job's.
- A per-category retention schedule, if ADR-0014's per-category wording is ever acted on literally
  rather than satisfied by one uniform number, is a second `@Value`-configured period and a second
  `WHERE` clause on `deleteBatchOlderThan` — not a schema change.
- The purge is invisible to every role including the Auditor: there is no screen, no endpoint, and
  no permission for it, by design. The only visibility into whether it is running is the
  `AUDIT_LOG_PURGED` rows it leaves behind and the application logs.
