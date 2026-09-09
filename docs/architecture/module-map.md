# Module map

Which application module owns which data and endpoints. **Update this in the same commit that adds
or changes a module** — agents read it instead of scanning the whole backend.

| Module | Owns | Endpoints | Tenant-scoped | Status |
|---|---|---|---|---|
| `platform` | shared kernel: tenancy, security, error handling, navigation, paging, config, the `StorageService` storage port (ADR-0025), classification-driven CSV export (`platform.export`, ADR-0027). Owns `audit_event` (per tenant) — the audit log records every module, so putting it in one of them would make the rest depend on that one to be audited. Also owns `public.state` (ADR-0029) — global reference data, seeded once from `IndianStates.java`, not per tenant. | `/api/audit`, `/api/dashboard`, `/api/reference/states` | `audit_event` is; `state` (in `public`) is not | built, with its screen |
| `school` | `public.school`, `public.school_group` (registry); `school_profile` (per tenant) | `/api/schools`, `/api/schools/bootstrap`, `/api/schools/boards`, `/api/school/profile` | registry is not; the profile is | built |
| `identity` | `user_account`, `user_identifier`, `user_credential`, `permission`, `role`, `role_permission`, `user_role_grant` (per tenant); `public.spring_session` | `/api/auth/**`, `/api/access/**`, `/api/me` | yes | built |
| `admission` | `enquiry`, `enquiry_follow_up` (per tenant) — enquiry management only; applications and admission fees still planned | `/api/admissions` | yes | enquiry capture, status, counsellor assignment, follow-up history and the due-date follow-up queue built (Phase 2, FR-016/017); the online admission form, the application workflow and student conversion are separate, later lanes |
| `student` | `student`, `guardian`, `student_guardian`, `student_enrolment`, `student_contact`, `student_transfer`, `student_medical`, `student_compliance` (per tenant); alumni still planned | `/api/students/**`, `/api/guardians/**` | yes | students, guardians, enrolment, CSV import, the contact/previous-school/medical/compliance sections, and masked/unmasked CSV export ([ADR-0027](adr/0027-export-masking.md)) built |
| `document` | `document` (per tenant) — a student's certificates, photo, signature and other documents; bytes held behind `platform`'s `StorageService`, not in this table (ADR-0025) | `/api/documents/**` | yes | port, module and student attachment built; renewal reminders and Restricted-category document types deliberately not built |
| `staff` | staff records, qualifications, leave | `/api/staff` | yes | planned |
| `academics` | `academic_session`, `school_class`, `section`, `subject` (per tenant); timetable and syllabus still planned | `/api/academics/**` | yes | sessions, classes and subjects built |
| `attendance` | `attendance_mark`, `attendance_correction_request` (per tenant) — student attendance, daily and period-wise in one table from day one; staff attendance still needs a `staff` module | `/api/attendance` | yes | daily grain built: mark, view, lock, correction request and approval (ADR-0030); period-wise has its table shape and no write path |
| `exam` | assessments, marks, report cards | `/api/exams` | yes | planned |
| `fee` | fee heads, concessions, invoices, receipts | `/api/fees` | yes | planned |
| `payroll` | salary structures, payslips | `/api/payroll` | yes | planned |
| `transport` | routes, stops, vehicles, drivers | `/api/transport` | yes | planned |
| `hostel` | rooms, allotments, mess | `/api/hostel` | yes | planned |
| `library` | catalogue, issues, returns, fines | `/api/library` | yes | planned |
| `inventory` | assets, stock, labs | `/api/inventory` | yes | planned |
| `communication` | notices, SMS/WhatsApp/email dispatch, templates | `/api/communication` | yes | planned |
| `compliance` | UDISE+ and APAAR exports, board disclosures, audit records | `/api/compliance` | yes | planned |

Modules are added in roadmap order — see
[docs/requirements/06-roadmap-and-mvp.md](../requirements/06-roadmap-and-mvp.md).

## Rules of ownership

- One module owns a table. Other modules read it through that module's `api`, never with a join.
- A table without an owner in this file should not exist.
- Global reference data lives in `platform` and is created in the `public` schema by
  `db/migration/shared` — landed for states (`public.state`, ADR-0029). **Boards are the deliberate
  exception**: `Board` stays a `school.domain` enum rather than a table, for the reasons ADR-0029
  gives (three request records and two response records already type it, and a table would need a
  migration and a backfill to remove a list that changes roughly once a decade). Districts and the
  subject catalogue are not built yet. Everything else belongs to a school's own schema, created by
  `db/migration/tenant` (ADR-0011).
- **Registry versus profile.** `public.school` is identity and routing — code, name and schema name,
  read before any tenant is bound. A school's editable detail lives in `school_profile`, inside its
  own schema. The registry's copy of name, board, city, state and time zone is written back on every
  profile save, because a school register that disagrees with the school is worse than a duplicated
  column — the time zone rides further still, onto the session as `SchoolSummary`, so `/api/me`
  (ADR-0008) never binds a tenant just to render a time ([ADR-0032](adr/0032-school-timezone.md)).
- **`academic_session` belongs to `academics`, not to `school`.** It moved there with the classes
  work: it is the time axis the academic model hangs off, and leaving it beside the school registry
  would make every academics query reach across a boundary for it. The table did not change — only
  the Java package.
- **Classes and sections are structural, not per session** ([ADR-0019](adr/0019-classes-and-sections.md)).
  The session appears on what references them — enrolment first, and later the class-teacher
  assignment, which genuinely changes every year.
