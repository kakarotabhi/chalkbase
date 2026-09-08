# ADR-0031: Role template reconciliation — a school gets a permission a template gained after it was onboarded

- Status: Accepted
- Date: 2026-09-08
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md) (roles are data, copied from a template once),
  [ADR-0023](0023-session-revalidation.md) (a granted permission takes effect at next login),
  [ADR-0018](0018-audit-log.md) (what gets a trace and how), [ADR-0011](0011-schema-per-tenant.md)
  (the startup-time budget this has to fit inside)

## Context

`RoleTemplateInstaller` copies each shipped role template into a school's own `role` table once,
at onboarding, and — until this ADR — never again: `initialize(schema)` read the existing role
codes and `continue`d past any template whose code was already a row. That was deliberate. ADR-0005
says a school's role is a copy, not a reference, precisely so that adding a permission to a
template in a release cannot silently widen access at every school that already exists — a security
incident delivered by an upgrade.

The rule was applied one level too coarsely. "Do not overwrite a role" became "do not look at a role
that exists," and those are not the same thing. A role that already exists because it was installed
at onboarding, and has never been touched by anyone since, is not the same as a role a school
deliberately edited — but the old installer could not tell them apart, so it treated both as
off-limits forever.

**Confirmed on the deployed dev environment.** Signing in as `principal` at `DEMO-001` returns 12
permissions. The catalogue holds 24. Every permission a module has added since `DEMO-001` was
onboarded — `identity:user:manage`, `academics:subject:{read,manage}`, all of `attendance:*`, all of
`document:document:*`, `student:student:reveal_restricted` — is simply missing, on every role,
forever, because the row for each role already existed the day it was added. A school onboarded
today gets all of them, through the `installed == null` branch of the same method. That divergence
is the entire bug: two schools running the same release, with different access, for a reason nobody
chose.

**It cannot be fixed through the product.** `AccessGuardrails.requireHeldByActor` correctly refuses
to let a holder of `identity:role:manage` grant a permission they do not themselves hold. The
`principal` account cannot grant itself `attendance:mark:manage` to then grant it to a role, so
there is no in-product path back to parity. This has to be a code fix.

ADR-0005 §2 also said, in passing, that a school's administrator would see a "new permissions
available" review prompt when a template grew. That screen was never built. There is no endpoint,
no notification and no UI for it anywhere in this codebase. Waiting for it is not a real option for
closing a live bug, and building it is a larger change than a one-lane fix — a review-and-accept
flow needs its own endpoint, its own screen, and its own decision about what happens to a school that
never opens it. This ADR does not build that screen. It picks the smaller, safe subset of the same
problem that can be closed without one, and leaves the review screen exactly as buildable as it always
was.

## Options considered

1. **Leave it as `continue`.** The status quo. Every school onboarded before a given release stays on
   that release's permission set forever, for every role, whether or not the school ever touched it.
   Rejected — this is the bug.

2. **Overwrite every template-derived role's permissions from the template on every startup.** Fixes
   the bug for a school that never touches its roles. Breaks the promise ADR-0005 makes the moment
   role management (shipped days before this bug was found) lets a school edit one: the next startup
   would silently undo the edit. Rejected outright — this is not a smaller version of the bug, it is
   a different one, and a worse one, because it destroys data a human deliberately chose rather than
   merely withholding data nobody chose.

3. **Additive-only reconciliation with no memory of edits: add whatever the template has that the row
   lacks, never remove anything.** Closer, and wrong in exactly one case. A school that used role
   management to _remove_ a permission from a template-derived role — deciding, on purpose, that its
   `CLASS_TEACHER` should not see guardian phone numbers, say — would have that permission silently
   restored the next time any release touches that template, because "the row lacks it" cannot
   distinguish "never had it" from "had it and gave it back." Rejected for the same reason as option
   2, at smaller scale: it still overwrites a choice a human made, just less often.

4. **Mark a role customised the moment role management changes its permission set; reconcile only
   roles that are not.** Chosen. One `boolean` column, `role.customised`, defaulting `false`, set to
   `true` — and never back to `false` — by `Role.replacePermissions`, the one method
   `RoleManagementService.updateRolePermissions` uses to change what a role grants.
   `RoleTemplateInstaller` reconciles a template-derived role only while `customised` is `false`: it
   adds whatever permission the template has that the row lacks, the same way option 3 does, but it
   never looks at a role a school has ever edited, for any reason, again. An unedited role tracks its
   template forever. An edited role belongs to the school from the moment of the first edit, exactly
   as ADR-0005 already says every role does — this ADR is what makes that true in the one place it
   was not.

## Decision

**Option 4.**

### What reconciliation does

For each shipped template, at every school, on every startup (`RoleTemplateInstaller.initialize`,
still `@Order(200)`, still run by `SchoolProvisioning` for both onboarding and the
`TenantMigrationRunner` startup fan-out):

- **No row with this template's code exists.** Install it in full, exactly as before — this path is
  unchanged.
- **A row exists and `customised` is `true`.** Do nothing. The school's, permanently.
- **A row exists and `customised` is `false`.** Add every permission the template names that the
  row's `role_permission` rows do not already carry. Never remove one — removing a permission is
  role management's decision, guarded by `AccessGuardrails`, never this installer's.

**Never remove. Never invent.** Reconciliation only ever grants a permission that (a) the template
names and (b) the row does not already have. `PermissionCatalog.requireAll` still runs once, at
construction, against every permission every template names, so reconciliation can never attempt to
grant a permission the catalogue does not contain — the existing startup-failure guarantee extends
to the new code path for free, because it is the same set of permission codes being inserted either
way.

### Cost

