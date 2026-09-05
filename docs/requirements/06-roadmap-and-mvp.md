# Roadmap and MVP

## Delivery Strategy

Build the system in phases. A "full" school ERP is large, so the safest approach is to deliver a strong operational core first and then expand modules without rewriting the foundation.

Recommended sequence:

1. Platform and school setup.
2. Student lifecycle.
3. Attendance, fees, communication, and reports.
4. Exams and academics.
5. Parent/student/teacher portals.
6. HR, transport, library, hostel, inventory, and compliance expansion.
7. Advanced integrations and analytics.

## Phase 0: Discovery and Foundation

Goal: Remove ambiguity before coding large modules.

Deliverables:

- Select first target board and state.
- Select first target school type.
- Confirm admission workflow.
- Confirm fee heads and accounting process.
- Confirm exam/report card format.
- Confirm attendance rules.
- Confirm certificate templates.
- Confirm payment gateway.
- Confirm communication providers.
- Confirm deployment VPS specs.
- Finalize data classification policy.
- Finalize MVP module list.
- Prepare sample data.

Output:

- Final product backlog.
- Database naming conventions.
- API conventions.
- UI design system.
- Deployment baseline.

## Phase 1: Platform, Master Data, and Student Core

Goal: Create the ERP foundation and student source of truth.

Features:

- School profile.
- Academic session.
- Classes, sections, subjects.
- Roles and permissions.
- User management.
- Student profile.
- Guardian profile.
- Documents.
- Import/export.
- Basic dashboards.
- Audit log.

Exit criteria:

- Admin can configure one school and academic session.
- Admin can import or create students and guardians.
- Users can log in with correct permissions.
- Audit logs capture critical changes.

## Phase 2: Admissions, Attendance, Fees, and Communication

Goal: Support daily operational work.

Features:

- Enquiry management.
- Online admission form.
- Admission workflow.
- Student conversion.
- Daily attendance.
- Leave requests.
- Absence alerts.
- Fee structure.
- Fee demand.
- Online/offline fee collection.
- Receipts.
- Dues and reminders.
- Circulars and notices.
- Parent portal basics.
- Teacher portal basics.

Exit criteria:

- A school can run admissions, mark attendance, collect fees, and communicate with parents.
- Fee ledger reconciles correctly.
- Parents can see attendance, circulars, dues, and receipts.

## Phase 3: Academics, Timetable, Exams, and Report Cards

Goal: Support academic management and result publishing.

Features:

- Timetable.
- Substitution.
- Homework.
- Lesson plans.
- Exam setup.
- Marks entry.
- Result calculation.
- Report cards.
- Exam analytics.
- Student promotion.
- Certificates.

Exit criteria:

- Teachers can manage homework and marks.
- Admin can publish report cards.
- Students can be promoted to next session.
- Certificates can be generated with approval.

## Phase 4: Compliance and Complete ERP Modules

Goal: Expand from core ERP to complete school operations.

Features:

- Compliance dashboard.
- UDISE-style data mapping and export.
- APAAR consent tracking.
- Mandatory public disclosure support.
- HR records.
- Staff attendance.
- Payroll.
- Transport.
- Library.
- Hostel.
- Inventory and assets.
- Visitor management.
- Health and safety.
- Incident management.

Exit criteria:

- School can track compliance readiness.
- School can run transport, library, hostel, HR, and inventory modules from the same system.
- Public disclosure pages can be generated from approved data.

## Phase 5: Advanced Features

Goal: Improve differentiation and scale.

Features:

- GPS live tracking.
- Biometric/RFID integrations.
- WhatsApp Business integration.
- Advanced analytics.
- Custom report builder.
- PWA offline drafts.
- Multi-branch analytics.
- Accounting software integration.
- SSO.
- API marketplace/integration framework.
- Native mobile apps if justified.

## MVP Recommendation

The first production release should include:

- School setup.
- Academic session.
- Role and permission system.
- Student and guardian records.
- Admissions.
- Attendance.
- Fee management.
- Receipts and dues.
- Communication.
- Teacher portal.
- Parent portal.
- Basic exam and report card.
- Certificates.
- Document storage.
- Audit logs.
- Basic compliance fields.
- Backups.
- Coolify deployment.

Do not include payroll, hostel, inventory, GPS, biometric, and custom report builder in the first MVP unless the first paying school requires them.

## Suggested MVP Epics

### Epic 1: Platform Setup

Stories:

- As a super admin, I can create a school.
- As an admin, I can create an academic session.
- As an admin, I can configure classes, sections, subjects, and fee heads.
- As an admin, I can create users and assign roles.
- As a system, I record audit logs for critical actions.

### Epic 2: Student and Guardian Records

Stories:

- As an admin, I can add student and guardian profiles.
- As an admin, I can link siblings under one guardian account.
- As an admin, I can upload student documents.
- As an admin, I can import student records from Excel.
- As a teacher, I can view students assigned to my class or subject.

### Epic 3: Admissions

Stories:

