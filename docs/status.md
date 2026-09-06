# Where the project is

Living status. **Updated in the same pull request as the work it describes** — a status file that is
updated "later" is worse than none, because people trust it.

Last updated: 2026-09-06 · Roadmap phase: **1** — Phase 0 is complete
([Phase definitions](requirements/06-roadmap-and-mvp.md) · [Phase 0 decisions](requirements/07-phase-0-decisions.md))

## At a glance

| Area | State |
|---|---|
| Repository, CI, branch protection | ✅ Done |
| Backend skeleton (Spring Boot 4.1, Modulith) | ✅ Done |
| Frontend skeleton (Angular 22, adaptive shell) | ✅ Done |
| PostgreSQL, profiles, Testcontainers | ✅ Done |
| API response envelope and error handling | ✅ Done |
| Design tokens and palette | ✅ Done |
| Screen designs for the first six screens | ✅ Done |
| Architecture decisions (ADR-0001…0022) | ✅ Done |
| **Phase 0 discovery — all 13 deliverables** | ✅ Done |
| Identity: login, sessions, forced password change | ✅ Done |
| Permissions, roles, scoped grants | ✅ Done |
| Server-driven navigation (`GET /api/me`) | ✅ Done |
| Schema-per-tenant: registry, migration orchestrator | ✅ Done |
| Audit log (FR-008) — table, service, `GET /api/audit`, and its screen | ✅ Done |
| School profile — `GET`/`PUT /api/school/profile` and its screen | ✅ Done |
| Shared UI components | ✅ Button, field, inputs, checkbox, select, bottom sheet |
| Academic sessions, classes and sections | ✅ Done |
| Students, guardians and enrolment | ✅ Done |
| Deployment to Coolify | ⬜ Not started |

## Done

| What | Where |
|---|---|
| Requirement pack imported | [`docs/requirements`](requirements/README.md) |
| Monorepo structure, agent instructions, docs skeleton | `AGENTS.md`, `docs/` |
| Split backend/frontend pipelines, image build checks | [`.github/workflows`](../.github/workflows) |
| `main` protected: no direct pushes, both checks required, admins included | — |
| Spring Boot 4.1 · Java 21 · Modulith 2.1, `school` vertical slice, boundary test | `backend/` |
| Angular 22 · TS 6 · Vitest, adaptive shell at three window size classes | `frontend/` |
| PostgreSQL 17 on Supabase; profiles `local`/`test`/`prod`; Testcontainers | PR #2 |
| One response envelope, module error codes, constraint registry, trace ids | PR #3 |
| Server-driven navigation and hand-built component decisions | PR #5 |
| Responsive layout, verified at 360 / 700 / 1280 | PR #6 |
| Contrast-verified palette + `contrast-audit.mjs` (44 pairs, light and dark) | `frontend/src/styles/` |
| Design mockups for the first six screens, every state, at 360 and 1280 | [`docs/artifacts`](artifacts/README.md) |
| Phase 0 closed: board, state, school type, MVP, five workflows, providers, hosting, data policy | [Phase 0 decisions](requirements/07-phase-0-decisions.md) |
| Fee ledger, provider ports, data classification, deployment baseline | ADR-0012…0015 |
| Audit log: one generic table per school, field NAMES only, two transaction semantics | [ADR-0018](architecture/adr/0018-audit-log.md) |
| School profile: tenant-schema table, registry write-back, the settings screen | `school/`, `/settings/school-profile` |
| Navigation contributions: a module adds a child to another module's section by its dotted id | `NavigationCatalog` |
| Audit log screen at `/audit`: filters, paging, per-row detail, cards below the wide size class | `features/audit/` |
| Academics: sessions, the class ladder and its sections, with transactional reordering | [ADR-0019](architecture/adr/0019-classes-and-sections.md) |
| Students, shared guardians and per-session enrolment | [ADR-0020](architecture/adr/0020-student-and-guardian-model.md) |
| `@Classification` on every API record, enforced by a build-failing test | [ADR-0014](architecture/adr/0014-data-classification.md) |
| Guardian search matching a phone number however it was typed, and "which students?" | `guardian.phone_digits` |
| Bulk student import: validate first, all-or-nothing, every problem listed | [ADR-0021](architecture/adr/0021-bulk-import.md) |

## Next, in order

Each line says what it unblocks, because the order is not arbitrary.

