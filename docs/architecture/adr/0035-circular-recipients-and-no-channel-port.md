# ADR-0035: A circular recipient is a student id, and in-app delivery does not go through a channel port

- Status: Accepted
- Date: 2026-09-09
- Deciders: Raja
- Related: [ADR-0013](0013-external-provider-ports.md) (the provider ports this ADR declines to use),
  [ADR-0014](0014-data-classification.md) (classification and the permission this module keeps its
  own), [ADR-0017](0017-identity-model.md) (a guardian is a person record, not an account),
  [ADR-0030](0030-attendance-grain-and-lock.md) (the "fixed at creation" precedent this one borrows),
  [08-phase-2-scope.md](../../requirements/08-phase-2-scope.md) (the trap this module was briefed
  against)

## Context

Phase 2's communication slice is circulars and notices: compose one, target it by class or section,
publish it, track who it reached and whether they acknowledged it (FR-100, FR-101, FR-104). Two
questions had no existing answer in the codebase and could not be deferred to "whoever builds the
parent portal":

1. **What is a recipient record, today?** FR-101 wants "delivery status" tracked per recipient. The
   obvious reading — a row in a parent's inbox — does not exist as a concept yet:
   [ADR-0017](0017-identity-model.md) makes a guardian a person record, not an account, and an
   account is created only when a guardian actually needs portal access. `ScopeType.WARD` exists on
   `AccessScope` and is deliberately unassignable; nothing resolves it into a query filter yet. So
   "delivered to a parent" cannot be built truthfully in this slice, and the scope brief for this
   module says so explicitly. What can be built, and what a recipient record should mean today so
   that it becomes the real thing later without rewriting history, needed a decision.

2. **Does an in-app circular go through [ADR-0013](0013-external-provider-ports.md)'s
   `NotificationChannel` port?** That ADR defines the port for exactly this kind of thing —
   messaging — and declares `SMS`/`WHATSAPP` as channel types with no adapter. It would be easy to
   add `IN_APP` as a third type and register a trivial adapter that writes a row. The brief for this
   module explicitly warned against a stub pretending to be a real channel.

## Decision

### A recipient record is keyed by student id, not a guardian account

`circular_recipient` carries `student_id` — a plain `uuid` with a database foreign key to `student`,
the same shape `attendance_mark.student_id` already uses for a cross-module reference with no Java
association. It does **not** carry a `guardian_id` or a `user_account` id for "who this was sent to."

Reasoning: a student id is permanent and exists from admission, regardless of whether any guardian
of that student ever has an account. When the parent portal ships and a guardian's account resolves
`ScopeType.WARD` against the students they guardian, the future parent inbox query is exactly:

```
select * from circular_recipient where student_id in (<ids resolved from the signed-in guardian>)
```

That is a new read path — a `CircularRecipientRepository` method, and a permission check for the
`WARD` scope once it resolves to something — not a new column and not a migration. The row this ADR
creates today is the same row that query reads tomorrow.

`section_id` on the same row is fixed at publish time, the same "fixed at creation" choice
[ADR-0030](0030-attendance-grain-and-lock.md) makes for `attendance_mark.section_id`: a student
moved to another section next term must not rewrite which section a past circular reached them
through.

**What "delivered" means today, honestly:** the row existing. There is no queue, so
`delivered_at` is set to `now()` at the moment `CircularPublishingService` creates the row, inside
the same transaction as the publish. This is not a placeholder for a future "real" delivery status —
it is what in-app delivery _is_: the data already lives in this system, and there is nothing for it
to wait for.

**What "acknowledged" means today, honestly:** a staff member recorded that a family acknowledged
the circular — by phone, in writing, or in person — through `acknowledged_by` (a `user_account`,
today always staff) and an optional `acknowledgement_note`. This is not a fiction standing in for a
parent's own action; it is a real, useful feature on its own (FR-104's "consent forms and
acknowledgements" is exactly this in a paper-and-phone school office today) and it is gated by its
own permission, `communication:circular:acknowledge`, separate from composing and publishing. The
column is unchanged in shape the day a guardian's own account can call the same endpoint for
themselves — only who is allowed to call it changes.

