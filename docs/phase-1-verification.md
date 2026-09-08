# Phase 1 verification against the deployed environment

Run on 2026-09-08 against `https://chalkbase-web.onrender.com` and `https://chalkbase-api.onrender.com`,
school `DEMO-001`, immediately after the Phase 1 polish merged. Four agents exercised the API with
`curl` in parallel, one per area; the screens were driven separately in a real browser.

**Why this file exists.** Phase 1 was declared complete on the strength of CI and merged pull
requests. This is the first time the whole of it was exercised as a user, and it found three defects
and one product gap that CI could not have caught — every one of them a case where the code was
right and something *around* it was wrong: a seed that cannot reach the database it was written for,
a screen whose prose contradicts its own behaviour, an audit row that records less than it should,
and a role nobody can delete.

## Result

**59 checks across five areas. 55 passed.** Everything that failed is listed below with its cause.
Nothing failed in a way that discloses data: the two properties this product is most careful about —
Restricted values never leaving the server unmasked, and the audit log never recording a value — both
held under direct attack.

## What passed

### Identity, access and sessions

| Check | Evidence |
|---|---|
| Login, four users | `principal` 21 permissions, `classteacher` 9, `auditor` 3, `newteacher` 6 with `mustChangePassword` |
| Forced password change is airtight | `GET /api/students` as `newteacher` → `403 AUTH_008`; `/api/auth/password` and `/logout` reachable |
| Navigation matches permissions | `auditor` is offered `dashboard`, `settings.users`, `audit` — and **not** `settings.access` |
| Account lifecycle | create → deactivate → reactivate → unlock → reset (a **different** temporary password each time) |
| `AUTH_010` last-access-manager guard | deactivating the only holder of `identity:role:manage` → `409`, account untouched |
| `AUTH_011` privilege-escalation guard | granting a permission the actor lacks → `403`, role re-read and **unchanged** — no partial write |
| Role create, edit, grant, revoke, holders | all correct |
| ADR-0023 session re-validation | a deactivated account's **very next** request → `401 AUTH_002` |
| ADR-0031 template reconciliation | 21 permissions, including every one the ADR named as missing |

### Academics, school profile, reference data

| Check | Evidence |
|---|---|
| Sessions, classes, sections, subjects | create, edit, retire, reinstate all correct |
| `GET /api/schools/boards` | **200 from an ordinary session with no setup key** — the prod 404 fix holds live |
| `GET /api/reference/states` | 200, full list (ADR-0029) |
| Timezone validation | `Asia/Kalkota` → `400 VAL_001`, not persisted; default `Asia/Kolkata` |
| Duplicate subject name / code | `ACAD_008` / `ACAD_009`, readable sentences |
| Incomplete reorder | `ACAD_007` with `details.missing`, rejected **before any write** |
| Pagination | page 0 and page 1 genuinely differ, ADR-0007 envelope |
| School profile write-back | `PUT` persisted and restored |

### Students, guardians, import, export

| Check | Evidence |
|---|---|
| Student CRUD and enrolment | correct, including `currentEnrolment` |
| Guardian link, detach, expansion | `linkedStudentCount` 1 → 0, expands to real students |
| Phone digit search | `+919876500011` found by `98765 00011`, `+91 98765 00011` and `9876500011` |
| **No Restricted plaintext on an ordinary GET** | raw response bytes grepped for every marker — **none present** |
| Reveal endpoints gated | principal sees values; `auditor` → `403 PERM_001` |
| APAAR consent | saving an id without consent → `422 STU_019` |
| Import: validate-first, all-or-nothing | 3 errors reported at once; **nothing written**, including the one valid row |
| Import: guardian dedup by phone | two siblings, differently formatted numbers → **1 guardian, 2 links** |
| `.xlsx` refused by magic bytes | `400 STU_016` with Save-As-CSV instructions, not a parse error |
| Masked export omits Restricted columns | header carries no caste/religion/APAAR/medical columns at all |
| Unmasked export refused | `403` for the principal — no shipped role holds `student:student:export_unmasked` |
| `DATA_EXPORTED` audit | names the 27 columns written, `recordCount`, no values |

### Documents, dashboard, attendance, audit

