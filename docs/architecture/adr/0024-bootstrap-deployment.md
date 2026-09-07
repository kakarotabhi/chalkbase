# ADR-0024: Bootstrapping a fresh deployment

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md) (authorization model, the permission this closes
  around), [ADR-0017](0017-identity-model.md) (per-school accounts), [ADR-0011](0011-schema-per-tenant.md)
  (schema per tenant, what `provision` brings online), [ADR-0018](0018-audit-log.md) (audit, `changed_fields`
  never a value)

## Context

`POST /api/schools` answers 403 `PERM_001` even with a correct `X-Chalkbase-Setup-Key`. Verified
against a deployed instance: no header → 404, wrong header → 404, correct header → 403. The setup
key works; something after it refuses.

The cause is `@PreAuthorize("hasAuthority('school:school:create')")` on every `SchoolController`
method, added by PR #33 to close a real hole — the register used to be `permitAll()`, which made
`GET /api/schools` world-readable: every school's name, code and PostgreSQL schema name, to anyone
who asked. Closing that read is right. Closing the write alongside it, with no other way to reach
onboarding, is what breaks the product: `school:school:create` is deliberately held by no shipped
role template (`RoleTemplates` says so explicitly), because it stands in for a platform-operator
permission that does not exist. Nobody, ever, holds it. `SecurityConfig` and `SchoolController` both
carry a `TODO(identity)` naming the operator account as the real fix.

`DemoSchoolSeeder` already states the trap, in a comment above the code that avoids it:

> Registered directly rather than through `POST /api/schools`, and the reason is a real one rather
> than convenience. That endpoint used to be `permitAll`, so this call worked before there was
> anyone to sign in as. It is now an operator endpoint requiring `school:school:create`, which no
> role holds — so the HTTP call would be refused, and the chicken-and-egg is unresolvable over HTTP:
> the first account cannot exist until the school does.

The seeder writes the registry row with JDBC and calls `SchoolProvisioning.provision()` directly,
and it is `@Profile("local")`. **The dev environment's school exists because a developer ran a local
seeder against the shared database, not because onboarding works.** The Mumbai VPS
([ADR-0015](0015-deployment-baseline.md)) cannot onboard a real school either, and neither can the
Render dev deployment if `demo_school` is ever dropped. Phase 1's first exit criterion — "Admin can
configure one school and academic session" — is currently satisfied only by a developer tool, not by
the product.

## Options considered

