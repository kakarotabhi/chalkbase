# Where the project is

Living status. **Updated in the same pull request as the work it describes** — a status file that is
updated "later" is worse than none, because people trust it.

Last updated: 2026-09-05 · Roadmap phase: **0 → 1** ([Phase definitions](requirements/06-roadmap-and-mvp.md))

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
| Architecture decisions (ADR-0001…0010) | ✅ Done |
| **Identity: login, users, sessions** | ⬜ Not started — **the next build** |
| Permissions, roles, scoped grants | ⬜ Not started |
| Row-level security policies | ⬜ Not started — needs identity |
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

## Next, in order

Each line says what it unblocks, because the order is not arbitrary.

1. **Identity — schema and login.** `user`, `user_identifier`, `user_credential`, session storage in
   PostgreSQL. Username/password only (ADR-0003).
   *Unblocks:* everything below, plus turning off `permitAll` in `SecurityConfig`.
2. **Permissions, roles, grants.** Permission registry per module, role templates, scoped grants
   (ADR-0005).
   *Unblocks:* `@PreAuthorize` on every endpoint, and the navigation payload.
3. **`GET /api/v1/me`** — user, school, permissions, navigation, settings (ADR-0008).
   *Unblocks:* the real menu, and the "More" bottom sheet on phones.
4. **Row-level security policies** on tenant-scoped tables (ADR-0002), plus `TenantContext` fed from
   the session instead of the development-only resolver.
5. **Shared components** as the login and admin screens need them: button, form field, text input,
   select, dialog, toast (ADR-0009). Designed already — see
   [`docs/artifacts`](artifacts/README.md).
6. **School setup and academic session** — the first real admin screens, and Phase 1 proper.

Also queued, not blocking:

- `PageResponse<T>` — settle before the first list endpoint that needs paging (noted in ADR-0007).
- Deploy to Coolify once authentication exists.
- Audit log (FR-008), which is a Phase 1 exit criterion.

## Waiting on a decision

These are not blocked on engineering.

| Question | Why it matters | Urgency |
|---|---|---|
| **TRAI DLT registration** | Weeks of paperwork. Needed for fee reminders and any OTP. | **Start now** regardless of when OTP ships |
| Production database region | Supabase project is in `ap-northeast-2` (Seoul), ~150-200 ms from India. Moving means a new project. | Before there is data worth migrating |
| First board and state | Shapes compliance, report cards, fee structures | Before Phase 2 |
| SMS / WhatsApp / payment / email providers | Integration work and cost | Before Phase 2 |
| Document storage: VPS volume, S3-compatible, or managed | Affects student documents and certificates | Before Phase 1 finishes |
| Parent access: PWA only, or native apps too | Changes the frontend plan | Before Phase 2 |

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

## Keeping this honest

- Update this file in the same PR as the change.
- Moving something to Done means it is merged and verified, not written.
- If something here is stale, that is a bug — fix the file, not the memory of it.
