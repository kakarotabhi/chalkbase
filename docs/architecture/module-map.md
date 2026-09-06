# Module map

Which application module owns which data and endpoints. **Update this in the same commit that adds
or changes a module** — agents read it instead of scanning the whole backend.

| Module | Owns | Endpoints | Tenant-scoped | Status |
|---|---|---|---|---|
| `platform` | shared kernel: tenancy, security, error handling, navigation, paging, config. Owns `audit_event` (per tenant) — the audit log records every module, so putting it in one of them would make the rest depend on that one to be audited. | `/api/audit` | `audit_event` is | audit log built |
| `school` | `public.school`, `public.school_group` (registry); `school_profile`, `academic_session` (per tenant) | `/api/schools`, `/api/school/profile` | registry is not; the rest are | profile built |
| `identity` | `user_account`, `user_identifier`, `user_credential`, `permission`, `role`, `role_permission`, `user_role_grant` (per tenant); `public.spring_session` | `/api/auth/**`, `/api/access/**`, `/api/me` | yes | built |
| `admission` | enquiries, applications, admission fees | `/api/admissions` | yes | planned |
| `student` | students, guardians, documents, alumni | `/api/students` | yes | planned |
| `staff` | staff records, qualifications, leave | `/api/staff` | yes | planned |
| `academics` | classes, sections, subjects, timetable, syllabus | `/api/academics` | yes | planned |
| `attendance` | student and staff attendance | `/api/attendance` | yes | planned |
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
- Global reference data (boards, states, districts, subject catalogue) lives in `platform` and is
  created in the `public` schema by `db/migration/shared`. Everything else belongs to a school's own
  schema, created by `db/migration/tenant` (ADR-0011).
- **Registry versus profile.** `public.school` is identity and routing — code, name and schema name,
  read before any tenant is bound. A school's editable detail lives in `school_profile`, inside its
  own schema. The registry's copy of name, board, city and state is written back on every profile
  save, because a school register that disagrees with the school is worse than a duplicated column.

## Reaching across a module boundary

Four SPIs exist so a module can contribute to something the platform owns without either side
importing the other. Each is a `@Bean` inside the module, collected by the platform at startup:

| SPI | Contributes | Registered by |
|---|---|---|
| `PermissionProvider` | what this module lets someone do | e.g. `SchoolPermissions` |
| `NavigationProvider` | where this module's screens sit in the menu | e.g. `SchoolNavigation` |
| `ConstraintMappingProvider` | how this module's database constraints read to a user | e.g. `SchoolConstraintMappings` |
| `AuditActorResolver` | who is acting, for the audit log's actor snapshot | `IdentityAuditActorResolver` |

Navigation adds one rule worth knowing: a module contributes a screen to **another** module's
section by declaring it at the top level under its dotted id — `school` declares `settings.profile`
and the catalogue places it beneath the `settings` container that `identity` owns. Without that,
`IdentityNavigation` would be the one file every module edits to add a menu entry.