1. **A platform-operator account.** Accounts outside any tenant schema, with their own
   authentication path, holding `school:school:create` for real. This is what every `TODO(identity)`
   in the codebase names as the answer, and it is the honest, complete fix.

   It is also the larger one, and larger in a way that matters here: ADR-0017 spent real effort
   establishing that accounts live *inside* a school's schema specifically so nothing about a person
   sits in `public` — "the table is `user_account`, not `user`" is one paragraph of a decision about
   exactly that boundary. An operator account breaks that boundary on purpose, for a different kind
   of person (nobody at a school, someone running the platform), and needs its own place to live, its
   own authentication path the per-school session model does not cover, and its own answer to "how
   does the *first* operator account get created" — the same chicken-and-egg one layer up, unless the
   first one is seeded by a deploy step, which reintroduces exactly the "a developer's tool did this,
   not the product" problem this ADR exists to close. None of that is wrong to eventually build —
   ADR-0005 already reserves a related idea ("platform support access is its own thing... every use
   of it is audited and time-boxed") for a support-access feature that does not exist yet either. It
   is wrong to build *first*, on the way to fixing a narrower, more urgent problem, when a smaller fix
   closes the actual gap.

2. **A bootstrap endpoint guarded by the setup key**, creating the school *and* its first
   administrator atomically, refusing to run a second time for a school that already has an account.

   Smaller, and closes the chicken-and-egg exactly where it bites: there is no principal to
   authenticate *because* there is no school yet, so the fix is an endpoint that needs none, protected
   by the one secret a fresh deployment already has to have (`CHALKBASE_SETUP_KEY`, which `prod`
   refuses to start without). The honest cost, in `SecurityConfig`'s own words about the setup key: "a
   single shared secret names nobody, expires never, and cannot be audited." True, and accepted for
   this one endpoint, for the reasons in the Decision section below.

3. **Keep `DemoSchoolSeeder`'s shape, but make it reachable in `prod`.** Rejected outright: the
   seeder writes rows with raw JDBC, bypassing every service method, every `@PreAuthorize`, and every
   `AuditService.recordChange` call the endpoints it skips would have made. Turning that on in
   production is not a smaller version of onboarding — it is no onboarding at all, with a database
   write where onboarding should be.

## Decision

**Option 2.** `POST /api/schools/bootstrap`, alongside the existing `POST /api/schools` (which stays
exactly as PR #33 left it — see Consequences).

### Shape

- **Request**: the same six school fields `CreateSchoolRequest` already takes, plus `adminUsername`
  and `adminDisplayName`. No password field — one is generated, matching `CreateUserAccountRequest`.
- **Response**: the created school, the administrator's id and username, and the generated temporary
  password — shown exactly once, the same rule `NewUserAccountResponse` already documents. Nothing
  stores it and no endpoint can retrieve it again.
- **Guard**: `permitAll()` at the method (there is nobody to authenticate against, which is the
  problem this closes, not an oversight), the setup key on `prod` (`SetupKeyFilter`, already matching
  `/api/schools/**`), and CSRF stays exempt on that path for the reason `SecurityConfig` already gives
  — the endpoint reads no cookie, so there is no ambient authority for a forged request to spend.
- **Idempotency**: retrying with the same `code` reuses the already-registered school and re-runs
  `SchoolProvisioning.provision` (idempotent by its own contract). What refuses is the account half:
  a new `FirstAdminProvisioner` port, implemented by identity, throws `AUTH_014` the moment the
  target schema already holds a `user_account` row — from an earlier successful bootstrap, or from an
  administrator who has since created further accounts through the ordinary screens. Retrying with
  the same `code` but a *different* `schemaName` is refused separately (`SCHOOL_007`), so a typo on a
  retry cannot silently redirect an already-registered school.
- **The administrator's password**: generated by `TemporaryPasswordGenerator` (already used for
  ordinary account creation and admin resets), never logged, never a fixed value, with
  `must_change_password = true` — the existing forced-change machinery (`SessionStandingFilter`) does
  the rest. The role granted is `PRINCIPAL`, the head-of-school template: the widest shipped
  template, on the reasoning that a first administrator is whoever brings the rest of the school's
  accounts online, and a school that wants less removes it from that one account's grant the same way
  it edits any other role.
- **Audit**: `AuditService.recordChange` runs inside the same transaction as the account write, once
  the tenant is bound, naming the fields (`displayName`, `username`, `role`) and no value — ADR-0018
  unchanged. The school-registration write itself is not audited, because it happens with no tenant
  bound (`public.school`) and `recordChange` requires one; `SchoolService.create` has never audited
  this either, for the same structural reason, and this ADR does not change that.

### Where the code lives, and why it does not create a cycle

`identity` already depends on `school.api` (`SchoolLookup`, for login). A bootstrap endpoint needs
the reverse as well — the school module needs identity's capability to create an account. Rather than
give `school` a compile-time dependency on `identity` (which `ModularityTests` would refuse as a
cycle), the account-creation capability is an SPI in `platform.tenancy`
(`FirstAdminProvisioner`/`FirstAdminAccount`), the same shape `TenantInitializer` already uses for
exactly this reason: "the shared kernel must not import identity" is `TenantInitializer`'s own
Javadoc, and it is `FirstAdminProvisioner`'s reasoning too. `identity` provides the implementation
bean (`FirstAdminProvisioningService`); `school.application.SchoolService` calls it only through the
interface, wiring the caller has no compile-time knowledge of which module answers.

### Why not one database transaction

The registry row lives in `public`; the account lives inside the new tenant's schema; and Hibernate's
multi-tenant session picks its schema when a transaction opens, not per statement — the same
constraint `AuthenticationService` already documents for login, which has the identical shape
("bind the tenant, then enter a transactional method on a different bean"). `SchoolService.bootstrap`
is not itself `@Transactional`; each half — the registry write, `SchoolProvisioning.provision`, and
`FirstAdminProvisioningService.provisionFirstAdmin` — is transactional on its own, entered in order,
with the tenant bound via `TenantContext.callWith` before the last one. This is not new: it is the
same non-atomicity `SchoolService.create`'s own Javadoc already accepts and explains ("a registered
school whose schema is missing is recoverable... while an unregistered schema is invisible").
"Atomic" in this ADR's acceptance sentence means atomic *from the caller's point of view* — one HTTP
call, not a lock across two schemas — not literal single-transaction ACID across `public` and a
tenant schema, which the multi-tenant session model does not offer and nothing else in this codebase
attempts either.

## Acceptance test

**After a fresh deploy against an empty database, a person with the deployment's secrets can create
a school and sign in to it, over HTTP, with no developer tooling and no direct database access.**

`SchoolBootstrapApiTests.bootstrapsASchoolAndSignsInAsTheGeneratedAdministrator` is this sentence made
executable: it calls `POST /api/schools/bootstrap` with no session and no CSRF token (an operator's
script has neither), reads the generated password back out of the response the same way a person
would, and signs in with it through the ordinary `POST /api/auth/login` — no seeder, no JDBC, no
shortcut. The sibling tests in that class cover the two ways this endpoint could quietly become a
backdoor: running twice for one school (`AUTH_014`), and disagreeing with itself about which schema a
retried code belongs to (`SCHOOL_007`).

The sequence a person actually runs against a fresh deployment:

```bash
curl -X POST https://<api-host>/api/schools/bootstrap \
  -H "X-Chalkbase-Setup-Key: <the deployment's CHALKBASE_SETUP_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
        "code": "GPS-001",
        "name": "Greenfield Public School",
        "schemaName": "greenfield",
        "board": "CBSE",
        "city": "New Delhi",
        "state": "Delhi",
        "adminUsername": "admin",
        "adminDisplayName": "Priya Sharma"
      }'
```

The response carries `adminUsername` and `adminTemporaryPassword` once. Sign in with those at
`POST /api/auth/login` (`schoolCode`, `username`, `password`), which returns
`mustChangePassword: true`; the existing forced-change flow takes it from there.

## Consequences

- **`POST /api/schools`, `GET /api/schools` and `GET /api/schools/{id}` are unchanged**, including
  staying unreachable by any account today. They are not this ADR's problem to solve: they are a
  platform-operator *view* of the whole register (list every school, read one by id, register a
  campus without also creating its first account), which is a different feature from bootstrapping
  one school end to end. If a platform-operator account is ever built, these three are what it gets —
  bootstrap does not change shape to accommodate it, because bootstrap's whole reason to exist is
  answering "there is no operator yet."
- **The setup key is now guarding two things it did not fully guard before**: a read it always
  should have (the register, closed by PR #33) and a write that now actually does something
  (bootstrap). Losing the key is more consequential than it was — but bootstrap's own refusal on a
  second run means a leaked key can create at most one administrator per school, not an unbounded
  number, which is the backdoor this ADR was written to rule out.
- **A platform-operator account remains the right answer for a different problem**: platform support
  access, ADR-0005 already flags as needing its own audited, time-boxed identity. This ADR does not
  build it and does not block it — `FirstAdminProvisioner` would become one of the things such an
  account calls, unchanged, the day it exists.
- `docs/status.md`'s "Known gaps and debt" bullet claiming `/api/schools/**` is `permitAll` and a
  CSRF-exempt hole was stale in both directions: the three operator endpoints were never reachable
  (so not a hole), and the CSRF exemption was never load-bearing for them (so not a live risk either).
  Corrected in the same change as this ADR.
