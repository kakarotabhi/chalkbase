# Product Scope

## Scope Statement

The school management system will be a web-based ERP for Indian schools. It will manage the operational, academic, financial, staff, communication, safety, and compliance workflows of a school.

The product should support:

- A standalone private school.
- A trust or group running multiple campuses.
- A future SaaS model where separate schools use the same deployed product with strong tenant isolation.

The initial architecture should serve one school well while keeping the data model compatible with multiple schools and branches.

## Institution Types

The system should support configuration for:

- Pre-primary schools.
- K-10 schools.
- K-12 schools.
- Day schools.
- Residential or boarding schools.
- Day boarding schools.
- Schools with transport service.
- Schools with multiple campuses.
- CBSE schools.
- CISCE schools.
- State board schools.
- Schools that follow more than one board or curriculum in separate wings.

## Product Modules

Core modules:

- Organization setup and master data.
- Student admissions.
- Student information system.
- Academic structure.
- Attendance.
- Timetable.
- Homework, assignments, lesson planning.
- Exams, grades, report cards.
- Fee management.
- Receipts, concessions, refunds, and dues.
- Communication and notifications.
- Parent, student, teacher, and admin portals.
- Reports and dashboards.
- Compliance data management.

Complete ERP modules:

- HR and payroll.
- Transport.
- Hostel.
- Library.
- Inventory and procurement.
- Asset management.
- Visitor management.
- Health, counselling, and safety.
- Discipline and wellbeing.
- Certificates and document generation.
- Public website and mandatory disclosure.
- Alumni.
- Helpdesk.
- Event and calendar management.

Platform modules:

- Authentication and authorization.
- Role-based and attribute-based access control.
- Tenant and campus management.
- Configurable workflows.
- Notification templates.
- Document storage.
- Audit logging.
- Backup and restore.
- Data import and export.
- Integration management.
- System settings.

## User Groups

### Management

Users:

- Owner.
- Trustee.
- Director.
- Principal.
- Vice principal.
- Administrator.

Needs:

- School-wide dashboard.
- Admission numbers and conversion.
- Fee collection and dues.
- Attendance trends.
- Class performance.
- Staff attendance and workload.
- Compliance readiness.
- Cash/bank/payment summaries.
- Transport and hostel summaries.
- Incidents and escalations.
- Audit logs and user activity.

### Academic Staff

Users:

- Academic coordinator.
- Head of department.
- Class teacher.
- Subject teacher.
- Exam controller.

Needs:

- Class and subject allocation.
- Timetable.
- Student lists.
- Daily attendance.
- Homework and assignments.
- Lesson plan tracking.
- Marks entry.
- Report card review.
- Substitution duties.
- PTM notes.
- Student behaviour and wellbeing notes.

### Non-Academic Staff

Users:

- Admission counsellor.
- Front office executive.
- Accountant.
- HR manager.
- Librarian.
- Transport manager.
- Hostel warden.
- Inventory manager.
- Lab assistant.
- Nurse.
- Counsellor.
- Security gate user.

Needs:

- Role-specific task screens.
- Fast search.
- Student and parent contact access where permitted.
- Receipts and certificates.
- Visitor and gate pass workflows.
- Inventory and issue-return workflows.
- Health and incident logs.

### Parents and Guardians

Needs:

- Student profile.
- Attendance.
- Homework and assignments.
- Circulars and notices.
- Fee dues and payment.
- Receipts.
- Exam schedules and results.
- Transport details.
- Hostel details where applicable.
- PTM booking.
- Leave application.
- Complaint/helpdesk.
- Consent forms.
- Certificate requests.

### Students

Needs:

- Timetable.
- Homework and assignments.
- Learning resources.
- Attendance summary.
- Exam schedule.
- Result and report card.
- Library account.
- Certificates and achievements.
- Clubs, events, competitions.

### External and Limited Users

Users:

- Auditor.
- Board inspection user.
- Government reporting user.
- Vendor.
- Alumni.

