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
| Architecture decisions (ADR-0001…0018) | ✅ Done |
| **Phase 0 discovery — all 13 deliverables** | ✅ Done |
| Identity: login, sessions, forced password change | ✅ Done |
| Permissions, roles, scoped grants | ✅ Done |
| Server-driven navigation (`GET /api/me`) | ✅ Done |
| Schema-per-tenant: registry, migration orchestrator | ✅ Done |
| Audit log (FR-008) — table, service, `GET /api/audit` | ✅ Backend; no screen yet |
| School profile — `GET`/`PUT /api/school/profile` and its screen | ✅ Done |
| Shared UI components | ✅ Button, field, inputs, checkbox, select, bottom sheet |
| Academic session and classes | ⬜ Not started |
| Students and guardians | ⬜ Not started |
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
5. ~~**Audit log (FR-008).**~~ ✅ Done, backend only. One `audit_event` table per school, field
   names and never values, data changes in the caller's transaction and security events in their
   own (ADR-0018). **Its screen is the next slice** — the backend emits the `audit` menu id today
   and the frontend drops it, which is ADR-0008's designed behaviour and costs a log line.
6. ~~**School profile.**~~ ✅ Done. `GET`/`PUT /api/school/profile` and `/settings/school-profile`,
   with the registry row written back on save so the register cannot disagree with the school.
7. **Shared components** as the admin screens need them: dialog and toast remain (ADR-0009).
   Designed already — see [`docs/artifacts`](artifacts/README.md).
8. **Academic session and classes** — the next real admin screens.

Also queued, not blocking:

- ~~`PageResponse<T>`~~ ✅ Built with `GET /api/audit`, the first list endpoint: offset pagination
  inside the ADR-0007 envelope, page size capped at 100, and an unknown `?sort=` property answered
  with a 400 rather than a 500.
- Deploy to Coolify on the Hostinger Mumbai box once authentication exists (ADR-0015).
- `@Classification` annotation and the build-failing test for unclassified DTO fields (ADR-0014) —
  cheapest to add before there are many DTOs, not after.
- Synthetic school seeder for the `dev` profile: ~600 students, 14 classes, one term of attendance,
  fixed seed. Needed to make list screens and performance real.

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
- **The audit log has no retention period and no purge job.** ADR-0014 requires every category to
  carry one; seven years is the Indian financial-record convention, but the number is a legal
  question for the board and the DPDP rules rather than an engineering choice. Needed before the
  first school completes a full session, since nothing bounds the table until then.
- Indian states are a hardcoded list in the school-profile form (`TODO(reference-data)`). They are
  Tier-1 master data and belong in `public` behind an endpoint (ADR-0006).

## Keeping this honest

- Update this file in the same PR as the change.
- Moving something to Done means it is merged and verified, not written.
- If something here is stale, that is a bug — fix the file, not the memory of it.
