# ADR-0023: Session re-validation — what is re-read, how often, and what it costs

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md) (authorization, resolved once at login),
  [ADR-0003](0003-authentication-and-authorization.md) (server-side sessions),
  [ADR-0017](0017-identity-model.md) (per-school accounts), [ADR-0008](0008-server-driven-navigation.md)
  (`/api/me`, `permissionsVersion`)

## Context

ADR-0005 resolves a session's effective permissions once at login and caches them on the principal
for the life of the session, deliberately: recomputing them per request would mean a join across
`user_role_grant`, `role` and `role_permission` on every call, and the ADR rejects that outright.

Closing the forced-password hole (`PasswordChangeRequiredFilter`, merged earlier) made a gap in that
design visible rather than creating it. `must_change_password` is read fresh from `user_account` on
every API call. **Nothing else about the account is.** `status` and `locked_until` are resolved once
at login and never revisited, so today:

- An administrator disables an account mid-session (no such endpoint exists yet, but item 2 of this
  same milestone adds one) and the holder keeps reading the school's data until the session expires
  on its own — up to `SessionDuration.REMEMBERED`, seven days.
- An account is locked out by repeated failed login attempts from somewhere else while a legitimate
  session is already open elsewhere; that open session is unaffected.

We re-check the least severe of the three things ADR-0005 resolves at login (a flag that only
narrows what the session may do) and not the two more severe ones (whether the session should exist
at all). That is backwards, and status.md names it as blocking the first real school.

## What a session re-validates

**`status`, `locked_until` and `must_change_password` — the same three columns of `user_account`, on
every API call. Nothing else.**

The reason all three are affordable and the permission set is not: each of the three is a single
column reachable by the primary key already known from the session (`SessionAttributes.USER_ID`).
The permission set is not a column at all — it is the result of a join across three tables, sized by
however many grants and roles a user holds. One is a lookup; the other is a query. ADR-0005 pays the
query once, at login; this ADR pays the lookup on every request, because it is cheap enough to.

## How often

**Every API request**, in `SessionStandingFilter` (renamed from `PasswordChangeRequiredFilter`, which
this supersedes rather than sits beside). There is no weaker cadence worth having: a background sweep
that revalidates sessions every N minutes still leaves a window exactly N minutes wide in which a
disabled account keeps working, and the whole point raised in status.md is that the window today is
"however long the session has left to live" — up to seven days. A per-request check has no window at
all beyond the request already in flight when the write happens.

## What it costs

**Nothing beyond what the application already pays.** `PasswordChangeRequiredFilter` already ran one
indexed primary-key `SELECT` against `user_account` on every authenticated API call, projecting the
single `must_change_password` column rather than loading the entity (`UserAccountRepository`'s
Javadoc already explains why: no dirty-checking for a read nobody writes back). `SessionStandingFilter`
replaces that projection with `AccountStanding` — `status`, `locked_until` and `must_change_password`
in one row — which is the same query, the same index, the same round trip. Three columns instead of
one changes nothing about the plan Postgres picks for a primary-key lookup. There is no new network
round trip, no new table, and no measurable difference in per-request latency.

## What this deliberately does not cover

- **The effective permission set.** Still resolved once at login (ADR-0005), still cached on the
  principal, still never re-read per request. A role edit or a grant change takes effect the next
  time the affected user logs in, or immediately when the write that made the change explicitly ends
  the affected sessions — see below. `permissionsVersion` (ADR-0008) is unchanged by this ADR: it
  still exists to let a client notice a stale menu after a `403` and refetch `/api/me`, which remains
  the correct and only mechanism for permissions specifically. Nothing here weakens or duplicates it.
- **A scheduled sweep of `public.spring_session`.** Re-validation is reactive — it fires on the next
  request a live session makes — not proactive. A disabled account that never makes another request
  keeps a session row in the table until it expires on its own; that row is inert (any request against
  it would be refused) but not deleted. Purging expired rows from `public.spring_session` remains the
  separate, already-known gap the "Known gaps and debt" section of `status.md` records.
