# Module map

Which application module owns which data and endpoints. **Update this in the same commit that adds
or changes a module** — agents read it instead of scanning the whole backend.

| Module | Owns | Endpoints | Tenant-scoped | Status |
|---|---|---|---|---|
| `platform` | nothing (shared kernel: tenancy, security, error handling, config) | — | n/a | scaffolded |
| `school` | `public.school`, `public.school_group` (registry); `academic_session` (per tenant) | `/api/schools` | registry is not; `academic_session` is | scaffolded |
| `identity` | `user_account`, `user_identifier`, `user_credential`, `permission`, `role`, `role_permission`, `user_role_grant` (per tenant); `public.spring_session` | `/api/auth/**`, `/api/access/**` | yes | scaffolded |
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
