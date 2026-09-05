# Chalkbase — School Management System Requirements

Version: 0.1  
Date: 2026-08-29  
Target market: Indian K-12 schools, including pre-primary, primary, middle, secondary, and senior secondary schools.  
Preferred stack: Spring Boot, Angular, PostgreSQL.  
Deployment target: self-hosted VPS using Coolify.

## Purpose

This document defines the requirements for building a full school management system from scratch for Indian schools. It covers school administration, admissions, academics, fees, attendance, communication, staff, payroll, transport, hostel, library, inventory, safety, compliance reporting, parent/student/staff portals, integrations, and deployment requirements.

The system should be usable by a single school first, but designed so that it can later support multi-branch school groups or a multi-school SaaS model without a rewrite.

## Document Map

- [Product Scope](01-product-scope.md)
- [Functional Requirements](02-functional-requirements.md)
- [India Compliance Requirements](03-india-compliance-requirements.md)
- [Data Model and Integrations](04-data-model-and-integrations.md)
- [Technical Architecture](05-technical-architecture.md)
- [Roadmap and MVP](06-roadmap-and-mvp.md)
- [Phase 0 decisions](07-phase-0-decisions.md)

## Research Basis

This requirement pack was prepared from:

- Official Indian education and government references for compliance-sensitive areas.
- Public feature sets of school ERP products used in India for market completeness.
- Architecture assumptions for a Spring Boot, Angular, PostgreSQL, Docker/Coolify deployment.

Important official references:

- CBSE affiliation, examination, safety, disclosure, and school compliance resources: <https://www.cbse.gov.in/>
- CBSE academic resources and circulars: <https://cbseacademic.nic.in/>
- UDISE+ official portal and school data ecosystem: <https://udiseplus.gov.in/>
- APAAR ID official portal: <https://apaar.education.gov.in/>
- Ministry of Education, National Education Policy 2020: <https://www.education.gov.in/nep/about-nep>
- NCERT National Curriculum Framework resources: <https://ncf.ncert.gov.in/>
- Right of Children to Free and Compulsory Education Act, 2009 on India Code: <https://www.indiacode.nic.in/>
- Digital Personal Data Protection Act, 2023 on India Code: <https://www.indiacode.nic.in/>
- Ministry of Electronics and Information Technology DPDP resources: <https://www.meity.gov.in/>
- National Disaster Management Authority school safety resources: <https://ndma.gov.in/>
- National Commission for Protection of Child Rights resources: <https://ncpcr.gov.in/>
- Coolify documentation: <https://coolify.io/docs/>

Note: This is a product requirements document, not legal advice. Before launch, validate the final workflows with the target board, state education department, school accountant, legal advisor, and local compliance consultant.

## Product Vision

Build an integrated operating system for Indian schools that lets administrators run the institution, teachers manage daily academics, parents stay informed, students access learning and records, and management track compliance and finances from a single source of truth.

The product should reduce duplicate data entry, replace scattered spreadsheets, generate audit-ready records, support Indian board and state variations, and work reliably on modest infrastructure.

## Core Goals

1. Maintain a complete digital record for every student from enquiry to alumni.
2. Handle Indian admission, fee, attendance, assessment, transport, and compliance workflows.
3. Provide separate portals for admin, teacher, parent, student, accountant, transport, hostel, librarian, and management roles.
4. Support board-specific and state-specific configuration without custom code for every school.
5. Provide strong privacy, consent, audit, backup, and data retention controls.
6. Deploy cleanly on a VPS using Docker/Coolify with PostgreSQL as the main database.
7. Keep the first implementation practical by starting as a modular monolith, not premature microservices.

## Primary Users

- School owner, trustee, principal, vice principal.
- Academic coordinator, head of department, class teacher, subject teacher.
- Admission counsellor and front office staff.
- Accounts team and fee counter operator.
- HR, payroll, and administrator.
- Transport manager, driver, attendant.
- Hostel warden and mess manager.
- Librarian, inventory manager, lab in-charge.
- Parent or guardian.
- Student.
- Auditor, board inspection user, government reporting user.
- Super admin or product support user.

## High-Level Capability Map