- **Anything about a session the holder is not currently using.** Re-validation happens inline, in
  the request that discovers the problem. It is not a mechanism for reaching into a session nobody is
  using right now; that is what explicit invalidation (below) is for.

## The active counterpart: explicit session invalidation

Passive re-validation answers "is this session still good?" on the next request it makes. Some writes
need a stronger answer than "eventually": an admin password reset must dislodge whoever holds the old
cookie immediately, not whenever they next click something (status.md states this as a hard
requirement), and revoking a role or a grant that carries `identity:role:manage` should not leave the
old permission set live for up to seven days just because nobody has re-derived it.

`SessionInvalidationService` (new, `identity/application`) deletes every live session for one account
on demand, looked up by a schema-qualified index (`<schema>:<accountId>`) set explicitly at login —
**not** by Spring Session's default `principal_name` index, which this application derives from the
_username_, and usernames are unique only within one school (ADR-0017). Two schools issuing the same
admission number as a username would otherwise let one school's forced session-end delete a
different school's session. See `SessionInvalidationService`'s own Javadoc for the mechanism.

It is called from:

- Admin password reset and account deactivation (item 2 of this milestone).
- Revoking a grant: the target's sessions end immediately (item 3).
- Replacing a role's permission set, whenever the replacement *removes* one or more permissions:
  every account currently holding that role has its sessions ended (item 3). Adding a permission is
  not guarded this way — it only ever widens access, and ADR-0005 already accepts "takes effect on
  next login" for anything additive. Only the removal case is urgent enough to act on immediately.

Creating a role and granting one add access and never end a session, for the same reason: nothing
about them needs to be urgent, and ADR-0005's "next login" already covers it. This ADR does not
attempt "every permission change takes effect instantly everywhere" — that is the per-request
permission query ADR-0005 rejected, arrived at by a different route. It attempts the narrower,
achievable thing: every write in this milestone that *takes access away* ends the affected session or
sessions immediately, and every write that only grants access can wait for the next login.

## Options considered

1. **Re-read the full permission set on every request.** Rejected by ADR-0005 already, for cost —
   restated here because it is the obvious way to make _everything_ live and the reason this ADR does
   not do that.
2. **A background job that expires sessions belonging to disabled or locked accounts.** Adds a
   scheduler, a polling interval, and a window equal to that interval during which the bug persists.
   Strictly worse than per-request re-validation on every axis except "no per-request query", and that
   query already exists.
3. **Re-validate status and lockout per request; leave permissions on the login-time cache; add
   explicit invalidation for the writes that need to be instant.** Chosen. Reuses the existing
   per-request cost, closes the exact gap status.md names, and does not relitigate ADR-0005.

## Consequences

- `PasswordChangeRequiredFilter` is renamed `SessionStandingFilter` and its responsibility widens
  from one flag to three; behaviour for `must_change_password` is unchanged and
  `ForcedPasswordChangeTests` passes unmodified against the new class.
- A disabled or locked account's session is invalidated the moment it is next used, and the caller
  sees the ordinary `AUTH_002` — the same response a client already knows to react to by discarding
  its session and returning to sign-in. No new error code, no new frontend work.
- `AuditAction.SESSION_REVOKED` is added: one row per session the server ends unasked, distinct from
  a user-initiated `LOGOUT` and from `PERMISSION_DENIED` (whose count two existing producers already
  own — see `AuditService`'s own Javadoc for why a third producer there would be wrong).
- `AuthenticationService#establishSession` now also sets Spring Session's principal-name index
  explicitly, to a schema-qualified value, so `SessionInvalidationService` can find a specific
  account's sessions without a cross-school collision. This changes what is stored in
  `spring_session.principal_name` (previously the bare username via Spring Security's default
  derivation); nothing reads that column today except this new lookup, so nothing else changes
  behaviour.
- Frontend impact: none. `permissionsVersion` and the `403`-refetches-`/api/me` contract from
  ADR-0008 are unchanged; the lane implementing the client half of that should not need to know this
  ADR exists.