| Check | Evidence |
|---|---|
| Document round trip | upload 201 → download 200 → **SHA-256 identical** → delete 204 → list empty |
| No signed or direct URL, ever | byte stream from the API host; `DocumentSummary` carries no storage key |
| Magic bytes beat filename **and** declared type | text named `.png` declared `image/png` → `DOC_001`; PNG named `.txt` declared `text/plain` → accepted as `image/png` |
| Oversized upload | 9 MB against an 8 MB limit → `413 VAL_003` in the envelope, not a network error |
| Dashboard gates tiles both ways | principal sees students, not audit; auditor sees audit, not students |
| Attendance, six Phase 0 statuses | accepted end to end; edits preserve the same `markId` |
| Future date refused | `400 VAL_001` |
| Correction not needed yet | `ATT_004` when the mark is still editable |
| **Audit records names, never values** | dozens of events, every module: `ATTENDANCE_MARKS_RECORDED` → `["remarks","status"]`, never `"EXCUSED_LEAVE"` |
| A refusal is itself audited | principal's 403 wrote `PERMISSION_DENIED` on `GET /api/audit` carrying **the same `traceId` as the response** |
| Retention is configuration | `chalkbase.audit.retention.years: 7`, batch 500, cron 02:30 — not a literal |

### Screens (browser, 1280px)

Login, dashboard, students list (search, status filter, 22-section class filter, paging, export/import
actions), student record (all eight sections), attendance marking (section picker, today's date,
"mark all present", six statuses, notes, save disabled until dirty), roles and access (12 roles,
grant/revoke), subjects, school profile, user accounts. **Zero console errors** on every screen except
the deliberate 401 before sign-in and 403 on the audit log.

The audit log's no-permission state is worth quoting, because it is the standard the rest should meet:
*"You do not have permission to view the audit log. Ask your principal to add 'View the audit log' to
your role. Nothing has been recorded against you for opening this page."*

## What failed

### 1. The ~600-student seed cannot reach any database that already has the demo school

The list reads **60**, and the dashboard independently agrees. `DemoSchoolSeeder` is
`@Profile("local")` — correct, and it must stay that way — and it short-circuits on `alreadySeeded()`
keyed on the school code. The shared dev database already has that school, so a developer running
locally gets the short-circuit too.

Everything the seed existed to expose is therefore still untested: list paging at scale, and the
guardian phone search that `docs/status.md` records as a deliberate unindexed sequential scan. The
seed was going to be the evidence for whether `pg_trgm` is a "when" or a "now". **Being fixed.**

### 2. The audit screen contradicted itself about time zones

`audit-log.ts` rendered times in the school's zone (ADR-0032); `audit-log.html` still told the reader
"Times are shown in this device's local time". The prose is the part a user believes. **Fixed** — the
lede now names the school's zone, and the component exposes it so the zone is on screen rather than
implied.

### 3. `RESTRICTED_DATA_REVEALED` recorded no field names

Reveal events carried `changedFields: []`. `StudentRecordService` used the `recordSecurityEvent`
overload without a field list, while `StudentExportService` used the one with it — which is why an
export names its 27 columns and a reveal named nothing. No value ever leaked, so this is an audit
*completeness* gap rather than a disclosure. But ADR-0014 makes "every read audited" one of the three
things Restricted means, and a row that cannot say *what* was revealed cannot answer "who has seen
this child's caste category". **Being fixed.**

### 4. A role can be created and edited but never removed

`AccessController` exposes `POST /roles`, `PUT /roles/{id}/permissions` and `GET /roles/{id}/holders`,
and `RoleManagementService` has no delete or archive method. A school that creates a role by mistake
carries it forever.

This is a **Phase 1 scope gap** in "roles and permissions" rather than a defect in what was built,
and it is not yet fixed — closing it needs a decision about what happens to a role that is still
granted to somebody, which is the same shape of question `AccessGuardrails` already answers for the
last access manager.

## A consequence nobody had hit: verification is not reversible

ADR-0019 and ADR-0020 remove `DELETE` from academics and from students deliberately — a class,
section, subject, session, student or guardian is retired, never deleted. That is right for a school.
Its consequence is that **testing against a shared environment leaves permanent residue**: the demo
school now carries a `VERIFY-2099-01` session, several retired `VERIFY-` classes, sections and
subjects, two withdrawn `VERIFY-` students, two unlinked `VERIFY-` guardians, a `VERIFY-TestRole` and
three attendance marks — none of which can be removed through the product.

Not a defect, and not an argument for adding deletes. But the demo school accumulates junk from every
verification pass, and that wants a decision before the next one — most likely a disposable school
per run, created through `POST /api/schools/bootstrap` (ADR-0024), which is exactly what that
endpoint makes possible.