`viewed_at` exists on the table with no write path in this build, the same "table shape, no write
path yet" choice [ADR-0030](0030-attendance-grain-and-lock.md) makes for `attendance_mark`'s
period-wise columns. It is where a real read receipt lands once a parent-facing screen exists to set
it.

### No `NotificationChannel` port for in-app delivery

[ADR-0013](0013-external-provider-ports.md)'s port exists for delivery that happens **outside** this
system and can fail **independently** of it — an email that bounces, an SMS the carrier drops —
which is exactly why that model is queued, asynchronous, and carries `QUEUED`/`SENT`/`FAILED`/
`UNSUPPORTED_CHANNEL` states. An in-app circular is not delivered anywhere outside the database this
request already committed to: `circular_recipient` is written directly by
`CircularPublishingService.publish` and read directly by a screen, in the same request/response
cycle a client already expects. There is nothing to queue and no independent failure mode to
represent, so a port here would have exactly one implementation with no second failure state to
justify the abstraction it introduces — an `IN_APP` adapter that does nothing but `INSERT`, which is
the "stub pretending to be email" this module was explicitly told not to build.

This is not a rejection of ADR-0013's model. When email or web push adapters land (the two ADR-0013
already commits v1 to), they will consume `circular_recipient` rows as their own send queue's
source — a call made **from** this module **to** `platform`'s `NotificationChannel` port, using the
`delivered_at`/`viewed_at` columns this table already has. That is forward-compatible with today's
shape and needs no reshaping; it is simply not built yet, because nothing in this Phase 2 slice
needs it built.

### The targeting permission is this module's own

`communication:circular:read` gates the target-preview endpoint — "how many students would this
class or section reach" — independently of `academics:class:read` or `student:student:read`, and
never bundled with a permission `fee` or `attendance` defines. `docs/requirements/08-phase-2-scope.md`
names the failure mode directly: targeting by fee or attendance status will be Confidential-adjacent
under [ADR-0014](0014-data-classification.md) the moment those dimensions are added, and a role that
can compose circulars must not gain a defaulter list as a side effect of that ability, nor should a
role with some other module's read permission gain the targeting query for free. Class/section
targeting is not itself as sensitive as fee or attendance status would be, but the boundary is drawn
now, before there is a second, more sensitive dimension to target by — moving it later would mean
finding and re-gating every caller that grew to depend on the wrong permission.

## Options considered, for the recipient question

1. **No recipient row until a parent portal exists; only aggregate counts.** Rejected — this is
   closer to a mailing list than the module map's own trap describes: "142 sent" with no per-student
   record cannot support FR-104's acknowledgement tracking, which is necessarily per person.
2. **A recipient row keyed by guardian, creating a dormant guardian record's account eagerly.**
   Rejected — this contradicts [ADR-0017](0017-identity-model.md) outright: an account is created
   only when a guardian needs portal access, and "a circular was sent near them" is not that.
3. **A recipient row keyed by student id, chosen.**

## Options considered, for the channel question

1. **Add `IN_APP` to `NotificationChannel` with a trivial adapter.** Rejected — one implementation
   with no second failure mode is not evidence the abstraction fits; ADR-0013's own "to revisit"
   section already flags this exact shape as suspicious.
2. **No port; a direct write and a direct read.** Chosen.

## Consequences

**Easier.** The recipient model does not need to change when the parent portal ships — only a new
read path, gated by a scope this codebase does not resolve yet, needs to be added. No queue, no
adapter and no channel-type enum to maintain for a feature that has no external delivery yet.

**Harder.** "Delivered" is a weaker claim than it will read as once channels exist — a school reading
"32 recipients, delivered" today should understand that as "32 students this reached inside our own
system," not "32 phones buzzed." The manual pages for this feature say so. Acknowledgement recorded
by staff on a family's behalf is also weaker than a family's own action, and is documented as such
everywhere it appears in the product and in this repository.

**To revisit.** When the parent portal's guardian-to-student scope resolution exists, add the
`WARD`-scoped read path this ADR describes and build the actual parent-facing acknowledgement
endpoint, gated by a permission a guardian's session can hold. When an email or push adapter is
built for circulars, decide then whether it reads `circular_recipient` directly or whether a second,
smaller table for external-channel attempts is worth the join — this ADR does not need to guess.
