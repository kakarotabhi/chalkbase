# Where the project is

Living status. **Updated in the same pull request as the work it describes** — a status file that is
updated "later" is worse than none, because people trust it.

Last updated: 2026-09-05 · Roadmap phase: **1** — Phase 0 is complete
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
| Architecture decisions (ADR-0001…0015) | ✅ Done |
| **Phase 0 discovery — all 13 deliverables** | ✅ Done |
| Identity: login, sessions, forced password change | ✅ Done |
| Permissions, roles, scoped grants | ✅ Done |
| Schema-per-tenant: registry, migration orchestrator | ✅ Done |
| Shared UI components | ⬜ Not started |
| School setup, academic session, classes | ⬜ Not started |
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

## Next, in order

Each line says what it unblocks, because the order is not arbitrary.

1. ~~**Identity — schema and login.**~~ ✅ Done. `user_account`, `user_identifier`, `user_credential`
   per tenant; Spring Session in `public`; login, logout and forced password change; the session
   filter binding `TenantContext`; `permitAll` off except onboarding (ADR-0017).
2. ~~**Permissions, roles, grants.**~~ ✅ Done. Permission registry per module seeded per tenant,
   twelve role templates copied on onboarding, scoped grants with validity windows, effective
   permissions resolved once at login, `@PreAuthorize` enforcement, and the test that fails the
   build when a controller method carries no authorization annotation (ADR-0005).
3. **`GET /api/me`** — user, school, permissions, navigation, settings (ADR-0008).
   *Unblocks:* the real menu, and the "More" bottom sheet on phones.
4. **Tenant resolution from the session.** The plumbing is built; what is missing is the filter that
   binds `TenantContext` per request. Until identity lands there is no authenticated school to bind,
   so every request runs against `public` — correct for the registry, and nothing else is exposed
   yet.
5. **Shared components** as the login and admin screens need them: button, form field, text input,
   select, dialog, toast (ADR-0009). Designed already — see
   [`docs/artifacts`](artifacts/README.md).
6. **School setup and academic session** — the first real admin screens, and Phase 1 proper.

Also queued, not blocking:

- `PageResponse<T>` — **settled by Phase 0**: offset pagination, `?page&size&sort`, inside the
  ADR-0007 envelope. Build it with the first list endpoint.
- Deploy to Coolify on the Hostinger Mumbai box once authentication exists (ADR-0015).
- Audit log (FR-008), which is a Phase 1 exit criterion.
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

- `SecurityConfig` permits every request, with CSRF disabled. **Do not expose a deployment
  publicly.** Closes with item 1 above.
- Durable cross-module events are not enabled; the Modulith event registry needs its own migration,
  which lands with the first published domain event (ADR-0001).
- The generated OpenAPI client is not wired up; `frontend/src/app/core/api/models.ts` is hand-written
  and mirrors the backend by hand (ADR-0007).
- `contrast-audit.mjs` is run by hand. Make it a CI step once the palette settles.
- No audit logging yet, though FR-008 marks it P0.
- Startup migration measured **9.4 s for two schools** against the Seoul database — ~4.7 s each,
  dominated by round trips. Fifty schools would be about four minutes of startup. Mumbai (ADR-0015)
  will cut it sharply; the linear shape does not change, so the ADR-0011 expiry stands.
- Startup migration is deliberate and has a recorded expiry — move it to a deploy step once startup
  passes ~1 minute, a second replica appears, or tenant count passes ~50 (ADR-0011).
- The tenant is a **campus**, not a group: a multi-campus trust gets one schema per campus, with the
  group as a row in `public` (ADR-0011). Group-wide reporting is therefore a fan-out, and primary
  keys must be globally unique — UUIDv7, already the convention. Not exercised by the MVP, which is
  scoped to a single-campus school.

## Keeping this honest

- Update this file in the same PR as the change.
- Moving something to Done means it is merged and verified, not written.
- If something here is stale, that is a bug — fix the file, not the memory of it.