1. ~~**Identity — schema and login.**~~ ✅ Done. `user_account`, `user_identifier`, `user_credential`
   per tenant; Spring Session in `public`; login, logout and forced password change; the session
   filter binding `TenantContext`; `permitAll` off except onboarding (ADR-0017).
2. ~~**Permissions, roles, grants.**~~ ✅ Done. Permission registry per module seeded per tenant,
   twelve role templates copied on onboarding, scoped grants with validity windows, effective
   permissions resolved once at login, `@PreAuthorize` enforcement, and the test that fails the
   build when a controller method carries no authorization annotation (ADR-0005).
3. ~~**`GET /api/me`**~~ ✅ Done. Bootstrap returns user, school, permissions, a
   `permissionsVersion` hash and the permission-filtered navigation tree; the shell renders from it,
   unknown ids are dropped and logged, and the guard now asks the server (ADR-0008).
4. ~~**Tenant resolution from the session.**~~ ✅ Done. `SessionTenantFilter` binds `TenantContext`
   per request and unbinds it in a `finally`; a rejected tenant name no longer leaks its pooled
   connection (ADR-0011).
5. ~~**Audit log (FR-008).**~~ ✅ Done, end to end. One `audit_event` table per school, field names
   and never values, data changes in the caller's transaction and security events in their own
   (ADR-0018), and the screen at `/audit`. **The screen landed with zero backend changes**, which
   was the real test of ADR-0008: the backend had been emitting the `audit` id since the previous
   PR and the frontend was dropping it with a log line until a route resolved it.
6. ~~**School profile.**~~ ✅ Done. `GET`/`PUT /api/school/profile` and `/settings/school-profile`,
   with the registry row written back on save so the register cannot disagree with the school.
7. **Shared components** as the admin screens need them: dialog and toast remain (ADR-0009).
   Designed already — see [`docs/artifacts`](artifacts/README.md).
8. ~~**Academic sessions and classes.**~~ ✅ Done. Sessions with exactly one current year enforced by
   a partial unique index, and the class ladder with its sections — structural rather than
   per-session (ADR-0019). Reordering is one endpoint taking the whole ladder, because two separate
   updates cannot swap two positions past a unique constraint.
9. ~~**Students and guardians.**~~ ✅ Done. One name field rather than three, guardians shared
   between siblings, and enrolment carrying the session. Restricted category fields are deliberately
   absent — see the blocker below.
10. **Encryption at rest** — **decided, not yet built**
    ([ADR-0022](architecture/adr/0022-encryption-at-rest.md)). Both open questions are settled: the
    key is a 256-bit `CHALKBASE_ENCRYPTION_KEY` from the environment with a key id prefixed to every
    ciphertext so rotation is possible, and storage is marked with `@Encrypted` on the entity while
    `@Classification` stays on the DTO, bound by a build-failing test rather than by repeating the
    tier in two places that can disagree. **This is the next slice**, and it unblocks the Restricted
    student fields and therefore UDISE+.
11. ~~**Student import.**~~ ✅ Done. Validate as its own call, all-or-nothing on commit, every
    problem listed at once. **Export is deliberately not built**: ADR-0014 requires exports masked by
    classification with the unmasked one audited, and neither the masking nor the permission that
    lifts it exists — an export ignoring that would be the largest unaudited disclosure surface in
    the product.
12. **Subjects**, then **guardian import, documents and dashboards** to close Phase 1.
13. **ADR-0008's staleness rule**, promoted out of the debt list: the audit screen is the exact case
    it describes. A 403 should make the client refetch `/api/me` and re-render navigation before
    showing the error, so a permission revoked mid-session stops leaving a menu entry that lies. The
    interceptor does this for 401 only.

Also queued, not blocking:

- ~~`PageResponse<T>`~~ ✅ Built with `GET /api/audit`, the first list endpoint: offset pagination
  inside the ADR-0007 envelope, page size capped at 100, and an unknown `?sort=` property answered
  with a 400 rather than a 500.