Detecting what is missing costs exactly two queries per school, not one per role and not one per
template: `select id, code, customised from role` and `select role_id, permission_code from
role_permission`, both read once into memory before the per-template loop runs. A school with
nothing to reconcile — every startup after the first one following a release — pays for two selects
that return normally and change nothing. ADR-0011 records the startup-time budget this has to fit
inside; two indexed, whole-table (twelve and at most a few hundred rows respectively) selects per
school do not move that number in any way worth measuring.

### What a school that edited a role gets

Nothing changes for it, ever, from this class. The moment `RoleManagementService.updateRolePermissions`
first replaces that role's permission set, `customised` becomes `true` and stays `true`. A permission
removed on purpose is never restored. A permission added on purpose is, obviously, still there — this
ADR does not touch permissions a human already granted. A template growing a new permission after
that point has no effect on the edited role at all; the school's admin adds it by hand, through role
management, the same as any other permission that role does not automatically receive. That is a real
limitation — the school gets no notice that a new permission exists — and it is the same limitation
ADR-0005's unbuilt review prompt would have closed. This ADR does not close it either; it only makes
sure the _unedited_ case, which is the common one and the one the reported bug is about, is not stuck
the same way.

### When it takes effect

At the affected accounts' next login, per ADR-0023: adding a permission is exactly the additive case
ADR-0023 already accepts "next login" for, and nothing about a role gaining a permission it did not
have is urgent enough to end a live session early. This is a statement of existing policy, not a new
exception — `RoleManagementService` already treats an added permission this way when a human grants
it through the product; the installer granting one on the school's behalf does not need a stronger
guarantee than a human does.

### What gets a trace

`RoleTemplateInstaller` runs inside `TenantMigrationRunner.afterPropertiesSet()`, before the entity
manager factory exists — the same reason it was already raw `JdbcClient` rather than JPA, and the
same reason `AuditService` is not reachable from here: every one of its write paths needs either a
Spring-managed transaction (`recordChange`, `recordBulkChange`) or, for `recordSecurityEvent`, an
actor to resolve a schema from when no tenant is bound — and there is no HTTP request, no
authenticated principal and no entity manager behind this call, ever. `AuditRetentionPurgeJob` is
the nearest precedent for "code acting with no account, no request" and it can afford
`AuditActor.system(...)` because it runs on `@Scheduled`, long after startup, with JPA fully up. This
runs before that machinery exists at all, so its precedent is not the purge job — it is
`RoleTemplateInstaller` itself, which has never audited the ordinary insert-a-new-template-role path
either, and instead logs it: `log.info("Copied {} role template(s) into {}", installed, target)`.

Reconciliation follows the same precedent rather than inventing a new one: `log.info` per role
reconciled, naming the school's schema, the template code, and how many permissions were granted
(never which ones removed, because none ever are), plus one summary line per school when anything
happened. A silent mass permission grant should still leave _some_ trace, and it does — in the
application log, at the same level and through the same mechanism the installer already uses for
installing a role in the first place, which nobody has ever needed `AuditService` for. Building a
path for identity to audit a pre-JPA startup event that only `platform` code runs during is a bigger
change than this bug warrants, and nothing about this decision forecloses it later.

### Existing rows, at the moment this migration runs

`V2026_09_08_1531__identity_add_role_customised.sql` adds the column `not null default false`. Every
role that exists anywhere the migration runs starts unedited, including a role a school genuinely did
edit before this column existed — there is no record of which rows those were, and role management
shipped only days before this bug was found, so the exposure is small and bounded to a single
migration. The alternative, starting every existing row `true`, would protect that small, unknown set
of past edits at the cost of the fix this ADR exists for: every school provisioned before today would
stay exactly as stuck as `DEMO-001` is now, forever, because reconciliation would never look at any of
their roles again. Between "a handful of already-edited roles might see one restored permission,
once, on the next deploy" and "the reported bug does not actually get fixed for any existing school,"
the former is the smaller and more honest cost, and it is a one-time window that closes for every role
the instant this migration ships and role management is used again.

## Consequences

- A school bootstrapped last month and a school bootstrapped today converge on the same permission
  set for every role neither has edited, on the next deploy that touches either — which is the whole
  point, and was the whole bug.
- `demo_school` and `qa_sandbox` heal themselves on the next startup that reaches them, verified by
  `RoleTemplateInstallerTests`, which provisions a schema, forces one of its roles into the exact
  "stale" shape the old installer left behind, and asserts the missing permission is granted on the
  next `initialize` call — and, separately, that a role marked `customised` is never touched and that
  running `initialize` twice grants nothing the second time.
- `role.customised` is set only by `Role.replacePermissions`, so `RoleManagementService.createRole`
  and `RoleManagementService.updateRolePermissions` are the only write paths that can produce a
  `true` row (a freshly created, school-invented role also starts `true`, for the same reason —
  nothing shipped it, so reconciliation was never going to apply to it, and the row should read as
  the school's own from the moment it exists rather than depend on its code never colliding with a
  template's). No endpoint exposes `customised` directly; there is no way to set or clear it except
  by editing a role's permissions.
- The "new permissions available" review prompt ADR-0005 §2 imagined is still not built. This ADR
  narrows that promise rather than fulfilling it: an unedited role gets the update automatically and
  silently, with no per-school opt-out, which is a different (smaller, safer, but less informative)
  guarantee than a review-and-accept screen would give. Building that screen remains open work, and
  nothing here makes it harder — `customised` is exactly the flag such a screen would need to decide
  which roles to even offer a review on.
- A school's administrator has no visibility that a role just gained a permission automatically.
  `RoleResponse` does not expose `customised` and no notification is sent. Surfacing either is a
  frontend and API change this ADR deliberately leaves out of a backend bug-fix lane.