| Area | Required capability | Priority |
|---|---|---|
| Platform | Multi-school setup, RBAC, audit logs, configuration | P0 |
| Student lifecycle | Enquiry, admission, profile, promotion, transfer, alumni | P0 |
| Academics | Classes, sections, subjects, timetable, lesson plans, homework | P0 |
| Attendance | Student/staff attendance, leave, alerts, reports | P0 |
| Fees | Fee heads, installments, concessions, collections, receipts, dues | P0 |
| Communication | Circulars, SMS/email/push/WhatsApp integrations, PTM | P0 |
| Exams | Assessment setup, marks entry, report cards, board workflows | P0 |
| Portals | Admin, teacher, parent, student, accountant dashboards | P0 |
| Compliance | UDISE, APAAR, CBSE/state board records, public disclosure support | P0 |
| HR/payroll | Staff records, attendance, leave, payroll, compliance exports | P1 |
| Transport | Routes, vehicles, stops, GPS integration, transport fees | P1 |
| Library | Catalogue, issue/return, fines, barcode/QR | P1 |
| Hostel | Room, bed, attendance, leave, visitors, mess | P1 |
| Inventory | Procurement, stock, assets, maintenance | P1 |
| Safety | Visitor management, incidents, health, counselling, drills | P1 |
| Analytics | Dashboards, custom reports, exports | P1 |
| LMS | Assignments, resources, online class links, question bank | P2 |
| Advanced | Mobile app, AI assistance, biometric/RFID, advanced BI | P2 |

Priority definitions:

- P0: Required for a credible first production release.
- P1: Required for a complete school ERP but can follow the first release.
- P2: Valuable differentiators or advanced capabilities.

## Key India-Specific Requirements

The system must support:

- Academic sessions commonly running April to March, with configurable session dates.
- Boards such as CBSE, CISCE, state boards, IB, and Cambridge as configurable master data.
- Classes Nursery/LKG/UKG to Class 12, streams in Classes 11 and 12, sections, houses, clubs, and optional batches.
- Indian address hierarchy including country, state, district, block/taluk/tehsil, city/village, ward, pincode.
- Student identifiers including admission number, roll number, registration number, UDISE/PEN where applicable, APAAR ID where consented, and board registration numbers.
- Parent/guardian relationships including father, mother, guardian, single parent, local guardian, and emergency contacts.
- Sensitive categories such as caste/community, minority status, religion, disability/CWSN, EWS/BPL/RTE category, and income bracket with role-based access and privacy controls.
- Fee structures using INR, Indian tax/accounting needs, receipts, concessions, refunds, and transport/hostel fee linkage.
- Board and government reporting formats that change over time.
- Configurable templates for transfer certificate, bonafide certificate, character certificate, marksheet, ID card, gate pass, fee receipt, circular, and official letters.
- Public disclosure data required by boards, especially CBSE-affiliated schools.
- Multilingual communication templates, especially English plus local language and Hindi where needed.
- Low-bandwidth usage patterns for parents and teachers.

## Recommended Product Shape

Build the first version as a modular ERP with these major portals:

- Admin web portal.
- Teacher web/mobile-responsive portal.
- Parent and student responsive portal or PWA.
- Public school website and disclosure portal.
- Optional super admin console for multi-school operations.

Use mobile-responsive Angular screens first. Native Android/iOS apps can come later if the school business model demands it.

## Non-Goals for the First Build

The first production version should not try to build:

- A complete LMS rivaling Google Classroom or Moodle.
- A payment gateway from scratch.
- A government API integration unless the API access, sandbox, and legal basis are confirmed.
- A full accounting system replacing Tally/Zoho Books.
- Native mobile apps before the web/PWA workflows are stable.
- Complex microservices before there is real scale pressure.

## Success Metrics

The product should be considered successful when:

- Admissions, student profile, attendance, fee collection, communication, exams, and reports can run without spreadsheets.
- Every important change has an audit trail.
- Fee due reports, attendance reports, student profile reports, and report cards are accurate and exportable.
- Parents can see attendance, homework, circulars, fees, receipts, transport, and results without contacting the office.
- Teachers can complete attendance and marks entry quickly from mobile or desktop.
- Admins can generate compliance and board-ready data with minimal manual cleanup.
- Backups and restore procedures are tested.

## Decisions Made

Recorded as architecture decision records in [docs/architecture/adr](../architecture/adr/):