- Deploy to Coolify on the Hostinger Mumbai box once authentication exists (ADR-0015).
- ~~`@Classification` annotation and the build-failing test for unclassified DTO fields~~ ✅ Built
  (PR #22), on every record under a `*/api/` package. The remaining hole is the accessor case, which
  is listed under Blocking below rather than here.
- Synthetic school seeder for the `dev` profile: ~600 students, 14 classes, one term of attendance,
  fixed seed. Needed to make list screens and performance real.

## Blocking the first real school

- **A Confidential value can still reach a log through an accessor.** `@Classification` and the
  redacting `toString` stop `log.info("saving {}", dto)`; nothing stops
  `log.info("saving {}", dto.fullName())`. The cheap next step is a static rule flagging a
  `CONFIDENTIAL` accessor inside a logger argument — worth more than export masking, and worth doing
  before the codebase has many more call sites.
- **Encryption at rest does not exist, and the student record now needs it.** The decisions are
  taken ([ADR-0022](architecture/adr/0022-encryption-at-rest.md)); the code is not written. Caste and community,
  religion, disability/CWSN, EWS/BPL/RTE category, guardian income, APAAR and Aadhaar are Restricted
  under [ADR-0014](architecture/adr/0014-data-classification.md): encrypted at rest, masked in the
  UI, every read audited. None of that machinery is built, so
  [ADR-0020](architecture/adr/0020-student-and-guardian-model.md) leaves those columns out entirely
  rather than store a child's caste in plaintext. **UDISE+ returns need them**, so this is on the
  critical path to onboarding a real school, not a later nicety.
- **Audit retention is unset.** ADR-0014 requires a period per category; the table grows unbounded
  until a purge exists. The number is a legal question, not an engineering one.

## Waiting on a decision

Phase 0 cleared this table. What is left is externally blocked rather than undecided.

| Question | Why it matters | Urgency |
|---|---|---|
| **TRAI DLT registration** | Weeks of paperwork, and nothing can start it retroactively. Blocks SMS fee reminders, absence alerts and any phone-OTP login. Not blocking v1, since v1 ships email and web push only ([ADR-0013](architecture/adr/0013-external-provider-ports.md)). | **Start now** |
| SMS / WhatsApp provider | Chosen once DLT registration completes — that process shows which providers are painless. | After DLT |
| Payment gateway | Chosen once the pilot school's bank and settlement account are known. Razorpay is the intended first adapter. | Before online fees |
| Production migration off Supabase Seoul | The Hostinger Mumbai box ([ADR-0015](architecture/adr/0015-deployment-baseline.md)) replaces it. Move before there is data worth migrating. | Before first real data |

## Known gaps and debt

Recorded so they are decided rather than discovered.

- `/api/schools/**` is still `permitAll`, because onboarding a campus has no caller to authenticate
  yet. It is CSRF-exempt for exactly as long as that is true — CSRF protects ambient cookie
  authority, and an endpoint that reads no cookie has none. Closes with a platform-operator account.
- Durable cross-module events are not enabled; the Modulith event registry needs its own migration,
  which lands with the first published domain event (ADR-0001).
- The generated OpenAPI client is not wired up; `frontend/src/app/core/api/models.ts` is hand-written
  and mirrors the backend by hand (ADR-0007).
- `contrast-audit.mjs` is run by hand. Make it a CI step once the palette settles.
- **Hibernate was logging the whole failed INSERT, values included, at WARN** — one duplicate
  admission number put a child's name, date of birth and gender in the log, in every environment.
  `org.hibernate.orm.jdbc.error` is now at ERROR, and the unmapped-constraint branch of
  `GlobalExceptionHandler` logs the constraint's *name* instead of the exception, because
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
  database-wide change wanting a measurement behind it.
- **The import reads CSV, not `.xlsx`.** The requirement says "import from Excel"; every Excel can
  *Save As* CSV, and reading `.xlsx` directly needs Apache POI — megabytes of dependency and real CVE
  surface, which AGENTS rule 8 says to ask about. A `.xlsx` upload is detected by its magic bytes and
  refused with instructions rather than a parse error. **Open question for the product owner**: if
  "Save as CSV" is a genuine barrier for school offices, POI is the answer and it is a small change
  behind the same endpoint.
- **Guardians are not imported**, deliberately (ADR-0021 §4): a file of six hundred students each
  naming a father would create six hundred guardian records, including four for one man with four
  children here — the duplicate the manual flow was just fixed to prevent. Doing it properly means
  matching each row against the directory by phone, which is its own slice.
- **The upload limit is coupled across two files.** `spring.servlet.multipart.*` is set below nginx's
  `client_max_body_size` so Spring is always the one refusing, in the ADR-0007 envelope; a request
  refused by nginx returns HTML and may reach the browser without CORS headers, so the client sees a
  network failure rather than "that file is too large". Raise both or neither.
- `MaxUploadSizeExceededException → VAL_003` is untested: `MockMvcRequestBuilders.multipart()` builds
  the request object directly and never runs the multipart resolver, so the limit cannot be exercised
  from MockMvc at all.
- `guardian.phone` is `varchar(20)`. `+91 98765 43210` fits at 16; a longer international number
  with an extension would not.
- Startup migration measured **9.4 s for two schools** against the Seoul database — ~4.7 s each,
  dominated by round trips. Fifty schools would be about four minutes of startup. Mumbai (ADR-0015)
  will cut it sharply; the linear shape does not change, so the ADR-0011 expiry stands.
- Startup migration is deliberate and has a recorded expiry — move it to a deploy step once startup
  passes ~1 minute, a second replica appears, or tenant count passes ~50 (ADR-0011).
- The tenant is a **campus**, not a group: a multi-campus trust gets one schema per campus, with the
  group as a row in `public` (ADR-0011). Group-wide reporting is therefore a fan-out, and primary
  keys must be globally unique — UUIDv7, already the convention. Not exercised by the MVP, which is
  scoped to a single-campus school.

- **The navigation contract still has no test across the two sides.** The backend now declares
  `settings`, `settings.access`, `settings.profile`, `schools` and `audit`; the frontend registry
  maps all but `settings.access` and `audit`, which stay dropped-and-logged because neither has a
  screen. That is ADR-0008's designed behaviour, not a defect — but the guard against a genuine typo
  is a CI check comparing the backend's ids to the frontend's registry, which needs both artefacts
  and so belongs in neither agent's half. Both sides carry a matching `TODO(contract)`.
- **ADR-0008's staleness rule is not implemented.** A `403` should make the client refetch
  `/api/me` before showing the error. `permissionsVersion` is stored and ready; the work is doing it
  without a refetch loop.
- Expired sessions are never purged.
- **A deactivated class keeps its name.** `uq_school_class_name` does not account for `active`, and
  there is no delete (ADR-0019), so a school that retires "Class 5" and later wants it back must
  reactivate that row rather than create a new one. That is the intended behaviour, but it makes
  showing inactive classes findable a correctness concern rather than a nicety — a user who cannot
  see the retired row hits a name clash they cannot explain.
- **`AGENTS.md` claimed two things that were not true** and now does not: indexes are `idx_`, not
  `ix_`, and the `@Classification` annotation ADR-0014 describes does not exist, so nothing fails
  the build for an unclassified field. Both were found by agents reading the file and trying to
  follow it. A rule that lies is worse than no rule; if ADR-0014's enforcement is wanted, it is
  still worth building while the DTO count is small.
- **The audit log has no retention period and no purge job.** ADR-0014 requires every category to
  carry one; seven years is the Indian financial-record convention, but the number is a legal
  question for the board and the DPDP rules rather than an engineering choice. Needed before the
  first school completes a full session, since nothing bounds the table until then.
- Indian states are a hardcoded list in the school-profile form (`TODO(reference-data)`). They are
  Tier-1 master data and belong in `public` behind an endpoint (ADR-0006). The audit screen's action
  filter is the same case: it lists the actions this build ships, so a verb a future module invents
  is filterable by neither name nor dropdown — those rows still list, label legibly and are
  reachable by actor or date. Closing it needs the distinct actions in a school's own log.
- **A school has no timezone**, so the audit screen renders times in the reader's own device zone.
  India is one zone, so this is right for everyone in the country and wrong only for someone reading
  from abroad — the row detail names the zone so they are not misled. A `timezone` on the school
  closes it properly, and is a contract change rather than a screen fix.
- The per-component style budget was raised from 4 kB / 8 kB to **8 kB / 12 kB**. The old warning
  threshold was calibrated when every screen was a simple form; a screen that honours ADR-0010 with
  a table above the wide breakpoint and cards below legitimately costs more, and the audit log is
  the first of those at 6.3 kB. Raised deliberately rather than left as a permanent warning, because
  a build that always warns is a build nobody reads. The shared page scaffolding was extracted to
  `styles/_page.scss` in the same change — three screens had their own copy and the copies had
  already drifted.

## Keeping this honest

- Update this file in the same PR as the change.
- Moving something to Done means it is merged and verified, not written.
- If something here is stale, that is a bug — fix the file, not the memory of it.
