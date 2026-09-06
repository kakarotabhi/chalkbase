# ADR-0018: The audit log

- Status: Accepted
- Date: 2026-09-06
- Deciders: Raja
- Related: [ADR-0011](0011-schema-per-tenant.md) (tenancy), [ADR-0014](0014-data-classification.md) (classification), [ADR-0012](0012-fee-ledger-model.md) (append-only money)

## Context

FR-008 is a P0 and a Phase 1 exit criterion: a complete audit log for create, update, delete,
approval, login, export and publish. Its acceptance note is more specific than the requirement —
every privileged action traceable to **user, role, timestamp, IP/device where available, and changed
fields**.

Audit is being built now rather than later for the same reason the data-classification test was: it
is cheap while there are three modules and close to impossible to backfill honestly across sixteen.
A retrofitted audit log is a log of the changes someone remembered to instrument.

## Decisions

### 1. One generic table per tenant, not Envers

Hibernate Envers is the obvious answer and is the wrong one here. It creates an `_AUD` table per
audited entity, and [ADR-0011](0011-schema-per-tenant.md) already names table count as the ceiling
of schema-per-tenant: sixty tables across five hundred schools is thirty thousand. Envers would
roughly double that to buy a per-entity history we do not need.

A single `audit_event` table per tenant records every action in one shape. It costs one table per
school instead of sixty.

### 2. Field names are recorded. Values are not

The acceptance note asks for "changed fields", and that is what is stored — which field changed, not
what it changed to.

This is not a shortcut, it is [ADR-0014](0014-data-classification.md) applied. Restricted and
Confidential data is "never logged", and an audit table holding before-and-after values of every
edit is a complete, unencrypted, permanently retained second copy of every student record in the
school. The audit log would become the largest concentration of children's data in the system, and
the one nobody thinks of as a database.

Where the previous value genuinely matters, the domain already carries it and the audit log is not
the mechanism: money is append-only with reversals rather than edits
([ADR-0012](0012-fee-ledger-model.md)), and anything else that needs a before-state needs versioning
in its own model, deliberately, with its own classification and retention.

**As built, no value is recorded at all** — not even of an `INTERNAL` or `PUBLIC` field.
`AuditService.recordChange` rejects anything in `changedFields` that is not a plain field name, so
`"phone"` is accepted and `"phone=9876543210"` throws before a row is written. The rule is
mechanical rather than remembered, which is what makes it hold.

A narrower rule — values permitted for `PUBLIC` and `INTERNAL` fields — is defensible and was
deliberately not built, because it needs the `@Classification` annotation from
[ADR-0014](0014-data-classification.md), which does not exist yet. Until something can *decide* a
field's classification at runtime, "values of unclassified fields are fine" is a rule with no way to
tell which fields those are. Relaxing this later is additive; discovering that a school's audit
table has been accumulating phone numbers is not.

### 2b. A count is not a value (amended 2026-09-06)

`audit_event.record_count` holds how many records a bulk action touched, and is null for the
single-record actions that are almost all of them.

This does not weaken §2. That rule keeps personal data out of the log: what is refused is the
*value a field took*, because those values are a child's name and date of birth. A row count is not
a value of any field and identifies nobody — it is a property of the event, like `occurred_at` and
`outcome`.

It has a column because the alternative was worse. Writing the count into `changed_fields` as
`imported_600` would pass the field-name regex, and would be smuggling a value past a check built to
stop exactly that. An agent building the student import declined to do it and asked instead, which
is the right instinct: a rule that can be satisfied by disguising the thing it forbids is not
holding.

Without it, the audit row for an import says who imported into which year and not whether that was
three students or six hundred — and reconstructing the number afterwards by counting `created_at`
timestamps is the forensic work an audit log exists to spare somebody.

### 3. A data change is audited in the SAME transaction as the change

If the transaction rolls back, the audit row goes with it. An audit log that records changes which
did not happen is worse than one with gaps, because it cannot be reconciled against the data.

### 4. A security event is audited in its OWN transaction

Sign-in, failed sign-in, lockout, permission denial, and an unmasking or export of protected data
are recorded whether or not the surrounding work succeeded. **A failed login must be recorded
precisely because it failed** — the same reasoning that already puts the failed-attempt counter in
its own transaction in `AuthenticationService`.

These two rules look inconsistent and are not. A data-change audit answers "what is the history of
this record", so it must match the record. A security audit answers "what did someone attempt", so
it must survive the attempt failing.

### 5. It lives in the tenant schema

The audit log is the school's record of itself, and belongs to the school's data — including for
export and erasure. The single exception under [ADR-0011](0011-schema-per-tenant.md) remains
sessions, and this is not a second one.

A failed sign-in is still attributable to a school, because the school code is resolved before
authentication is attempted. A sign-in attempt against an unknown school code has no tenant to
write to and is not recorded; that is a platform-level concern for later, not a hole in a school's
audit trail.

### 6. Reading the audit log is a permission, and the Auditor role finally has one

`platform:audit:read`. The shipped `AUDITOR` template holds no permissions today, which is honest
but useless; this is its first and, for now, only one.

The audit log is append-only in the application: there is no update or delete endpoint, ever. A
retention purge is a scheduled platform job, not an API.

## Shape

```
<tenant>.audit_event
  id, occurred_at, actor_id, actor_name, actor_roles,
  action, entity_type, entity_id, changed_fields,
  outcome, ip_address, user_agent, trace_id
```

`actor_name` and `actor_roles` are **snapshots**, deliberately denormalised. An audit row must still
read correctly after the account is renamed, its roles changed, or the account deleted — a foreign
key to a mutable row would let the past change.

`trace_id` is the same one the [ADR-0007](0007-api-response-envelope.md) envelope returns, so a
parent quoting a trace id from an error screen leads directly to the audited action.

## Consequences

- **Every module must emit audit events**, and nothing structural forces it to. This is the weak
  point of the decision. The mitigation is that write endpoints are few and reviewed; a test in the
  shape of `ControllerAuthorizationTests` — asserting that every mutating handler is audited — is
  the real answer and should follow once there are enough of them to be worth it.
- **The table grows without bound** until a retention job exists. Attendance-scale volume it is not,
  but a purge is required before the first school runs a full session.
- **Retention is unset.** [ADR-0014](0014-data-classification.md) requires every category to carry a
  period running from an event. Audit retention is a legal question, not an engineering one, and is
  the one open item here: Indian financial-record convention suggests seven years, but this needs
  confirming against DPDP obligations and the board's own requirements before the first deployment.
- **IP address is itself personal data** under the DPDP Act. It is recorded because the acceptance
  note asks for it and because it is what makes an intrusion investigable, and it inherits the
  audit log's retention rather than being kept forever.