| Question | Decision | Record |
|---|---|---|
| One school, a group, or many schools? | Built for many. One PostgreSQL schema per school in one database, so the tenant boundary is structural rather than a `WHERE` clause; a dedicated database stays available for a school that needs physical isolation. | [ADR-0011](../architecture/adr/0011-schema-per-tenant.md) |
| School-controlled authentication or external SSO? | School-controlled, owned by the `identity` module. Sessions, not JWT. External SSO stays addable as another credential type. | [ADR-0003](../architecture/adr/0003-authentication-and-authorization.md) |
| How do users log in? | Username/email and password for the first release. Credentials are pluggable, so phone + OTP, MFA and SSO are additive rather than a migration. | [ADR-0003](../architecture/adr/0003-authentication-and-authorization.md) |
| Role-based or fine-grained permissions? | Permissions are the unit of enforcement and are defined in code; roles are per-school editable bundles of them; each grant carries a scope (campus, class, section, subject, ward). | [ADR-0005](../architecture/adr/0005-authorization-model.md) |
| Where does the menu come from? | The server, after login, derived from the user's permissions and the school's enabled modules. It sends stable ids and translation keys, never URLs or layout — and a hidden item is never treated as an access control. | [ADR-0008](../architecture/adr/0008-server-driven-navigation.md) |
| Buy or build the UI components? | Build. No third-party visual component library; Angular CDK is used for behaviour (focus management, overlays, virtual scroll) because that part is hard and invisible. | [ADR-0009](../architecture/adr/0009-hand-built-component-library.md) |
| How do we version the API? | We do not. One client ships with the backend, so a breaking change is one atomic pull request. `/api/v1` is a frozen base path, not a version number. Revisit when a consumer exists that cannot be forced to upgrade. | [ADR-0016](../architecture/adr/0016-no-api-versioning.md) |
| How is one installation split between schools? | One PostgreSQL schema per school in a single database, one connection pool. Migrations fan out to every schema at startup; a tenant registry in `public` lists them. | [ADR-0011](../architecture/adr/0011-schema-per-tenant.md) |
| How does the layout work across devices? | Three window size classes (600px / 840px) — bottom bar, icon rail, sidebar. Compact navigation adapts to how many destinations the user actually has, since that is permission-dependent. Mobile is the default, not the fallback. | [ADR-0010](../architecture/adr/0010-responsive-and-adaptive-layout.md) |
| How configurable should the product be? | Four tiers — master data, typed per-school settings, named strategies, and a deliberate list of things that stay fixed. A setting earns its place when two real schools disagree about it. | [ADR-0006](../architecture/adr/0006-configurability-model.md) |
| How is money modelled? | Append-only. Immutable charges plus signed payment, concession, refund and reversal rows; a balance is always a sum, never a stored field. A correction is a credit note, never an edit. | [ADR-0012](../architecture/adr/0012-fee-ledger-model.md) |
| Which payment gateway and messaging providers? | None yet. Both are ports. v1 registers offline fee collection, transactional email and web push; Razorpay, SMS and WhatsApp are later adapters against a data model that is already ready for them. | [ADR-0013](../architecture/adr/0013-external-provider-ports.md) |
| How is children's data protected? | Four classification tiers declared on DTO fields, driving log redaction, error responses and export masking — with an unclassified field failing the build. Consent and retention are part of the model. | [ADR-0014](../architecture/adr/0014-data-classification.md) |
| Where does it run? | One Hostinger KVM 2 in Mumbai via Coolify: PostgreSQL, backend, frontend and MinIO on one box. ~20–30 ms to Delhi, data in India, backups off-box and restore-drilled. | [ADR-0015](../architecture/adr/0015-deployment-baseline.md) |

## Open Decisions

[Phase 0](07-phase-0-decisions.md) closed the questions that used to sit here: the first board
(CBSE), the first state (Delhi NCR), the school type (K-12 day, single campus), the MVP list, the
admission, fee, exam, attendance and certificate workflows, the providers, the deployment baseline,
the data classification policy and the sample data plan.

What remains open:

- Which SMS and WhatsApp providers, once TRAI DLT entity registration completes — that process
  reveals which are painless. **DLT registration itself starts now**, independently of when SMS
  ships, because it is weeks of paperwork on the critical path of fee reminders and phone-OTP login.
- Which payment gateway, once the pilot school's bank and settlement account are known. Razorpay is
  the intended first adapter, not a commitment.
- When phone + OTP login supplements passwords for parents and students — blocked on DLT above.
- Which GPS provider, when transport reaches Phase 4.
- Whether a Tally or accounting export is needed, and in what shape, when a school asks for it.
