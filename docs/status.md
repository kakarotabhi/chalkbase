# Where the project is

Living status. **Updated in the same pull request as the work it describes** — a status file that is
updated "later" is worse than none, because people trust it.

Last updated: 2026-09-07 · Roadmap phase: **2** — Phases 0 and 1 are complete
([Phase definitions](requirements/06-roadmap-and-mvp.md) · [Phase 0 decisions](requirements/07-phase-0-decisions.md) ·
[Phase 2 scope](requirements/08-phase-2-scope.md), planned ahead of Phase 1 finishing so it can be handed out
the way Phase 1's work was)

## At a glance

| Area                                                                  | State                                                                                                                                                       |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Repository, CI, branch protection                                     | ✅ Done                                                                                                                                                     |
| Backend skeleton (Spring Boot 4.1, Modulith)                          | ✅ Done                                                                                                                                                     |
| Frontend skeleton (Angular 22, adaptive shell)                        | ✅ Done                                                                                                                                                     |
| PostgreSQL, profiles, Testcontainers                                  | ✅ Done                                                                                                                                                     |
| API response envelope and error handling                              | ✅ Done                                                                                                                                                     |
| Design tokens and palette                                             | ✅ Done                                                                                                                                                     |
| Screen designs for the first six screens                              | ✅ Done                                                                                                                                                     |
| Architecture decisions (ADR-0001…0027)                                | ✅ Done                                                                                                                                                     |
| **Phase 0 discovery — all 13 deliverables**                           | ✅ Done                                                                                                                                                     |
| Identity: login, sessions, forced password change                     | ✅ Done                                                                                                                                                     |
| Permissions, roles, scoped grants                                     | ✅ Enforced and manageable, with a screen · ⬜ impact preview                                                                                               |
| Server-driven navigation (`GET /api/me`)                              | ✅ Done                                                                                                                                                     |
| Schema-per-tenant: registry, migration orchestrator                   | ✅ Done                                                                                                                                                     |
| Audit log (FR-008) — table, service, `GET /api/audit`, and its screen | ✅ Done                                                                                                                                                     |
| School profile — `GET`/`PUT /api/school/profile` and its screen       | ✅ Done                                                                                                                                                     |
| Shared UI components                                                  | ✅ Button, field, inputs, checkbox, select, bottom sheet                                                                                                    |
| Academic sessions, classes and sections                               | ✅ Done                                                                                                                                                     |
| Subjects                                                              | ✅ Done                                                                                                                                                     |
| Students, guardians and enrolment                                     | ✅ Core record, contact, medical, previous school and compliance — including the Restricted columns, encrypted · ⬜ transport and hostel, which are Phase 4 |
| Documents (FR-013, FR-032)                                            | ✅ Storage port, module, S3 adapter and a screen · ⬜ the five environment variables set on Render                                                          |
| Basic dashboards                                                      | ✅ Done                                                                                                                                                     |
| Export                                                                | ✅ Done                                                                                                                                                     |
| Deployment                                                            | ✅ Render dev · ⬜ Coolify/VPS (production, Phase 4)                                                                       |

A ✅ in this table means the slice works end to end, not that the roadmap feature is finished.
[Phase 1 in detail](#phase-1-in-detail) is the per-feature account.

## Phase 1 in detail

[The roadmap](requirements/06-roadmap-and-mvp.md) lists eleven features for Phase 1. The table above
is deliberately coarse; this one is the honest state of each, because three of them read as done at a
glance and are not.

| Roadmap feature       | State                                                                  | What exists · what does not                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| --------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| School profile        | ✅ Done                                                                | `GET`/`PUT /api/school/profile`, its screen, and the registry write-back.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Academic session      | ✅ Done                                                                | Create, edit, and make-current, with its screen.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| Classes and sections  | ✅ Done                                                                | The structural ladder (ADR-0019), reorder, retire and reinstate.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| Subjects              | ✅ Done                                                                | The flat catalogue (no ladder, no relation to a class or section), paged, retire and reinstate.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Roles and permissions | ✅ Done                                                                | 18 permissions across 5 module registries, `@PreAuthorize` on every write endpoint, scoped grants, and shipped role templates. `/api/access` creates a role, replaces its permission set, and grants or revokes it for a user ([ADR-0023](architecture/adr/0023-session-revalidation.md) covers the guards this needed: `AccessGuardrails` stops a holder of `identity:role:manage` granting a permission they do not themselves hold, and stops any of these writes leaving the school with nobody who can manage access). The screen is `/settings/access`: the permission catalogue, this school's roles, create and edit, who holds each role, grant/revoke for an account, and an impact preview on the edit form — who holds this role and, from [ADR-0023](architecture/adr/0023-session-revalidation.md), that removing a permission signs every holder out immediately while adding one waits for their next login — built entirely from `GET /api/access/roles/{id}/holders`, no new endpoint.                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| User management       | ✅ Done                                                                | `user_account` carries `status`, `failedAttempts` and `lockedUntil`, and login honours all three. `/api/access/users` creates an account, deactivates or reactivates one, clears a lockout, and issues an admin password reset — the last one ends the target's sessions immediately rather than waiting for them to notice (`SessionInvalidationService`, ADR-0023). Guarded against locking a school out of its own access: deactivating the last account that can manage access is refused. The screen is `/settings/users`: the roster, with status, and all five actions. It cannot show a lockout badge per row — `GET /api/access/users` answers no `lockedUntil` — so **Clear lockout** is offered on every active account rather than only the ones known to need it; see [Known gaps and debt](#known-gaps-and-debt).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Student profile       | ⚠️ Core, contact, medical and compliance done                          | `student` holds admission number, name, date of birth, gender, status and admitted-on, plus enrolment. Added: contact (`student_contact`), previous school and transfer certificate (`student_transfer`, FR-033), medical (`student_medical`, FR-034 — CWSN/disability, allergies, chronic conditions, medication and blood group Restricted, encrypted, masked and read-audited; emergency contact Confidential) and compliance identifiers (`student_compliance`, FR-029 — PEN/UDISE and board registration number Confidential; caste, religion, EWS/BPL/RTE category and a consent-gated APAAR id Restricted). [ADR-0020](architecture/adr/0020-student-and-guardian-model.md) §2, amended, and [ADR-0022](architecture/adr/0022-encryption-at-rest.md) cover the design. A documents section (certificates, photo, signature) now sits alongside these on the record — see the Documents row below. **Still absent:** transport and hostel — both are Phase 4 modules and are a need-flag only on this record, not built yet.                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| Guardian profile      | ✅ Done                                                                | Directory, attach and detach, relation, main contact, and digit-normalised phone search.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| Documents             | ✅ Port, module and screen · ⬜ configured on the deployed environment | [ADR-0025](architecture/adr/0025-document-storage.md) built the storage port, the `document` module, and a documents section on the student record ([FR-013](requirements/02-functional-requirements.md), [FR-032](requirements/02-functional-requirements.md)) — upload (with progress, failure and size-refusal states), list, download (proxied, never a signed URL), edit, verify and delete, all audited. The ADR's amendment settled the dependency question with AWS SDK v2 (`software.amazon.awssdk:s3` plus `url-connection-client`, with the SDK's own default Netty and Apache HttpClient 5 dependencies excluded — ~8.3 MB measured) over a hand-rolled signer, on the same reasoning already recorded here: signing is security-sensitive code where a subtle error costs more than jar weight. `local`/`test` keep the real filesystem adapter; `prod` uses the S3-compatible adapter once five `CHALKBASE_STORAGE_*` variables are set ([docs/operations/document-storage.md](operations/document-storage.md)) and otherwise still answers a clean 503 — those variables are not yet set on Render, so nothing persists on the deployed environment until that operational step happens. Renewal reminders are modelled (`expiry_date`) but not built — no scheduler exists to run one. Restricted-category document types (a caste certificate, an Aadhaar copy) are deliberately excluded until the same encryption machinery ADR-0020 §2 is waiting on lands. |
| Import                | ✅ Done                                                                | CSV, validate-first, all-or-nothing ([ADR-0021](architecture/adr/0021-bulk-import.md)). Guardians are imported too, one per row, matched against the directory by phone; a phone shared under two names refuses the row rather than guessing. `.xlsx` is refused with instructions rather than parsed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **Export**            | ✅ Done                                                                | `GET /api/students/export` and `GET /api/guardians/export` (masked, `student:student:read`/`student:guardian:read`) and `GET /api/students/export/unmasked` (`student:student:export_unmasked`, held by no shipped role template). Masking is derived from `@Classification` at write time, not a hand-written column list ([ADR-0027](architecture/adr/0027-export-masking.md)): a Restricted column is omitted from the file entirely in the masked mode, never blanked or marked. Every export — masked included, because a masked file still carries Confidential names — writes `AuditAction.DATA_EXPORTED` naming the columns disclosed and the row count, in its own transaction. Streamed straight into the response as CSV; nothing builds the file in memory first.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Basic dashboards**  | ✅ Done                                                                | `GET /api/dashboard` and its screen: the current session, students enrolled and by class, guardians without a student or students without a guardian, and recent audit activity for whoever holds `platform:audit:read` — each tile gated on its own module's read permission, server-side. The landing screen for most users now; see [Done](#done).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| Audit log             | ✅ Done                                                                | Table, service, `GET /api/audit`, its screen, record counts, and a scheduled seven-year retention purge ([ADR-0026](architecture/adr/0026-audit-retention-purge.md)).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |

**Phase 1 is complete.** All eleven roadmap features are built, every backend controller has a
screen, and it is deployed and verified on Render. The four exit criteria were met before the phase
finished and still are: a school can be configured with a session; students and guardians can be
created or imported; users sign in with the permissions their role grants; and creates, updates,
deletes and logins are audited.

Two things are deliberately _not_ built and are not gaps. **Transport and hostel** sections on the
student record wait for their Phase 4 modules — a need-flag that nothing reads is a field a user
fills in for no reason. And **documents stores nothing on a deployed environment** until the five
`CHALKBASE_STORAGE_*` variables are set on it; the code is complete and the deployment answers a
clean 503 until then ([document storage](operations/document-storage.md)). Both are recorded under
_Decisions taken and not to be reopened_ and in the operations notes rather than left to be
rediscovered.

The honest caveat on "complete": it means every feature the roadmap named exists end to end, not
that it has met a real school. The largest untested thing was volume — the seed used to be a few
dozen students, so no list screen, no paging and no search had ever been exercised at the ~600 rows a
real school has. The `local` seed now admits ~600 (see _Also queued, not blocking_, below), which
makes the exercise possible; it does not itself constitute having done it. Nobody has yet paged
through the student list, timed the guardian phone search, or watched an import at this size against
a database that is not a developer's own laptop — that is still the open question, not a closed one.

**Built in Phase 1 but not on its list**, because the roadmap assumed them rather than naming them:
identity, login and server-side sessions; forced password change, enforced on the server; schema-per-
tenant with a migration orchestrator ([ADR-0011](architecture/adr/0011-schema-per-tenant.md)); server-driven
navigation ([ADR-0008](architecture/adr/0008-server-driven-navigation.md)); the generated API contract
([contracts/README.md](../contracts/README.md) — it has no ADR, which is worth noticing given how much
depends on it); and the Render dev deployment.

## Deployed

A personal dev environment on Render's free tier, deployed from `main` on every merge. The Coolify
path on the Mumbai VPS ([ADR-0015](architecture/adr/0015-deployment-baseline.md)) is unchanged and
remains the production plan.

|              |                                                                                 |
| ------------ | ------------------------------------------------------------------------------- |
| App          | <https://chalkbase-web.onrender.com>                                            |
| API          | <https://chalkbase-api.onrender.com>                                            |
| API explorer | <https://chalkbase-api.onrender.com/swagger-ui.html>                            |
| Database     | the same Supabase project the local profile uses — so the demo school is shared |

The staging pair that briefly sat beside this has been retired — see
[the free-tier runbook](operations/render-free-tier.md#there-is-no-staging-environment) for what
it bought and what it cost. Verify on dev, after merge.

Sign in with school code `DEMO-001` and password `Chalkbase@2026` as `principal`, `classteacher`,
`auditor` (the only one who can open the audit log) or `newteacher` (forced password change).

**Three things that look like faults and are not.**

The first request after fifteen minutes of idle takes **86 to 121 seconds** to a 200 — three
measurements: 86 s, 96 s, 121 s. Free instances sleep, and this one runs a per-tenant Flyway pass on
wake. Nothing in the configuration fixes that; it is the tier.

A backend deploy times out when the service has been asleep for hours, and succeeds when it is warm,
so **hit the health endpoint until it answers before triggering a deploy.** The evidence, the
numbers and the version-skew this leaves behind are in
[the free-tier runbook](operations/render-free-tier.md).

`POST /api/schools` itself requires `school:school:create`, which no shipped role holds — nothing
can call it, on this deployment or any other, until a platform-operator account exists
([ADR-0024](architecture/adr/0024-bootstrap-deployment.md)). The setup key alone does not open
onboarding; a correct `X-Chalkbase-Setup-Key` against that endpoint answers 403, not success. What
actually onboards a school here is `POST /api/schools/bootstrap` (ADR-0024), guarded the same way —
an `X-Chalkbase-Setup-Key` header on this deployment, answering 404 without it, byte-identical to any
unmapped path — and additionally refusing on its own once a school already has an administrator. It
creates the school and its first sign-in in one call; the roster of students and guardians the
`local`-only seeder also builds is still a developer-only convenience, not part of onboarding. The
application refuses to start on `prod` if the key is unset, because a deployment that silently falls
back to open onboarding is worse than one that will not boot.

**Numbers worth keeping.** Spring context startup on a free instance: **~257 s**, from four
measurements (257.2, 257.9, 256.8, 261.1). The per-tenant Flyway pass: **7,955 ms and 8,081 ms**,
one figure per school, now that a `qa_sandbox` schema exists alongside `demo_school`. That
per-school figure is what makes ADR-0011's "move startup migration to a deploy step" expiry
concrete — at ~8 s each, fifty schools would be close to seven minutes of every cold start, and
startup already passes the one-minute trigger the ADR names. Where the 257 s goes — the one-CPU
instance and Spring Modulith's runtime module scan, which the product owner has decided to keep —
is in [the free-tier runbook](operations/render-free-tier.md).

## What to do next

**Only live work is listed here.** Anything finished moves to [Done](#done) — a queue where nine of
thirteen entries are struck through is a queue nobody can read.

### 1. Encryption at rest — machinery and columns both built

~~Encryption at rest — machinery built, columns still to land~~ ✅ Closed. [ADR-0022](architecture/adr/0022-encryption-at-rest.md)'s
mechanism was already built: `EncryptedStringConverter` (AES-GCM, multi-key reads), the
`@Encrypted` marker, `EncryptionBindingTests` beside `ClassificationTests`, and
`EncryptionKeyConfiguration` — `prod` refuses to start without `CHALKBASE_ENCRYPTION_KEY`, `local`
and `test` fall back to a fixed checked-in key. See
[the encryption key](operations/encryption-key.md) for generating and backing one up.

**The columns have now landed too.** `student_medical` and `student_compliance` carry caste,
religion, EWS/BPL/RTE category, CWSN/disability, allergies, chronic conditions, medication and blood
group — encrypted, masked by default, and read-audited on every reveal
(`StudentAudit#RESTRICTED_DATA_REVEALED`). `ClassificationTests.noRestrictedDataHasBeenIntroducedWithoutEncryption`
is deleted, per its own comment, now that the first Restricted field is real.
[ADR-0020](architecture/adr/0020-student-and-guardian-model.md) §2 is amended in place. Guardian
income and an Aadhaar reference are still out — see that section for why. **UDISE+ returns now have
somewhere to write these fields to; the return itself is not built.**

### 2. Subjects

~~The last piece of master data.~~ ✅ Closed. `subject` (flat, paged, retire and reinstate) is
built; see [Done](#done).

### 3. Roles, users, and what a session re-validates

~~`/api/access` is three `@GetMapping`s.~~ ✅ Closed. Session re-validation shipped first
(ADR-0023) so a disabled or locked session stops working the moment it is next used; then the write
endpoints (`UserAccountManagementService`, `RoleManagementService`, `AccessGuardrails`); and now the
two screens, `/settings/users` and `/settings/access`. See [Done](#done).

### 4. Guardian import, documents, dashboards

What is left of Phase 1 after the above. Guardian import specifically needs matching each row
against the existing directory by phone, or it recreates the duplicate problem
[ADR-0020](architecture/adr/0020-student-and-guardian-model.md) §5 exists to prevent.

### 5. The student record's missing sections

~~The student record's missing sections~~ ✅ Closed for contact, medical, previous school/transfer
certificate, compliance and documents — see the Student profile and Documents rows above.
[FR-028](requirements/02-functional-requirements.md) still asks for transport and hostel sections.
Both are Phase 4 modules; on this record they are a need flag at most, not a model of their own — do
not build one here.

### Also queued, not blocking

- Deploy to Coolify on the Hostinger Mumbai box ([ADR-0015](architecture/adr/0015-deployment-baseline.md)).
- ~~Export, which is deliberately unbuilt~~ ✅ Closed. See the Export row above and
  [ADR-0027](architecture/adr/0027-export-masking.md).
- ~~A larger synthetic seed — the `local` profile seeds one school with a few dozen students~~
  ✅ Closed. The `local` seed now admits ~600 students behind ~378 guardian records, with most
  households sharing a guardian across two to four siblings rather than a handful of hand-picked
  examples of it, plus a run of no-guardian children and a run with both parents on file. It goes in
  as one file to the bulk import endpoint ([ADR-0021](architecture/adr/0021-bulk-import.md)) instead
  of a create-and-link sequence per child, which is what keeps six hundred students from turning a
  developer's `./mvnw spring-boot:run` into a coffee break. See
  [running locally](development/running-locally.md). List screens, paging and the guardian phone
  search below now have six hundred rows to be measured against — that measurement itself is not
  done, see below.

## Blocking the first real school

- **Encryption at rest does not exist, and the student record now needs it.** The decisions are
  taken ([ADR-0022](architecture/adr/0022-encryption-at-rest.md)); the code is not written. Caste and community,
  religion, disability/CWSN, EWS/BPL/RTE category, guardian income, APAAR and Aadhaar are Restricted
  under [ADR-0014](architecture/adr/0014-data-classification.md): encrypted at rest, masked in the
  UI, every read audited. None of that machinery is built, so
  [ADR-0020](architecture/adr/0020-student-and-guardian-model.md) leaves those columns out entirely
  rather than store a child's caste in plaintext. **UDISE+ returns need them**, so this is on the
  critical path to onboarding a real school, not a later nicety.
- ~~A session survives its account being disabled or locked.~~ ✅ Closed by
  [ADR-0023](architecture/adr/0023-session-revalidation.md): `SessionStandingFilter` (renamed from
  `PasswordChangeRequiredFilter`, which it supersedes) now re-reads `status` and `locked_until`
  alongside `must_change_password` on every API call, at no extra cost — it is the same indexed
  primary-key row the forced-password check already read, projecting three columns instead of one.
  A disabled or locked account's session is invalidated the moment it is next used. Permissions stay
  resolved once at login ([ADR-0005](architecture/adr/0005-authorization-model.md)), unchanged and
  deliberately not re-read per request. Still **no admin password-reset endpoint** — that is the
  next item below — and `SessionInvalidationService` (also new in ADR-0023) is what it will call to
  dislodge anyone holding the old cookie.
- ~~Audit retention is decided at seven years and not yet enforced.~~ ✅ Closed by
  [ADR-0026](architecture/adr/0026-audit-retention-purge.md): `AuditRetentionPurgeJob` runs per
  tenant, on the same `TenantRegistry.activeSchemas()` fan-out `TenantMigrationRunner` uses at
  startup, deleting rows older than `chalkbase.audit.retention.years` (default 7, configurable, not
  a literal) in bounded batches of `chalkbase.audit.retention.batch-size` (default 500) rather than
  one unbounded `delete`. The purge is itself audited — one `AUDIT_LOG_PURGED` row per school per
  run, naming a count and never which rows, written by a new `AuditActor.system` actor — and cannot
  be recursive, since that row is never older than the cutoff that produced it. Scheduled, never an
  endpoint (ADR-0018 §6 already ruled that out), with `chalkbase.audit.retention.enabled` as the
  operator tripwire. This is also the first `@Scheduled` job in the codebase, so it is the first
  `TaskScheduler` bean too — `AuditRetentionSchedulingConfiguration` turns `@EnableScheduling` on
  application-wide, not only for this job.

## Waiting on a decision

Phase 0 cleared this table, and a later round cleared file storage, audit retention, `.xlsx` and
who may run an unmasked export — all four are recorded where the work they block is described, not
here. What is left is externally blocked rather than undecided.

| Question                                    | Why it matters                                                                                                                                                                                                                                        | Urgency            |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| **TRAI DLT registration**                   | Weeks of paperwork, and nothing can start it retroactively. Blocks SMS fee reminders, absence alerts and any phone-OTP login. Not blocking v1, since v1 ships email and web push only ([ADR-0013](architecture/adr/0013-external-provider-ports.md)). | **Start now**      |
| SMS / WhatsApp provider                     | Chosen once DLT registration completes — that process shows which providers are painless.                                                                                                                                                             | After DLT          |
| Payment gateway                             | Chosen once the pilot school's bank and settlement account are known. Razorpay is the intended first adapter.                                                                                                                                         | Before online fees |
| ~~Production migration off Supabase Seoul~~ | **Settled: not before Phase 4.** See _Decisions taken and not to be reopened_ below.                                                                                                                                                                  | Phase 4            |

## Decisions taken and not to be reopened

Recorded here so they are not asked again. Each was put to the product owner and answered; none is
an open question, and a brief that treats one as undecided is wrong.

**Production stays on Render until Phase 4.** [ADR-0015](architecture/adr/0015-deployment-baseline.md)
names a self-hosted VPS under Coolify as the deployment baseline and that is still the eventual
plan — but the Render dev environment is the _only_ environment until Phase 4, deliberately. Nothing
in Phases 1 to 3 is gated on it, `ops/docker/` and `ops/coolify/` already exist for when it happens,
and standing up a second production path now would be a second thing to keep working for no user.
The Supabase Seoul database therefore stays where it is; the move goes with the box, not before it.
**Do not raise Coolify, the Mumbai VPS or the database migration as pending work again.**

**Row Level Security stays off on the Supabase `public` tables.** Supabase's advisor flags all five
as critical, because they are reachable by the `anon` and `authenticated` roles its client libraries
use — and `spring_session` rows are enough to impersonate a signed-in user. The accepted reasoning:
Chalkbase connects as the `postgres` owner over JDBC and never uses the anon key, the key is not
published anywhere, and the tenant schemas holding student data are not exposed by PostgREST at all,
which is only `public`. So this is an accepted risk on a dev environment with no real school on it,
**not** a thing to ship a real school against. The remediation is four `ALTER TABLE … ENABLE ROW
LEVEL SECURITY` statements and belongs in whatever change first puts real data on a deployment.

**No platform-operator account before Phase 2.** [ADR-0024](architecture/adr/0024-bootstrap-deployment.md)
weighed it and chose the setup-key bootstrap endpoint instead, which closed the actual gap:
onboarding works. `/api/schools` list, get and create still require `school:school:create`, which no
role holds, so they remain callable by nobody — harmless, because the bootstrap endpoint does not go
through them. The operator account needs somewhere to live outside any tenant schema, its own
authentication path, and its own answer to how the first operator is created; that is a Phase 2
shaped piece of work, not a Phase 1 loose end.

**Transport and hostel stay as Phase 4 modules with nothing on the student record.**
[FR-028](requirements/02-functional-requirements.md) lists both as sections and they were considered
as need-flags in Phase 1. Declined: a flag that nothing reads is a field a user has to fill in for
no reason, and the sections land with the modules.

## Done

| What                                                                                                                                                                                                                                                                                                                                                                         | Where                                                                                                                                               |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Requirement pack imported                                                                                                                                                                                                                                                                                                                                                    | [`docs/requirements`](requirements/README.md)                                                                                                       |
| Local run guide and a demo school seeded on the `local` profile                                                                                                                                                                                                                                                                                                              | [running locally](development/running-locally.md)                                                                                                   |
| Monorepo structure, agent instructions, docs skeleton                                                                                                                                                                                                                                                                                                                        | `AGENTS.md`, `docs/`                                                                                                                                |
| Split backend/frontend pipelines, image build checks                                                                                                                                                                                                                                                                                                                         | [`.github/workflows`](../.github/workflows)                                                                                                         |
| `main` protected: no direct pushes, both checks required, admins included                                                                                                                                                                                                                                                                                                    | —                                                                                                                                                   |
| Spring Boot 4.1 · Java 21 · Modulith 2.1, `school` vertical slice, boundary test                                                                                                                                                                                                                                                                                             | `backend/`                                                                                                                                          |
| Angular 22 · TS 6 · Vitest, adaptive shell at three window size classes                                                                                                                                                                                                                                                                                                      | `frontend/`                                                                                                                                         |
| PostgreSQL 17 on Supabase; profiles `local`/`test`/`prod`; Testcontainers                                                                                                                                                                                                                                                                                                    | PR #2                                                                                                                                               |
| One response envelope, module error codes, constraint registry, trace ids                                                                                                                                                                                                                                                                                                    | PR #3                                                                                                                                               |
| Server-driven navigation and hand-built component decisions                                                                                                                                                                                                                                                                                                                  | PR #5                                                                                                                                               |
| Responsive layout, verified at 360 / 700 / 1280                                                                                                                                                                                                                                                                                                                              | PR #6                                                                                                                                               |
| Contrast-verified palette + `contrast-audit.mjs` (44 pairs, light and dark)                                                                                                                                                                                                                                                                                                  | `frontend/src/styles/`                                                                                                                              |
| Design mockups for the first six screens, every state, at 360 and 1280                                                                                                                                                                                                                                                                                                       | [`docs/artifacts`](artifacts/README.md)                                                                                                             |
| Phase 0 closed: board, state, school type, MVP, five workflows, providers, hosting, data policy                                                                                                                                                                                                                                                                              | [Phase 0 decisions](requirements/07-phase-0-decisions.md)                                                                                           |
| Fee ledger, provider ports, data classification, deployment baseline                                                                                                                                                                                                                                                                                                         | ADR-0012…0015                                                                                                                                       |
| Audit log: one generic table per school, field NAMES only, two transaction semantics                                                                                                                                                                                                                                                                                         | [ADR-0018](architecture/adr/0018-audit-log.md)                                                                                                      |
| School profile: tenant-schema table, registry write-back, the settings screen                                                                                                                                                                                                                                                                                                | `school/`, `/settings/school-profile`                                                                                                               |
| Navigation contributions: a module adds a child to another module's section by its dotted id                                                                                                                                                                                                                                                                                 | `NavigationCatalog`                                                                                                                                 |
| Audit log screen at `/audit`: filters, paging, per-row detail, cards below the wide size class                                                                                                                                                                                                                                                                               | `features/audit/`                                                                                                                                   |
| Academics: sessions, the class ladder and its sections, with transactional reordering                                                                                                                                                                                                                                                                                        | [ADR-0019](architecture/adr/0019-classes-and-sections.md)                                                                                           |
| Students, shared guardians and per-session enrolment                                                                                                                                                                                                                                                                                                                         | [ADR-0020](architecture/adr/0020-student-and-guardian-model.md)                                                                                     |
| `@Classification` on every API record, enforced by a build-failing test                                                                                                                                                                                                                                                                                                      | [ADR-0014](architecture/adr/0014-data-classification.md)                                                                                            |
| Guardian search matching a phone number however it was typed, and "which students?"                                                                                                                                                                                                                                                                                          | `guardian.phone_digits`                                                                                                                             |
| Bulk student import: validate first, all-or-nothing, every problem listed                                                                                                                                                                                                                                                                                                    | [ADR-0021](architecture/adr/0021-bulk-import.md)                                                                                                    |
| Signing in lands on the first item of the user's own menu, never a constant                                                                                                                                                                                                                                                                                                  | `landingGuard`, `features/landing/`                                                                                                                 |
| Students filter bar rebuilt to the design: value-printing pills, tinted when set, actions on the title row                                                                                                                                                                                                                                                                   | `cb-select` `pill` variant, `features/students/`                                                                                                    |
| A boot state while `/api/me` is unanswered: the root component says the app is loading, and says so differently after 10s, instead of holding a blank page                                                                                                                                                                                                                   | `app.ts`, `layout/boot-state/`                                                                                                                      |
| `contracts/` regenerated in Actions and committed to the branch, so an endpoint change no longer needs the full backend build on a machine that cannot run it                                                                                                                                                                                                                | [`.github/workflows/contracts.yml`](../.github/workflows/contracts.yml)                                                                             |
| ADR-0008's staleness rule: any `403` refetches `/api/me` and re-renders navigation, sharing one in-flight refetch, before the error is shown                                                                                                                                                                                                                                 | `core/interceptors/api-error-interceptor.ts`, `core/auth/session-bootstrap.ts`                                                                      |
| A build-failing test flags a `CONFIDENTIAL`/`RESTRICTED` DTO accessor passed to a logger, `String.format` or an exception message on the same line                                                                                                                                                                                                                           | `LoggingClassificationTests`                                                                                                                        |
| ~~A staging API and web pair on the `staging` branch, with a second Supabase project of its own~~ — built, used to prove ADR-0024's bootstrap against a genuinely empty database, then retired                                                                                                                                                                                      | [render.yaml](../render.yaml), [free-tier runbook](operations/render-free-tier.md)                                                                  |
| Session re-validation: account status and lockout re-read on every API call, at no extra cost; sessions can be ended on demand                                                                                                                                                                                                                                               | [ADR-0023](architecture/adr/0023-session-revalidation.md)                                                                                           |
| User account lifecycle: create, deactivate, reactivate, unlock and admin password reset, all at `/api/access/users`                                                                                                                                                                                                                                                          | `UserAccountManagementService`, `UserAccountController`                                                                                             |
| Role management: create a role, replace its permission set, grant or revoke it for a user, with guards against privilege escalation and against locking a school out of its own access                                                                                                                                                                                       | `RoleManagementService`, `AccessGuardrails`, `AccessController`                                                                                     |
| Encryption-at-rest machinery: AES-GCM `EncryptedStringConverter`, `@Encrypted`, the `EncryptionBindingTests` binding it to `@Classification`, and `EncryptionKeyConfiguration`                                                                                                                                                                                               | [ADR-0022](architecture/adr/0022-encryption-at-rest.md)                                                                                             |
| Subjects: a flat, paged catalogue, retire and reinstate, the last piece of Phase 1 master data                                                                                                                                                                                                                                                                               | `academics/`                                                                                                                                        |
| `POST /api/schools/bootstrap`: a fresh deployment can be onboarded over HTTP — school and first administrator, atomically from the caller's side, refusing a second run                                                                                                                                                                                                      | [ADR-0024](architecture/adr/0024-bootstrap-deployment.md)                                                                                           |
| The account roster and roles/access screens: `/settings/users` (create, deactivate, reactivate, unlock, reset password, each with the confirmation and one-time password reveal the write endpoints need) and `/settings/access` (permission catalogue, this school's roles, create and edit a role, who holds it, grant and revoke for an account)                          | `features/access/`                                                                                                                                  |
| Document storage: a `StorageService` port, a real filesystem adapter for `local`/`test`, and attaching a certificate, photo, signature or other document to a student — upload, list, proxied download, edit, delete, all audited                                                                                                                                            | [ADR-0025](architecture/adr/0025-document-storage.md), `document/`                                                                                  |
| The first basic dashboard: `GET /api/dashboard`, gated tile by tile through two new SPIs (`AcademicsDashboardContributor`, `StudentDashboardContributor`) so the shared kernel never imports a feature module, and `student.api.StudentLookup`, the module's first cross-module read interface                                                                               | `platform/dashboard/`, `student/api/StudentLookup.java`                                                                                             |
| Audit retention purge: seven years, per tenant, batched, and audited without being recursive                                                                                                                                                                                                                                                                                 | [ADR-0026](architecture/adr/0026-audit-retention-purge.md), `AuditRetentionPurgeJob`                                                                |
| Document storage's S3-compatible adapter (AWS SDK v2, path-style addressing, `local`/`test` untouched) and a documents section on the student record: upload with progress/failure/size-refusal states, list, proxied download, edit, verify, delete                                                                                                                         | [ADR-0025](architecture/adr/0025-document-storage.md) amendment, `platform/storage/S3StorageService.java`, `features/students/student-documents.ts` |
| The users roster on the server-driven menu: `settings.users`, gated on `identity:user:read`                                                                                                                                                                                                                                                                                  | `identity/infrastructure/IdentityNavigation.java`, `core/navigation/nav-routes.ts`                                                                  |
| The role-edit impact preview: who holds a role, and whether saving signs them out immediately or waits for their next login                                                                                                                                                                                                                                                  | `features/access/access-roles.ts`                                                                                                                   |
| Session cleanup investigated: `JdbcIndexedSessionRepository` already purges `public.spring_session` itself, on its own scheduler, on by default — no second job needed, made explicit in configuration                                                                                                                                                                       | [ADR-0028](architecture/adr/0028-session-cleanup-is-already-handled.md)                                                                             |
| Student and guardian CSV export, masked by classification with an audited unmasked permission of its own                                                                                                                                                                                                                                                                     | [ADR-0027](architecture/adr/0027-export-masking.md), `platform/export/`, `StudentExportService`                                                     |
| Global reference data: states move to `public.state`, seeded from code, cached in memory, read through `GET /api/reference/states`; boards stay the `school` enum but drop their frontend-side label copy at `GET /api/schools/boards`; the audit action filter examined and deliberately left as is, with the index it would need first named                               | [ADR-0029](architecture/adr/0029-reference-data.md), `platform/reference/`                                                                          |
| **Phase 2, first feature:** daily attendance end to end — mark a section, view it, correction request and admin approval, locking at end of day plus 24 hours. One table shaped for the period-wise grain too, with no write path for it yet. The marking screen is card-based at every width, not only below the wide breakpoint: its primary user is a teacher on a phone. | [ADR-0030](architecture/adr/0030-attendance-grain-and-lock.md), `attendance/`, `features/attendance/`                                               |
| A school carries an IANA time zone, defaulted to `Asia/Kolkata`: authoritative on the profile, copied to the registry and the session, validated against `java.time.ZoneId`; the audit log renders every timestamp in it instead of the reader's device zone | [ADR-0032](architecture/adr/0032-school-timezone.md), `school/`, `features/audit/audit-log.ts` |
| Fixed on `prod`: `GET /api/schools/boards` was hidden behind `SetupKeyFilter` along with the rest of `/api/schools/**`, so the school-profile form's Board picker 404'd for every real user on every deployed environment; the filter now names it an explicit exemption, pinned by a `prod`-profile test | [ADR-0029 amendment](architecture/adr/0029-reference-data.md), `SetupKeyFilter`, `SetupKeyFilterTests` |

| The design drift assessment's three cheap, real fixes: the active nav item tinted `--cb-primary-surface` instead of reading as unselected, the page gutter restored to 32px from `from-expanded` up, and shared `cb-card`/`cb-badge` components adopted in place of the hand-rolled surfaces in 18 feature stylesheets (a handful of forms styled as a surface, and the responsive card-to-table rows on four list screens, deliberately left for a follow-up — see the assessment). Also found and fixed while driving the app: the primary nav rendered top-level items only, so a section's own screens (`academics.classes`, `settings.users`, …) were reachable only by typing a URL — the rail and sidebar now render a container's children beside it, not only inside the compact-width More sheet. | `layout/main-layout/`, `shared/components/card/`, `shared/components/badge/`, [the assessment](design-drift-assessment.md) |

| Fixed, verified live: `RESTRICTED_DATA_REVEALED` audit rows recorded that a student's medical or compliance reveal happened but never which of the Restricted fields it disclosed (`changedFields: []` on every one) — `StudentRecordService#revealMedical`/`revealCompliance` now name whichever Restricted fields actually held a value for that student, the same "present fields only" rule a first-time save already used, via a new `AuditService.recordSecurityEvent` overload for a single-record disclosure with no row count to state | [ADR-0014](architecture/adr/0014-data-classification.md), [ADR-0018](architecture/adr/0018-audit-log.md), `StudentRecordService`, `AuditService` |

## What is left on the frontend

Every backend controller has a screen except one, so this list is short and specific. It exists
because "the endpoint exists" and "a person can do it" are different claims, and this file has never
separated them.

| Gap                                                                                                                                                                                                                                                                                                                                  | Where                                                                              |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| ~~Export has no screen yet.~~ ✅ Closed. The students and guardians lists carry an export action, and a dialog that makes the masked/unmasked choice and its audit consequence explicit.                                                                                                                                             | `features/students/`                                                               |
| ~~The users roster has no menu entry.~~ ✅ Closed. `IdentityNavigation` now emits `settings.users`, gated on `identity:user:read` rather than `identity:user:manage` — `AUDITOR` and `VICE_PRINCIPAL` hold the first without the second, and can legitimately see the roster without acting on it — and `nav-routes.ts` resolves it. | `identity/infrastructure/IdentityNavigation.java`, `core/navigation/nav-routes.ts` |
| ~~No impact preview when editing a role.~~ ✅ Closed. The edit form now shows who holds the role and, from ADR-0023, whether saving signs them out immediately (removing a permission) or waits for their next login (adding one) — built from `GET /api/access/roles/{id}/holders`, no new endpoint.                                | `features/access/access-roles.ts`                                                  |
| ~~The design drift the assessment recorded is still open.~~ ✅ Closed for the three cheap items: the active nav item tints, the gutter is 32px from `from-expanded`, and `cb-card`/`cb-badge` replace the hand-rolled surfaces on the great majority of the 18 affected screens (a handful of card-shaped forms, and four screens' responsive card-to-table rows, are noted in the assessment as left for later rather than adopted silently). | [the assessment](design-drift-assessment.md) |

Transport and hostel sections on the student record are **not** on this list: they are Phase 4
modules, and [FR-028](requirements/02-functional-requirements.md) wants a need flag rather than a
model until those exist.

## Known gaps and debt

Recorded so they are decided rather than discovered.

- ~~There is no HTTP timeout anywhere in the app.~~ ✅ Closed. `timeoutInterceptor`
  (`core/interceptors/timeout-interceptor.ts`) applies one number, one place: 150 seconds, chosen
  against the Render free tier's own measured worst case — a cold wake answers in 86 to 121
  seconds, so a shorter number would fail a save that was only ever slow, not stuck, on the one
  environment this app is actually deployed to
  ([the free-tier runbook](operations/render-free-tier.md)). `GET /api/me` stays exempt, exactly as
  this bullet already decided: a timeout there would still resolve `authGuard` as "not signed in"
  and loop the user between two screens. Every other call now fails instead of hanging, and every
  screen's existing generic-failure branch — already reached by an ordinary network error, never
  reading anything but `apiErrorCode()` — is what shows the retry: nothing is cleared but the
  spinner, because a form here only ever resets itself on success.
- ~~The app showed a blank white page for the whole of the first `/api/me`~~ ✅ Closed. `authGuard`
  guards the shell route and the router renders nothing until its guards resolve, so a bare
  `<router-outlet />` root meant an empty document for the length of that call — 121 seconds
  against a sleeping API, with `<app-root>` present and no console error, which is why it read as a
  crash. Three comments in `app.config.ts`, `auth-guard.ts` and `session-bootstrap.ts` asserted the
  shell painted its chrome meanwhile; nothing had ever been built to do it, and the comments are
  what kept anyone from noticing. `SessionBootstrap.bootstrapping` now drives a boot state that
  `App` renders outside the outlet — held back 600ms so a warm load never sees it, escalating at
  10s to say the wait is unusual. The lesson worth keeping is the one about the comments: a comment
  that defends behaviour nobody wrote is worse than no comment.
- ~~`/api/schools/**` is still `permitAll`, because onboarding a campus has no caller to
  authenticate yet. It is CSRF-exempt for exactly as long as that is true... Closes with a
  platform-operator account.~~ Corrected: this was stale in both directions.
  `SchoolController#list`, `#get` and `#create` require `school:school:create`, which no shipped
  role holds, so they were never reachable and the CSRF exemption on them was never live authority a
  forged request could spend. [ADR-0024](architecture/adr/0024-bootstrap-deployment.md) adds
  `POST /api/schools/bootstrap`, which genuinely is `permitAll` and genuinely is CSRF-exempt, by
  design rather than by accident: it is how a fresh deployment gets its first school and
  administrator, guarded instead by the setup key on `prod` and by refusing a second run for a
  school that already has one. ADR-0024 looked at a platform-operator account and decided against it
  for now, as the larger fix for a smaller problem.
- Durable cross-module events are not enabled; the Modulith event registry needs its own migration,
  which lands with the first published domain event (ADR-0001).
- The generated OpenAPI client is not wired up; `frontend/src/app/core/api/models.ts` is hand-written
  and mirrors the backend by hand (ADR-0007).
- `contrast-audit.mjs` is run by hand. Make it a CI step once the palette settles.
- ~~The built UI and the mockups differ, and most of the difference is not drift.~~ The three
  cheap, real items [the assessment](design-drift-assessment.md) named are closed: the active nav
  item is `--cb-primary-surface` rather than `--cb-bg`, so the sidebar reads as having something
  selected again; the page content gutter is 32px from `from-expanded` up, matching the mockups,
  rather than 16px at every width; and `shared/components/card/` and `shared/components/badge/`
  exist and are adopted across the great majority of the screens that used to hand-roll each —
  including fixing `--cb-shadow-1`, which only one of the thirteen original cards carried, and the
  pill radius, which none of the six original badges got right. Left alone, and said so in the
  assessment rather than silently: a handful of forms styled as a card, where adopting the
  component means nesting it inside the `<form>` rather than swapping the tag, and the responsive
  card-below/table-above rows on `student-list`, `subjects`, `user-roster` and `audit-log`, which
  are a data-table component's job (ADR-0009 names one as a future, larger undertaking) rather than
  a plain surface's. The five things the assessment called not worth fixing and the six obsolete
  mockups are unchanged — the design should move on those, not the code. Also found while driving
  the app, outside the assessment's own list: the primary nav rendered top-level items only, so a
  container's own screens were reachable only by URL — see the Done row above.
- `nav-routes.ts` registers `students.import`, but no backend `NavigationProvider` emits that id, so
  the menu will never show it — the import screen is reachable only from the student-list link.
  Harmless (registering ahead of the backend is what that file is for), but it is not a menu entry
  anyone should wait for.
- **Hibernate was logging the whole failed INSERT, values included, at WARN** — one duplicate
  admission number put a child's name, date of birth and gender in the log, in every environment.
  `org.hibernate.orm.jdbc.error` is now at ERROR, and the unmapped-constraint branch of
  `GlobalExceptionHandler` logs the constraint's _name_ instead of the exception, because
  PostgreSQL's `DETAIL` line carries the values that clashed. Both have tests.
- ~~`linkedStudentCount` cannot be expanded into "which students"~~ ✅ Closed. It expands into the
  list, and the phone search now matches digits to digits — it had been comparing the raw stored
  value, so a guardian entered `+919876543210` never matched a clerk typing `98765 43210`. The one
  defence for ADR-0020 §5 had not been working on the field that matters. There is still no
  server-side uniqueness, deliberately: two people genuinely share a phone number, so the create
  form warns and offers rather than refusing.
- **The guardian search is a sequential scan and that is a decision.** `like '%digits%'` is
  unanchored, which no btree index can serve, so none was created — an index that is never used
  reads to the next person as though the search were indexed, and under ADR-0011 it would be created
  once per school forever for nothing. The answer when it stops being fine is a `pg_trgm` GIN index;
  the extension is available on the dev database and not installed, and installing it is a
  database-wide change wanting a measurement behind it. **The demo school's ~378 guardians are that
  measurement's first opportunity** — nobody has yet run `explain analyze` on the guardian search
  against a school this size, on a database sized like the shared dev Supabase project rather than a
  developer's laptop. That is the thing to actually do, not this note: time the search with the seed
  in place, at ~400 and then again once a school is closer to the 2,000-row cap a single import
  allows, before deciding whether the `pg_trgm` index is still a "when," not a "now."
- **The import reads CSV, not `.xlsx`.** The requirement says "import from Excel"; every Excel can
  _Save As_ CSV, and reading `.xlsx` directly needs Apache POI — megabytes of dependency and real CVE
  surface, which AGENTS rule 8 says to ask about. A `.xlsx` upload is detected by its magic bytes and
  refused with instructions rather than a parse error. **Asked and answered: POI is not approved.**
  The dependency's size and CVE surface are not worth buying while every Excel can _Save As_ CSV, so
  the magic-byte refusal is the shipped behaviour rather than a placeholder for it. Revisit only if
  a real school office reports "Save as CSV" as a genuine barrier — the change is small and sits
  behind the same endpoint.
- **Guardian import is one guardian per row** (ADR-0021 §4, resolved from the earlier gap this
  bullet used to describe). A student needing a second guardian on record — a mother, once the
  father is already in from the file — gets one from that child's own record afterwards, the same
  way any guardian is added by hand. Matching is by phone, reusing the directory search's own
  digit-stripping rule; a phone number shared under two different names, in the file or against the
  directory, refuses the row rather than guessing which person was meant.
- **The upload limit is coupled across two files.** `spring.servlet.multipart.*` is set below nginx's
  `client_max_body_size` so Spring is always the one refusing, in the ADR-0007 envelope; a request
  refused by nginx returns HTML and may reach the browser without CORS headers, so the client sees a
  network failure rather than "that file is too large". Raise both or neither.
- `MaxUploadSizeExceededException → VAL_003` is untested: `MockMvcRequestBuilders.multipart()` builds
  the request object directly and never runs the multipart resolver, so the limit cannot be exercised
  from MockMvc at all.
- **A module's `api/` records cannot actually be used by another module.** Found while building the
  demo seeder, and confirmed by `ModularityTests`: `SaveStudentRequest` names
  `student.domain.Gender` and `StudentStatus`, `CreateSchoolRequest` names `school.domain.Board`,
  `LinkGuardianRequest` names `GuardianRelation` — all of them types the owning module does not
  expose. So the named interface leaks types that are themselves not exposed, and referencing the
  record from outside fails the boundary test:

  > `Module 'platform' depends on non-exposed type in.chalkbase.student.domain.Gender within module 'student'!`

  The fix is small — move those four enums into their `api/` packages, or mark them
  `@NamedInterface` — but it is a contract change and was not worth making from inside a dev tool.
  It matters the first time one feature module genuinely needs another's request shape.

- `guardian.phone` is `varchar(20)`. `+91 98765 43210` fits at 16; a longer international number
  with an extension would not.
- Startup migration measured **9.4 s for two schools** against the Seoul database — ~4.7 s each,
  dominated by round trips. That was from a developer machine; from the Render free instance the
  same pass costs ~8 s per school ([runbook](operations/render-free-tier.md)) — the same shape on a
  slower client rather than a different finding. Fifty schools would be four to seven minutes of
  startup depending on where it runs. Mumbai (ADR-0015) will cut it sharply; the linear shape does
  not change, so the ADR-0011 expiry stands.
- Startup migration is deliberate and has a recorded expiry — move it to a deploy step once startup
  passes ~1 minute, a second replica appears, or tenant count passes ~50 (ADR-0011).
- The tenant is a **campus**, not a group: a multi-campus trust gets one schema per campus, with the
  group as a row in `public` (ADR-0011). Group-wide reporting is therefore a fan-out, and primary
  keys must be globally unique — UUIDv7, already the convention. Not exercised by the MVP, which is
  scoped to a single-campus school.

- ~~The navigation contract still has no test across the two sides.~~ ✅ Closed. That bullet was
  itself already stale by the time it was picked up: `settings.access` and `audit` had since been
  mapped on the frontend for a different reason (the screens shipped), and `settings.users` had
  gone missing from the frontend registry for a while with nothing to catch it — which is the
  argument for this check, not a hypothetical. `NavigationContractExportTests` now writes every id
  the running backend declares to `contracts/navigation-ids.json`, the same way `openapi.json` is
  exported, and `.github/workflows/navigation-contract.yml` compares that against
  `nav-routes.ts`. A backend id absent from the frontend is an ERROR unless it is named in
  `tools/navigation-contract/allowlist.json` with a required reason (today, only
  `students.documents`, which has no screen yet); a frontend id no backend provider emits (`schools`,
  `students.import`) is a WARNING, never a failure, because `NavigationStore` only resolves an id
  the server actually sends; an allowlist entry that stops matching a real gap is an ERROR too, so
  the allowlist cannot go stale unnoticed. `tools/navigation-contract/check.test.mjs` proves the
  distinction with fixtures, including a `settings.acess`-style typo reported as an error. The
  frontend's `TODO(contract)` in `nav-routes.ts` is resolved and removed — the backend had none in
  code despite this bullet's earlier claim of "a matching `TODO(contract)`" on both sides; only the
  frontend one ever existed.
- ~~ADR-0008's staleness rule is not implemented~~ ✅ Closed. Any `403` other than one on `/api/me`
  itself now makes `apiErrorInterceptor` call `SessionBootstrap.refreshAfterForbidden()` before the
  error reaches the screen: it refetches `/api/me`, re-renders navigation from the answer, and only
  then rethrows. Concurrent `403`s share the one in-flight refetch; a failure of the refetch itself
  (including a `401` that turns out to mean the session is actually gone) never replaces the
  original error, it only decides whether the user also lands on `/login`. A refetch that comes back
  with the same `permissionsVersion` still shows the same error — see the comment at the call site
  for why that case is not reworded.
- ~~Expired sessions are never purged.~~ ✅ Closed — the claim itself was wrong.
  `JdbcIndexedSessionRepository` has purged `public.spring_session` on its own dedicated
  scheduler since the session store shipped, on by default, independent of this codebase's
  `@EnableScheduling`/`TaskScheduler` (ADR-0026) and needing no `TenantRegistry` fan-out —
  the table is shared across every school, not per tenant. See
  [ADR-0028](architecture/adr/0028-session-cleanup-is-already-handled.md), which makes the
  existing schedule explicit in configuration rather than adding a second job.
- ~~The forced password change is enforced at two points, not everywhere.~~ ✅ Closed.
  `passwordChangeGuard` (`core/auth/password-change-guard.ts`) sits on the shell route in
  `app.routes.ts`, after `authGuard` in the same `canActivate` array, so it runs in front of every
  child route rather than in front of one reload path — a deep link to `/students/1234` now lands
  on `/change-password` the same way a reload of `/` already did. The login screen's own redirect
  and `landingGuard`'s are both still there and still correct; this closes the gap between them
  rather than replacing either. `landingGuard`'s own `mustChangePassword` check is now unreachable
  in practice, left alone rather than removed here because `core/navigation` belongs to a different
  lane while several are running in parallel.
- **A deactivated class keeps its name.** `uq_school_class_name` does not account for `active`, and
  there is no delete (ADR-0019), so a school that retires "Class 5" and later wants it back must
  reactivate that row rather than create a new one. That is the intended behaviour, but it makes
  showing inactive classes findable a correctness concern rather than a nicety — a user who cannot
  see the retired row hits a name clash they cannot explain.
- **The same trap applies to a retired subject.** `uq_subject_name` and `uq_subject_code` do not
  account for `active` either, so a school that retires "Hindi" and later adds it back must
  reinstate that row rather than create a second one — and the subjects screen names the row
  already holding a clash for exactly this reason, the same fix the classes screen still lacks.
- **`AGENTS.md` claimed two things that were not true** and now does not: indexes are `idx_`, not
  `ix_`, and the `@Classification` annotation ADR-0014 describes does not exist, so nothing fails
  the build for an unclassified field. Both were found by agents reading the file and trying to
  follow it. A rule that lies is worse than no rule; if ADR-0014's enforcement is wanted, it is
  still worth building while the DTO count is small.
- ~~The audit log has no retention period and no purge job.~~ ✅ Closed by
  [ADR-0026](architecture/adr/0026-audit-retention-purge.md). Seven years, one period for every
  classification tier rather than a schedule per tier — a per-category schedule stays possible
  later without a schema change, it is simply not what was built. See the **Blocking the first real
  school** section above for how the purge itself runs.
- ~~Indian states are a hardcoded list in the school-profile form.~~ ✅ Closed by
  [ADR-0029](architecture/adr/0029-reference-data.md): `public.state`, seeded from `IndianStates.java`
  the way `PermissionCatalog` seeds `permission`, read through `GET /api/reference/states` and
  cached in memory. The board list beside it is closed too, but differently — `Board` stays the
  `school` module's enum rather than becoming a table (ADR-0029 explains why), and
  `GET /api/schools/boards` serves its labels from there instead. **The audit screen's action filter
  is not closed and, on inspection, should not be yet**: `audit_event` carries no index on `action`
  (only on `occurred_at`, `actor_id` and `entity_type`/`entity_id`), so `select distinct action` is a
  full scan of a table that is append-only, unbounded until a seven-year purge, and gets more
  expensive every year a school stays on the platform — a bad trade on the one screen an
  administrator opens during an incident. It lists the actions this build ships, so a verb a future
  module invents is filterable by neither name nor dropdown — those rows still list, label legibly
  and are reachable by actor or date. `idx_audit_event_action` first, then this is worth revisiting.
- **The audit log's date-range filter still reads "a day" off the browser's own clock**, not the
  school's timezone ([ADR-0032](architecture/adr/0032-school-timezone.md)): the column and the row
  detail render in the school's zone now, but `instantAtStartOfDay`/`instantAfter` still build
  midnight from the reader's device. Converting that needs either the `Temporal` API or a hand-rolled
  DST-safe offset calculation — real work for a narrower payoff, since "the 5th" read as the reader's
  own local day is a defensible reading of the filter, unlike a bare "14:32" silently meaning the
  wrong clock. Left for whoever next opens this screen.
- **The audit log still uses the labelled-form filter pattern** the students list has just left
  behind (`features/audit/audit-log.html`). That is deliberate for now, not an oversight: two of its
  three filters are dates, which have no sensible "current value" to print on a pill, and the
  screen was out of scope for the change that rebuilt the students bar. The two screens now look
  different from each other, which is the cost of stopping at one. Worth revisiting when a third
  filter bar appears — at that point the pill row is a shared thing, not a variant on a control.
- The per-component style budget was raised from 4 kB / 8 kB to **8 kB / 12 kB**. The old warning
  threshold was calibrated when every screen was a simple form; a screen that honours ADR-0010 with
  a table above the wide breakpoint and cards below legitimately costs more, and the audit log is
  the first of those at 6.3 kB. Raised deliberately rather than left as a permanent warning, because
  a build that always warns is a build nobody reads. The shared page scaffolding was extracted to
  `styles/_page.scss` in the same change — three screens had their own copy and the copies had
  already drifted.
- `GET /api/access/users` answers `UserSummary` — id, display name, status — for every account in
  one call, and nothing else: no `lockedUntil`, no `lastLoginAt`. Only the four per-account write
  endpoints answer the richer `UserAccountResponse`. So the account roster (`/settings/users`)
  cannot paint a lockout badge per row without an N+1 request per account, which every list screen
  in this app avoids; **Clear lockout** is offered on every active account instead, and the
  confirmation that follows is where the truth about whether anything was locked actually comes
  from. Closing it properly means a paged, filterable roster endpoint carrying the fuller shape,
  which is a bigger change than the screen that surfaced the gap.
- **Two of `ScopeType`'s values have nothing to scope to.** `CAMPUS` and `DEPARTMENT` are legal
  values on the wire (ADR-0005 names them as a future capability) but no module owns a campus or a
  department record, so the grant screen at `/settings/access` does not offer them — a free-text
  UUID box would be a control nobody could fill in correctly. Closes when a module exists to look
  one up against.
- **`identity:user:read` and `identity:role:manage` are separate permissions, and the access
  screen's grant picker needs the first to list accounts by name.** A role manager who does not
  also hold "View users" cannot pick an account from a list, so the picker falls back to a plain id
  field — usable from the roster's own "Manage roles" link (which carries only an id, never a name,
  in its query parameter — a display name is Confidential under ADR-0014), but not a name-based
  search on its own. Every shipped template that holds `identity:role:manage` also holds
  `identity:user:read` (`RoleTemplates`), so this is a gap for a school's own invented roles rather
  than the common case.
- **`IdentityNavigation` declares `settings.access` but not `settings.users`.** The roles and access
  screen is on the server-driven menu; the account roster is not, and is reached from a link on the
  access screen or by typing the URL — the same trade `students.import` already made in the other
  direction (an id registered here ahead of the backend emitting it). Adding a `settings.users`
  navigation id is a small backend change and belongs with whichever lane is next in `identity`.
- **Documents has no working adapter on the deployed environment yet.** ADR-0025's amendment
  picked the S3-compatible adapter (AWS SDK v2) and it is built, tested and wired to a
  documents section on the student record, but `prod` still answers a clean 503 until the five
  `CHALKBASE_STORAGE_*` variables are set on Render — see
  [docs/operations/document-storage.md](operations/document-storage.md) for exactly which ones
  and where each value comes from in the Supabase dashboard. A document uploaded on `local` or
  `test` works end to end today; nothing uploaded on the deployed environment persists until
  that one operational step happens.
- **Attendance (Phase 2) has no academic calendar to check a date against.**
  [FR-015](requirements/02-functional-requirements.md) asks for one and it is not built —
  confirmed while building `attendance`, which is the first feature Phase 0 decision 8 makes
  actually depend on it (working days, week start and holidays should come from the calendar,
  not per-teacher habit). Rather than invent a calendar module for this lane, attendance marks
  any date up to today, including a Sunday or a school holiday — `HOLIDAY` is a status precisely
  so a school can record one by hand — and does not know which days a school actually runs.
  This is a recorded gap, not a silent one: see [ADR-0030](architecture/adr/0030-attendance-grain-and-lock.md#what-this-build-does-not-do).
  The eligibility and short-attendance reports FR-049 asks for need a working-days denominator
  this build cannot supply until the calendar exists.
- **Attendance FR-047 (leave applications), FR-048 (absence alerts) and FR-050 (biometric/RFID
  import) are deliberately not built.** Leave applications need a parent-facing portal that does
  not exist; alerts need the communication module and channel ports
  ([ADR-0013](architecture/adr/0013-external-provider-ports.md)), which Phase 0 decision 8 itself
  says must fire on a schedule against locked data rather than per mark — the lock
  [ADR-0030](architecture/adr/0030-attendance-grain-and-lock.md) defines is what that job will
  read once built; biometric import is P2 and has no device integration to import from yet.
- **Attendance has no section-scoped permission narrowing.** `attendance:mark:manage` reaches
  every section in the school, not only a class teacher's own — `platform.security.AccessScope`
  has a `SECTION` scope type, but nothing in the codebase resolves it into a query filter yet,
  the same gap `RoleTemplates` already notes for `student:guardian:read` on `CLASS_TEACHER`.
  Honest about what the authorization model can currently express, not a regression this lane
  introduced.
- ~~A school never received a permission added to a role template after the school was
  onboarded.~~ ✅ Closed by [ADR-0031](architecture/adr/0031-role-template-reconciliation.md).
  `RoleTemplateInstaller` used to skip any role whose code already existed, permissions and all, so
  `DEMO-001` (onboarded early) held 12 permissions against a 24-entry catalogue while a school
  onboarded today got all of them — and there was no in-product way back, because
  `AccessGuardrails` correctly refuses to let a role manager grant a permission they do not
  themselves hold. The installer now reconciles: a role a school has never edited (`role.customised
= false`) receives whatever the template has that it lacks, on every startup; a role the school
  has edited (`RoleManagementService.updateRolePermissions` sets `customised = true`, permanently)
  is never touched again. Never removes a permission. Takes effect at the affected accounts' next
  login (ADR-0023). **Still not built:** the "new permissions available" review prompt ADR-0005 §2
  originally imagined — an edited role gets no notice that a template it was copied from has grown;
  its admin adds the permission by hand, same as any permission that role does not automatically
  receive. Proven at three levels: `RoleTemplateInstallerTests` against the installer directly,
  `AccessControlTests` through `SchoolProvisioning` (replacing a test that had asserted the pre-fix
  behaviour as correct), and `RoleManagementTests` through the real permissions endpoint, confirming
  a role edited through the product — not a raw SQL edit — is the one that stays protected.

## Keeping this honest

- Update this file in the same PR as the change.
- Moving something to Done means it is merged and verified, not written.
- If something here is stale, that is a bug — fix the file, not the memory of it.
