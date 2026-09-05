# Project Context

## Product

This repository is for a full school management system for Indian K-12 schools.

Primary stack:

- Backend: Spring Boot.
- Frontend: Angular.
- Database: PostgreSQL.
- Deployment: VPS using Coolify.

Primary product requirements live in:

- [requirement.md](../requirement.md)
- [requirements/01-product-scope.md](../requirements/01-product-scope.md)
- [requirements/02-functional-requirements.md](../requirements/02-functional-requirements.md)
- [requirements/03-india-compliance-requirements.md](../requirements/03-india-compliance-requirements.md)
- [requirements/04-data-model-and-integrations.md](../requirements/04-data-model-and-integrations.md)
- [requirements/05-technical-architecture.md](../requirements/05-technical-architecture.md)
- [requirements/06-roadmap-and-mvp.md](../requirements/06-roadmap-and-mvp.md)

## Product Principle

The product must become the source of truth for school operations. Treat student records, fees, attendance, exams, official documents, compliance reports, and audit history as long-lived institutional records.

## Target Users

Design for these user groups:

- Management: owner, trustee, principal, vice principal, administrator.
- Academic staff: coordinators, HODs, class teachers, subject teachers, exam controllers.
- Operations staff: admissions, front office, accounts, HR, librarian, transport, hostel, inventory, nurse, counsellor, security.
- Parents and guardians.
- Students.
- Auditors, inspectors, and limited external users.

## Domain Constraints

Indian school workflows vary by board and state. Common variables include:

- Academic session dates.
- Class naming.
- Board and affiliation rules.
- Fee regulation.
- Admission process.
- RTE/EWS categories.
- Attendance thresholds.
- Exam patterns and report cards.
- Staff qualification reporting.
- Public disclosure expectations.
- Transport and hostel rules.

Do not implement these as one-off hardcoded constants. Model them as configurable settings, templates, rule profiles, or compliance profiles.

## Sensitive Data

The application handles child data and school finance data. Handle these fields with special care:

- Student identity and date of birth.
- Parent/guardian contact details.
- APAAR ID, PEN, board registration numbers, and similar identifiers.
- Aadhaar-related data if ever collected.
- Caste/community, religion, minority, RTE/EWS/BPL, income, and disability/CWSN fields.
- Medical, counselling, discipline, safeguarding, and incident records.
- Fee transactions, bank data, payroll, and payment gateway identifiers.
- Uploaded documents and generated official certificates.

Default to least privilege, masking, audit logs, and explicit consent where required.

## MVP Focus

The first serious release should prioritize:

- School setup.
- Academic session.
- Roles and permissions.
- Students and guardians.
- Admissions.
- Attendance.
- Fees and receipts.
- Communication.
- Basic exams and report cards.
- Teacher and parent portals.
- Documents and certificates.
- Audit logs.
- Backups and Coolify deployment.

Avoid building advanced modules before the core data model is stable.

## Architectural Direction

Use a modular monolith first.

Reasons:

- Easier to build and deploy on a VPS.
- Easier to keep transactions consistent for fees, attendance, admissions, and exams.
- Easier for a small team to maintain.
- Still allows module extraction later.

Each module must own its business rules and expose clear service/API boundaries.

## Source of Truth Rules

- Product behavior must trace back to requirements, configuration, or documented decisions.
- If requirements are ambiguous, implement the smaller safe version and document the assumption.
- Do not silently invent compliance behavior.
- Prefer a configuration point when a rule changes by board, state, campus, session, or school.

## Compliance Disclaimer

Requirements and guidelines in this repository are engineering guidance, not legal advice. Before production launch, validate workflows with the target school, board, state education department, accountant, and legal/compliance advisor.

## Official Reference Links

- CBSE: <https://www.cbse.gov.in/>
- CBSE Academic: <https://cbseacademic.nic.in/>
- UDISE+: <https://udiseplus.gov.in/>
- APAAR: <https://apaar.education.gov.in/>
- Ministry of Education NEP: <https://www.education.gov.in/nep/about-nep>
- NCERT NCF: <https://ncf.ncert.gov.in/>
- India Code: <https://www.indiacode.nic.in/>
- MeitY: <https://www.meity.gov.in/>
- NDMA: <https://ndma.gov.in/>
- NCPCR: <https://ncpcr.gov.in/>