- As a parent, I can submit an online application.
- As an admission counsellor, I can track enquiries and follow-ups.
- As an admin, I can approve an admission.
- As an accountant, I can collect registration/admission fees.
- As a system, I can create a student record from an admitted applicant.

### Epic 4: Attendance

Stories:

- As a teacher, I can mark daily attendance quickly.
- As a parent, I receive an absence alert.
- As an admin, I can view class-wise and student-wise attendance.
- As a principal, I can view short-attendance reports.
- As a teacher, I can request correction after attendance lock.

### Epic 5: Fees

Stories:

- As an accountant, I can define fee structures and due dates.
- As an accountant, I can apply concessions with approval.
- As a parent, I can pay fees online.
- As an accountant, I can record offline payments.
- As a system, I generate receipts and update dues.
- As management, I can view collection and outstanding reports.

### Epic 6: Communication

Stories:

- As an admin, I can send circulars to selected groups.
- As a teacher, I can send homework to my class.
- As a parent, I can see notices and homework.
- As a system, I track delivery status.
- As an admin, I can collect acknowledgement or consent.

### Epic 7: Exams and Report Cards

Stories:

- As an exam controller, I can create exams and grading rules.
- As a teacher, I can enter marks.
- As an exam controller, I can verify and lock marks.
- As a principal, I can publish report cards.
- As a parent, I can view report cards.

### Epic 8: Documents and Certificates

Stories:

- As an admin, I can configure certificate templates.
- As a parent, I can request a certificate.
- As an admin, I can approve and generate certificates.
- As an external verifier, I can verify a QR code without accessing private data.

### Epic 9: Deployment and Operations

Stories:

- As a developer, I can deploy the app through Coolify.
- As an admin, I can configure environment variables.
- As a system, I run database migrations on deployment.
- As an operator, I can view health checks and logs.
- As an operator, I can restore from backup.

## MVP Acceptance Criteria

The MVP is production-ready only when:

- All P0 workflows work end to end.
- Data isolation works for school/session/class/role scopes.
- Fee ledger passes reconciliation tests.
- Attendance calculations are correct.
- Report card calculations match configured rules.
- Parent and teacher mobile views are usable.
- Audit logs are generated for sensitive actions.
- Backups are automated and restore-tested.
- Payment webhooks are idempotent.
- Basic security tests pass.
- Admin can export all critical data.

## Suggested Timeline

Actual timeline depends on team size and product quality expectations. For a small team:

- Phase 0: 2 to 4 weeks.
- Phase 1: 6 to 8 weeks.
- Phase 2: 8 to 12 weeks.
- Phase 3: 8 to 12 weeks.
- Phase 4: 12 to 20 weeks.
- Phase 5: ongoing.

A realistic full ERP can take 9 to 18 months for a small team if built carefully.

## Key Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Trying to build every module before validating core workflows | Slow launch and weak product quality | Ship core ERP first |
| Hardcoding CBSE or one state's rules | Difficult expansion | Use compliance profiles |
| Weak fee ledger design | Financial mismatch and loss of trust | Build ledger-style financial model and tests |
| Weak permissions | Data leaks | Design RBAC/ABAC early |
| Ignoring privacy for children | Legal and reputational risk | Build consent, masking, and audit logs |
| Underestimating report cards | Delayed launch | Collect real report card formats early |
| Poor import tooling | Slow onboarding | Build dry-run imports and validation |
| No restore testing | Data loss risk | Schedule restore drills |
| Too much custom work per school | Product becomes unmaintainable | Add configuration, not one-off code |

## First Technical Milestones

1. Create repo structure.
2. Add Docker Compose for local PostgreSQL, Redis, and MinIO.
3. Add Spring Boot app with health endpoint.
4. Add Angular app with shell layout.
5. Add authentication and role model.
6. Add Flyway/Liquibase migrations.
7. Add school/session/class/student core tables.
8. Add audit log infrastructure.
9. Add document storage abstraction.
10. Add first E2E workflow: create school, create student, login as teacher/parent.

## Backlog Ordering Recommendation

Build in this order:

1. Identity, school setup, academic session.
2. Student and guardian.
3. Attendance.
4. Fees.
5. Communication.
6. Admissions.
7. Exams and report cards.
8. Certificates.
9. Compliance dashboard.
10. Transport, HR, library, hostel, inventory.

Reasoning:

- Student, session, and permission models are shared by every other module.
- Attendance and fees create immediate school value.
- Communication makes parent adoption easier.
- Exams and report cards need stable academic and student data.
- Complete ERP modules become easier after the core data model is mature.

## Launch Checklist

- Production domain configured.
- HTTPS enabled.
- Admin user created.
- School profile completed.
- Academic session configured.
- Classes, sections, subjects, and fee heads configured.
- Student import completed and verified.
- Fee opening balances verified.
- Teacher accounts created.
- Parent accounts linked.
- Payment gateway tested.
- SMS/email tested.
- Backup job tested.
- Restore drill completed.
- Privacy policy and terms uploaded.
- Staff training completed.
- Rollback plan prepared.