- **Subjects are a flat catalogue, not a ladder.** `subject` carries no `sequence` — a subject has
  no natural order the way a class does — and no relation to `school_class` or `section`: which
  classes teach which subjects is a subject allocation for the timetable and marks modules to
  decide, not this one. It is deactivated rather than deleted for the same reason ADR-0019 gives
  classes and sections that treatment.

## Reaching across a module boundary

Four SPIs exist so a module can contribute to something the platform owns without either side
importing the other. Each is a `@Bean` inside the module, collected by the platform at startup:

| SPI | Contributes | Registered by |
|---|---|---|
| `PermissionProvider` | what this module lets someone do | e.g. `SchoolPermissions` |
| `NavigationProvider` | where this module's screens sit in the menu | e.g. `SchoolNavigation` |
| `ConstraintMappingProvider` | how this module's database constraints read to a user | e.g. `SchoolConstraintMappings` |
| `AuditActorResolver` | who is acting, for the audit log's actor snapshot | `IdentityAuditActorResolver` |
| `CurrentUserResolver` | who is acting, as a plain `UUID` a module may store on a row it owns | `IdentityCurrentUserResolver` |
| `AcademicsDashboardContributor` | the current-session tile on `/api/dashboard` | `AcademicsDashboardTileService` |
| `StudentDashboardContributor` | the enrolment-by-class and linkage-gap tiles on `/api/dashboard` | `StudentDashboardTileService` |

The first dependency between two **feature** modules is `student` → `academics`, and it goes through
a named interface rather than a package import: `academics.api.AcademicsLookup` answers "which
session is current", "what class is this section in". It is read-only by design — a module that
needs the academic structure *changed* asks a person, not another module. `student_enrolment` holds
`academic_session_id` and `section_id` as plain UUIDs rather than JPA associations, so the foreign
keys live in the database and the Java coupling does not exist at all.

`document` reaches `student` the same way, but with no named interface at all: `document.student_id`
is a plain `uuid` with a database foreign key (`fk_document_student`), and this module never imports
`student` in either direction. There is nothing for a named interface to do when the only thing one
module needs from another is "this id must belong to a row over there", which the database already
enforces on every write. `document` also uses `platform.storage.StorageService` (ADR-0025), which is
not one of the four SPIs above: it is `platform`-owned infrastructure a module calls directly, the
same way every module already calls `platform.audit.AuditService`.
`student` also exposes its own named interface now, `student.api.StudentLookup`, mirroring
`AcademicsLookup`: read-only counts (active enrolments, by section, and the two linkage gaps),
never a `Student` or `Guardian` row. It exists for the dashboard — the first caller outside
`student` to need anything from it in bulk — and answers a number, never a name or a phone number,
so a tile cannot become a second, unaudited way to read data `student:student:read` and
`student:guardian:read` already guard.

The dashboard (`platform.dashboard`) is the first place the shared kernel needs data *from* a
feature module, which is backwards from every dependency above: `platform` must not import a
feature module (the same reason `AuditActorResolver` exists rather than the audit log reaching into
`identity`'s principal type). `AcademicsDashboardContributor` and `StudentDashboardContributor`
above are what keep that true — each module builds its own tile record from its own repositories
(or, for `student`, from `StudentLookup` and `AcademicsLookup`, exactly as any other caller would),
decides for itself whether the caller's permissions allow it, and hands the finished tile to
`platform.dashboard.DashboardService`, which never imports `academics.api` or `student.api`.

Navigation adds one rule worth knowing: a module contributes a screen to **another** module's
section by declaring it at the top level under its dotted id — `school` declares `settings.profile`
and the catalogue places it beneath the `settings` container that `identity` owns. Without that,
`IdentityNavigation` would be the one file every module edits to add a menu entry.

`attendance` reaches both existing feature interfaces at once — the first module to need to.
`academics.api.AcademicsLookup` resolves the section being marked and the school's current
session; `student.api.StudentLookup` gained two methods for it, `rosterOfSection` (a section's
live roster, in class-register order — names and admission numbers, the same tier as everything
else that interface returns) and `namesOf` (a student's name from a bare id, for a correction
queue that already holds one and has no roster to resolve it against). Neither hands back a
`Student` row or anything `student:student:read` does not already guard.

`attendance` also introduces a fifth SPI, `platform.security.CurrentUserResolver`, mirroring
`AuditActorResolver` exactly. The audit log's resolver answers "who, for the log" — a name and a
role snapshot, enough to describe an event and never enough to be a foreign key.
`attendance_mark.marked_by` and `attendance_correction_request.requested_by`/`decided_by` are
domain data, not audit rows, and ADR-0018 §3 already rules out storing a value in the audit log —
so "who did this" has to live on the record itself, and `platform.security.CurrentUser` is what
gets a module a plain `UUID` for that without importing `identity`, the same way
`user_account.password_reset_by` already does one module over.

`admission` reaches `academics.api.AcademicsLookup` the same read-only way `attendance` does, to
resolve the class an enquiry names, and adds a new interface of the same shape on the other side:
`identity.api.IdentityLookup`, the first time a feature module has needed to point at a **staff**
account rather than a student or an academic structure — an enquiry names an assigned counsellor,
and this is how `admission` answers "who is that" and "can this account still be assigned
something" without importing `identity` or joining `user_account`. It answers no more than
`UserSummary` already discloses at `GET /api/access/users`, so it carries no permission of its
own — the caller's own module permission gates it, same as every other lookup interface in this
table.
