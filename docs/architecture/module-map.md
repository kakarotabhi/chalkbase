# Module map

Which application module owns which data and endpoints. **Update this in the same commit that adds
or changes a module** — agents read it instead of scanning the whole backend.

| Module | Owns | Endpoints | Tenant-scoped | Status |
|---|---|---|---|---|
| `platform` | nothing (shared kernel: tenancy, security, error handling, config) | — | n/a | scaffolded |
| `school` | `school` | `/api/v1/schools` | no — this **is** the tenant | scaffolded |
| `identity` | users, roles, permissions, sessions | `/api/v1/auth`, `/api/v1/users` | yes | planned |
| `admission` | enquiries, applications, admission fees | `/api/v1/admissions` | yes | planned |
| `student` | students, guardians, documents, alumni | `/api/v1/students` | yes | planned |
| `staff` | staff records, qualifications, leave | `/api/v1/staff` | yes | planned |
| `academics` | classes, sections, subjects, timetable, syllabus | `/api/v1/academics` | yes | planned |
| `attendance` | student and staff attendance | `/api/v1/attendance` | yes | planned |
| `exam` | assessments, marks, report cards | `/api/v1/exams` | yes | planned |
| `fee` | fee heads, concessions, invoices, receipts | `/api/v1/fees` | yes | planned |
| `payroll` | salary structures, payslips | `/api/v1/payroll` | yes | planned |
| `transport` | routes, stops, vehicles, drivers | `/api/v1/transport` | yes | planned |
| `hostel` | rooms, allotments, mess | `/api/v1/hostel` | yes | planned |
| `library` | catalogue, issues, returns, fines | `/api/v1/library` | yes | planned |
| `inventory` | assets, stock, labs | `/api/v1/inventory` | yes | planned |
| `communication` | notices, SMS/WhatsApp/email dispatch, templates | `/api/v1/communication` | yes | planned |
| `compliance` | UDISE+ and APAAR exports, board disclosures, audit records | `/api/v1/compliance` | yes | planned |

Modules are added in roadmap order — see
[docs/requirements/06-roadmap-and-mvp.md](../requirements/06-roadmap-and-mvp.md).

## Rules of ownership

- One module owns a table. Other modules read it through that module's `api`, never with a join.
- A table without an owner in this file should not exist.
- Global reference data (boards, states, districts, subject catalogue) lives in `platform` and is
  created in the `public` schema by `db/migration/shared`. Everything else belongs to a school's own
  schema, created by `db/migration/tenant` (ADR-0011).