Needs:

- Restricted access.
- Exportable records.
- Public verification links.
- Compliance evidence.

## Core Lifecycle Workflows

### Student Lifecycle

1. Enquiry.
2. Application.
3. Document submission.
4. Interaction, test, or interview if allowed by board/state rules.
5. Selection, waiting list, rejection, or hold.
6. Admission offer.
7. Registration/admission fee.
8. Student profile creation.
9. Class and section allocation.
10. ID card, login, transport, hostel, library, and fee plan setup.
11. Attendance, academics, exams, communication, and support.
12. Promotion or detention as per school policy and board rules.
13. Transfer certificate or alumni conversion.

### Academic Year Lifecycle

1. Create academic session.
2. Define classes, sections, streams, subjects, electives, houses, and terms.
3. Promote students from previous session.
4. Assign class teachers and subject teachers.
5. Create timetable.
6. Configure fee plans.
7. Configure exams and grading.
8. Publish calendar.
9. Run attendance, homework, exams, fee collection, events, and reporting.
10. Close the year.
11. Archive records and generate annual reports.

### Fee Lifecycle

1. Define fee heads.
2. Define fee groups and installments.
3. Assign fee plan by class, section, transport route, hostel, category, or custom student group.
4. Apply concession, scholarship, RTE/EWS category, staff child benefit, sibling discount, or special waiver.
5. Generate demand.
6. Collect payment online or offline.
7. Generate receipt.
8. Reconcile gateway, bank, cash, cheque, or UPI collections.
9. Apply fine, waive fine, or reschedule due.
10. Process refunds, cancellations, or adjustments.
11. Export accounting entries.

### Examination Lifecycle

1. Configure exam pattern for board and school.
2. Create terms, assessments, subjects, marks, grades, weightage, and skill areas.
3. Publish exam schedule.
4. Generate hall tickets or admit cards.
5. Enter marks, internal assessments, practical marks, projects, and remarks.
6. Validate marks and lock entry.
7. Moderate if permitted.
8. Generate report cards and analytics.
9. Publish results to parent/student portal.
10. Archive exam data.

### Compliance Lifecycle

1. Maintain required school, staff, student, infrastructure, safety, and finance data.
2. Validate completeness against selected board/state profile.
3. Generate export-ready reports.
4. Store certificates and renewal dates.
5. Track submission status, acknowledgement, and attachments.
6. Maintain audit logs of changes and exports.

## Indian Localization

The system must support:

- INR amounts and Indian date formats.
- Academic sessions such as 2026-27.
- Admission classes like Nursery, LKG, UKG, I to XII.
- Streams such as Science, Commerce, Humanities, Vocational.
- Subject combinations for Classes 9 to 12.
- Second language and third language choices.
- Roll number generation per class/section.
- House allocation.
- Scholarship, EWS, BPL, RTE, CWSN, minority, caste/community, religion, and income fields with privacy controls.
- Local address hierarchy and pincode validation.
- Mother tongue and medium of instruction.
- Board registration details.
- Transfer certificate details.
- Government identifiers where legally permitted and consented.
- Hindi and regional language labels/templates.

## Deployment Assumptions

The product will be deployed on a VPS through Coolify using containers.

The deployment should include:

- Angular frontend container or static site.
- Spring Boot API container.
- PostgreSQL database.
- Redis for cache, sessions, rate limiting, and queues where needed.
- Object storage such as S3-compatible storage or MinIO for documents.
- Scheduled backup jobs.
- Reverse proxy and TLS certificates managed through the platform.
- Environment variable based configuration.
- Health checks and logs.

## Out of Scope Unless Explicitly Added

- Native mobile app development in the first release.
- Biometric device SDK work unless the hardware vendor and API are known.
- Direct government API submission unless credentials and integration documents are available.
- Full GST/accounting replacement.
- AI-based student evaluation, proctoring, or facial recognition.
- ERP customizations for colleges or universities.

