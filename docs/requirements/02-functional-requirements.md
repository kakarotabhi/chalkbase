# Functional Requirements

Priority:

- P0: First production release.
- P1: Full ERP release.
- P2: Advanced or differentiating feature.

## 1. Platform, Tenant, and User Management

| ID | Requirement | Priority |
|---|---|---|
| FR-001 | The system shall support one or more schools under one installation. | P0 |
| FR-002 | The system shall support campuses/branches under a school group. | P1 |
| FR-003 | The system shall maintain academic sessions and allow session-specific configuration. | P0 |
| FR-004 | The system shall enforce access through named permissions of the form `module:resource:action`, defined in code and seeded per release. Authorization checks shall reference permissions only, never role names. | P0 |
| FR-004a | The system shall ship versioned system role templates (principal, class teacher, accountant, librarian, transport manager, parent, student, auditor and similar) that a school copies when it is onboarded. | P0 |
| FR-004b | The system shall allow each school to create and edit its own roles as bundles of permissions, independently of other schools and of the templates they were copied from. | P0 |
| FR-004c | The system shall notify school administrators when a release introduces permissions their roles do not yet grant, and shall not widen access automatically. | P0 |
| FR-005 | The system shall grant a role to a user within a scope — school, campus, department, class, section, subject, self or ward — and shall allow a user to hold several such grants at once. | P0 |
| FR-005a | The system shall support validity periods on a grant, so temporary responsibilities such as an acting principal or an exam controller expire without manual removal. | P1 |
| FR-005b | The system shall apply scope restrictions within data queries rather than by filtering results after retrieval. | P0 |
| FR-005c | The system shall derive parent and student access from the guardian and enrolment relationship rather than from an administrator-assigned scope. | P0 |
| FR-005d | The system shall compute access as the union of a user's grants. Explicit deny rules shall not be supported. | P0 |
| FR-006 | The system shall support user activation, deactivation, password reset, forced logout, and account lockout. | P0 |
| FR-006a | The system shall separate a user's identity from their credentials, supporting several identifier types (username, email, phone) and several credential types per user, so that additional login methods can be added without migrating existing accounts. | P0 |
| FR-006b | The system shall authenticate the first release with username or email and password, and shall issue a server-side session referenced by an HttpOnly cookie. | P0 |
| FR-007 | The system shall support optional multi-factor authentication for staff and admins. | P1 |
| FR-007a | The system shall support phone number and one-time password login for parents and students. | P1 |
| FR-007b | The system shall provide a platform-level support role that exists outside any school, is time-boxed, and records every access to school data in the audit log. | P1 |
| FR-008 | The system shall maintain a complete audit log for create, update, delete, approval, login, export, and publish actions. | P0 |
| FR-009 | The system shall support configurable master data for board, class, section, subject, category, religion, caste/community, language, fee head, designation, and department. | P0 |
| FR-010 | The system shall provide import and export for master data through validated CSV/XLSX templates. | P0 |

Acceptance notes:

- Every privileged action must be traceable to user, role, timestamp, IP/device where available, and changed fields.
- Admin users must be able to preview permission impact before assigning roles.
- Every endpoint must carry an explicit authorization check; this is verified automatically rather than by review.
- Permission identifiers are treated as public API. Renaming one requires a migration that rewrites existing school roles and grants.

See [ADR-0005](../architecture/adr/0005-authorization-model.md) for the authorization model and
[ADR-0003](../architecture/adr/0003-authentication-and-authorization.md) for authentication.

## 2. Organization Setup

| ID | Requirement | Priority |
|---|---|---|
| FR-011 | The system shall maintain school profile details including name, address, contact, logo, affiliation, UDISE code, board, medium, trust/society details, and recognition data. | P0 |
| FR-012 | The system shall maintain campus details, buildings, floors, classrooms, labs, library, playground, hostel, transport office, and other facilities. | P1 |
| FR-013 | The system shall maintain certificate and compliance document records with issue date, expiry date, attachment, and renewal reminders. | P0 |
| FR-014 | The system shall maintain bank accounts, payment modes, receipt series, and accounting settings. | P0 |
| FR-015 | The system shall support school calendar, holidays, working days, special working days, and exam days. | P0 |

## 3. Admissions and Enquiry Management

| ID | Requirement | Priority |
|---|---|---|
| FR-016 | The system shall capture enquiries from walk-in, phone, website, referral, campaign, and imported sources. | P0 |
| FR-017 | The system shall track enquiry status, counsellor assignment, follow-up history, reminders, and conversion analytics. | P0 |
| FR-018 | The system shall provide online admission forms configurable by class and board. | P0 |
| FR-019 | The system shall collect applicant details, parent/guardian details, sibling details, previous school details, transport need, hostel need, and document uploads. | P0 |
| FR-020 | The system shall support seat matrix by class, category, quota, gender, stream, and section where applicable. | P1 |
| FR-021 | The system shall support admission stages such as submitted, document pending, interaction scheduled, selected, waitlisted, rejected, offered, admitted, and withdrawn. | P0 |
| FR-022 | The system shall support application fee and registration fee collection. | P0 |
| FR-023 | The system shall support RTE/EWS/disadvantaged group admission tagging where applicable. | P0 |
| FR-024 | The system shall generate admission number according to configurable rules. | P0 |
| FR-025 | The system shall convert an admitted applicant into a student record without re-entry. | P0 |
| FR-026 | The system shall support duplicate detection by mobile, email, previous application, Aadhaar where legally permitted, and student name/date of birth combination. | P1 |
| FR-027 | The system shall support admission offer letters and admission cancellation/refund workflows. | P1 |

## 4. Student Information System

| ID | Requirement | Priority |
|---|---|---|
| FR-028 | The system shall maintain a complete student profile with personal, academic, guardian, contact, medical, transport, hostel, document, and compliance sections. | P0 |
| FR-029 | The system shall support student identifiers including admission number, roll number, registration number, PEN/UDISE identifier where applicable, APAAR ID where consented, and board registration number. | P0 |
| FR-030 | The system shall maintain parent and guardian profiles independent of student profiles so siblings can share guardian records. | P0 |
| FR-031 | The system shall support class, section, roll number, stream, house, club, and elective assignment. | P0 |
| FR-032 | The system shall support student photo, signature, documents, certificates, and verification status. | P0 |
| FR-033 | The system shall maintain previous school records, transfer certificate details, and migration details. | P0 |
| FR-034 | The system shall maintain CWSN/disability, allergies, chronic conditions, medication, blood group, and emergency contact with restricted access. | P0 |
| FR-035 | The system shall support student status values such as active, inactive, long absent, transferred, graduated, withdrawn, deceased, and alumni. | P0 |
| FR-036 | The system shall support promotion, detention, section transfer, subject change, stream change, and session history. | P0 |
| FR-037 | The system shall support bulk updates with approval and rollback logs. | P1 |

## 5. Academic Structure

| ID | Requirement | Priority |
|---|---|---|
| FR-038 | The system shall define classes, sections, terms, streams, subjects, subject groups, electives, and co-scholastic areas. | P0 |
| FR-039 | The system shall support class-wise and board-wise grading schemes. | P0 |
| FR-040 | The system shall support subject combinations for Classes 9 to 12. | P0 |
| FR-041 | The system shall validate teacher-subject-class allocation conflicts. | P0 |
| FR-042 | The system shall support academic departments and heads of department. | P1 |
| FR-043 | The system shall support teaching load calculations. | P1 |

## 6. Attendance

| ID | Requirement | Priority |
|---|---|---|
| FR-044 | The system shall support daily attendance by class teacher. | P0 |
| FR-045 | The system shall support period-wise or subject-wise attendance. | P1 |
| FR-046 | The system shall support present, absent, late, half-day, excused, medical leave, activity duty, and holiday states. | P0 |
| FR-047 | The system shall support leave applications by parent/student and approval by authorized staff. | P0 |
| FR-048 | The system shall send absence and late alerts to parents through configured channels. | P0 |
| FR-049 | The system shall generate monthly attendance, percentage, short-attendance, and board eligibility reports. | P0 |
| FR-050 | The system shall support import from biometric, RFID, QR, or device APIs where integrated. | P2 |
| FR-051 | The system shall lock attendance after a configurable period while allowing authorized corrections with reason and audit trail. | P0 |

## 7. Timetable and Substitution

| ID | Requirement | Priority |
|---|---|---|
| FR-052 | The system shall define periods, breaks, working days, rooms, labs, and special schedules. | P0 |
| FR-053 | The system shall create class-wise, teacher-wise, room-wise, and subject-wise timetables. | P0 |
| FR-054 | The system shall detect conflicts in teacher, room, class, and lab allocation. | P0 |
| FR-055 | The system shall support substitutions for absent teachers. | P0 |
| FR-056 | The system shall publish timetables to teacher, student, and parent portals. | P0 |
| FR-057 | The system shall support timetable generation assistance using constraints. | P2 |

## 8. Lesson Planning, Homework, and LMS-lite

| ID | Requirement | Priority |
|---|---|---|
| FR-058 | The system shall maintain syllabus plans by class, subject, term, chapter, topic, and learning outcome. | P0 |
| FR-059 | The system shall support annual plan, monthly plan, lesson plan, completion tracking, and review by coordinator/HOD. | P1 |
| FR-060 | The system shall allow teachers to assign homework with due date, attachment, marks, and remarks. | P0 |
| FR-061 | The system shall allow students to submit assignments online. | P1 |
| FR-062 | The system shall support study material links, videos, PDFs, worksheets, and question banks. | P1 |
| FR-063 | The system shall support online class links through external providers. | P2 |
| FR-064 | The system shall support teacher feedback on submissions. | P1 |

## 9. Exams, Assessment, and Report Cards

| ID | Requirement | Priority |
|---|---|---|
| FR-065 | The system shall support exam types such as unit test, periodic test, term exam, pre-board, practical, internal assessment, project, viva, and activity. | P0 |
| FR-066 | The system shall support marks-based, grades-based, descriptive, competency-based, and skill-based assessments. | P0 |
| FR-067 | The system shall configure exam schedules, subject marks, minimum passing marks, weightage, grade scale, and result rules. | P0 |
| FR-068 | The system shall support marks entry by subject teacher with locking and approval. | P0 |
| FR-069 | The system shall support absent, medical, exempted, not applicable, and withheld marks states. | P0 |
| FR-070 | The system shall generate class-wise mark sheets, toppers, subject analysis, failure lists, and remedial lists. | P0 |
| FR-071 | The system shall generate customizable report cards with school logo, signatures, grades, remarks, attendance, and QR verification. | P0 |
| FR-072 | The system shall support board-specific assessment patterns for CBSE, CISCE, and state boards through configuration. | P0 |
| FR-073 | The system shall support board registration, LOC data preparation, admit card data, practical marks, and internal assessment exports where applicable. | P1 |
| FR-074 | The system shall support CWSN assessment accommodation records with restricted access. | P1 |

## 10. Fee Management and Accounts

| ID | Requirement | Priority |
|---|---|---|
| FR-075 | The system shall define fee heads, fee groups, installments, due dates, fines, and waivers. | P0 |
| FR-076 | The system shall assign fee structures by class, section, student category, transport route, hostel, optional subject, or individual student. | P0 |
| FR-077 | The system shall support one-time, monthly, quarterly, term-wise, annual, and custom fee schedules. | P0 |
| FR-078 | The system shall support concessions, scholarships, staff-child discount, sibling discount, management quota concession, and RTE/EWS tagging. | P0 |
| FR-079 | The system shall generate fee demand and due reports. | P0 |
| FR-080 | The system shall collect online payments through payment gateway integration. | P0 |
| FR-081 | The system shall collect offline payments through cash, cheque, DD, UPI reference, POS, bank transfer, and adjustment. | P0 |
| FR-082 | The system shall generate unique receipts with configurable receipt series and cancellation/void workflow. | P0 |
| FR-083 | The system shall support partial payments, advance payments, refunds, cheque bounce, reversals, and write-offs. | P0 |
| FR-084 | The system shall send fee reminders and receipts through configured channels. | P0 |
| FR-085 | The system shall reconcile payment gateway settlements and bank deposits. | P1 |
| FR-086 | The system shall export accounting data to Tally/CSV or other accounting software. | P1 |
| FR-087 | The system shall support configurable tax/accounting treatment for non-tuition items where required. | P1 |
| FR-088 | The system shall maintain an immutable ledger-style audit history for fee transactions. | P0 |

## 11. HR, Staff, and Payroll

| ID | Requirement | Priority |
|---|---|---|
| FR-089 | The system shall maintain staff records including personal details, qualification, experience, appointment, department, designation, documents, and bank details. | P0 |
| FR-090 | The system shall maintain teacher qualification, training, subject expertise, class allocation, and workload. | P0 |
| FR-091 | The system shall support staff attendance through manual entry, biometric import, or integration. | P1 |
| FR-092 | The system shall support leave types, leave balance, approval workflow, and leave encashment rules. | P1 |
| FR-093 | The system shall support payroll components, deductions, reimbursements, advances, arrears, and payslips. | P1 |
| FR-094 | The system shall support configurable PF, ESI, professional tax, TDS, and other statutory deduction fields. | P1 |
| FR-095 | The system shall support recruitment, applicant tracking, interviews, appointment letters, and onboarding. | P2 |
| FR-096 | The system shall support appraisal, training records, disciplinary records, and exit management. | P2 |

## 12. Communication and Collaboration

| ID | Requirement | Priority |
|---|---|---|
| FR-097 | The system shall send circulars, notices, reminders, alerts, emergency messages, and personal messages. | P0 |
| FR-098 | The system shall support channels such as in-app, email, SMS, push notification, and WhatsApp provider integration. | P0 |
| FR-099 | The system shall maintain templates in English, Hindi, and configurable local languages. | P1 |
| FR-100 | The system shall support audience targeting by class, section, transport route, hostel, house, club, fee status, attendance status, and custom groups. | P0 |
| FR-101 | The system shall track delivery status, read status where available, retries, and failed messages. | P0 |
| FR-102 | The system shall support PTM scheduling, slots, booking, attendance, notes, and follow-up actions. | P1 |
| FR-103 | The system shall provide helpdesk/ticketing for parent complaints, service requests, and internal tasks. | P1 |
| FR-104 | The system shall support consent forms and acknowledgements. | P0 |

## 13. Parent, Student, and Teacher Portals

| ID | Requirement | Priority |
|---|---|---|
| FR-105 | Parent portal shall show student profile, attendance, fees, receipts, circulars, homework, timetable, results, transport, hostel, and certificate requests. | P0 |
| FR-106 | Student portal shall show timetable, homework, assignments, learning resources, library, results, and announcements. | P0 |
| FR-107 | Teacher portal shall support attendance, homework, marks entry, timetable, substitution, lesson plans, student search, and communication. | P0 |
| FR-108 | Portals shall be mobile responsive and usable on low-cost Android phones. | P0 |
| FR-108a | The interface shall adapt at the compact (<600px), medium (>=600px) and expanded (>=840px) width classes, presenting bottom navigation, an icon rail and a labelled sidebar respectively. | P0 |
| FR-108b | Compact navigation shall show up to five top-level destinations directly, and where a user has more, shall show four plus an expandable sheet containing the full menu. | P0 |
| FR-108c | Interactive targets shall be at least 44x44 CSS pixels, and no content shall be obscured by fixed navigation or by device system areas. | P0 |
| FR-108d | No screen shall require horizontal scrolling of the page; wide content such as tables shall scroll within its own container or adopt a card or expandable-row presentation. | P0 |
| FR-108e | Anything a parent, student or teacher does routinely shall be fully usable on a phone. Administrative bulk tasks may be designated desktop-first, and shall be listed explicitly and degrade with a clear message rather than a broken layout. | P0 |
| FR-108f | Screens shall be verified at 360x640, 390x844, 768x1024 and 1280x800 before release. | P1 |
| FR-109 | The system shall support parent access for multiple children in the same account. | P0 |
| FR-110 | The system shall support notification preferences where appropriate. | P1 |
| FR-110a | The system shall return the user's navigation menu from the server after login, derived from that user's effective permissions and the modules the school uses, so that a school-defined role produces a correct menu without a frontend release. | P0 |
| FR-110b | Navigation items shall be identified by stable identifiers that the client resolves to its own routes. The server shall not send URLs, component names, or layout. | P0 |
| FR-110c | Hiding a navigation item shall never be relied on as an access control; every endpoint shall enforce its own permission independently. | P0 |
| FR-110d | The system shall allow a school to override a navigation label, and shall otherwise send translation keys rather than display text. | P1 |
| FR-110e | The client shall refresh its navigation and permissions when the server rejects a request as unauthorised, so a role change mid-session self-corrects. | P1 |
| FR-110f | User interface components shall be built in-house against the product's own design tokens; no third-party visual component library shall be adopted. | P0 |
| FR-110g | Interactive components shall support keyboard operation and screen readers, and shall remain usable on low-cost Android tablets. | P0 |

## 14. Transport

| ID | Requirement | Priority |
|---|---|---|
| FR-111 | The system shall manage vehicles, routes, stops, route timings, drivers, attendants, and student assignments. | P1 |
| FR-112 | The system shall maintain vehicle documents including registration, insurance, fitness, permit, pollution certificate, and expiry reminders. | P1 |
| FR-113 | The system shall support pickup/drop attendance and parent alerts. | P1 |
| FR-114 | The system shall integrate with GPS providers where available. | P2 |
| FR-115 | The system shall calculate transport fees by route, stop, distance band, or custom assignment. | P1 |
| FR-116 | The system shall track fuel, maintenance, incidents, and complaints. | P2 |

## 15. Hostel and Mess

| ID | Requirement | Priority |
|---|---|---|
| FR-117 | The system shall manage hostel buildings, floors, rooms, beds, and student allocation. | P1 |
| FR-118 | The system shall manage wardens, room changes, hostel attendance, night roll call, and leave/gate pass. | P1 |
| FR-119 | The system shall manage visitor entries for hostel students. | P1 |
| FR-120 | The system shall manage mess menu, diet notes, meal attendance, and mess fees. | P2 |
| FR-121 | The system shall record hostel incidents, maintenance, and medical escalation. | P1 |

## 16. Library

| ID | Requirement | Priority |
|---|---|---|
| FR-122 | The system shall manage books, accession numbers, copies, categories, authors, publishers, shelves, and barcode/QR labels. | P1 |
| FR-123 | The system shall manage issue, return, renewal, reservation, loss, damage, and fines. | P1 |
| FR-124 | The system shall provide student/staff library accounts and circulation history. | P1 |
| FR-125 | The system shall provide catalogue search for users. | P1 |
| FR-126 | The system shall support stock verification and write-off. | P2 |

## 17. Inventory, Procurement, and Assets

| ID | Requirement | Priority |
|---|---|---|
| FR-127 | The system shall manage vendors, purchase requisitions, purchase orders, goods receipts, invoices, and payments status. | P1 |
| FR-128 | The system shall manage stock items, issue/return, minimum stock alerts, and location-wise inventory. | P1 |
| FR-129 | The system shall manage fixed assets, asset tags, warranty, AMC, maintenance, and disposal. | P1 |
| FR-130 | The system shall manage lab equipment and consumables. | P1 |
| FR-131 | The system shall support approval workflows for procurement. | P1 |

## 18. Health, Safety, Discipline, and Wellbeing

| ID | Requirement | Priority |
|---|---|---|
| FR-132 | The system shall maintain student health records with restricted permissions. | P1 |
| FR-133 | The system shall record infirmary visits, medication, first aid, parent notification, and follow-up. | P1 |
| FR-134 | The system shall manage counselling referrals, sessions, notes, and confidentiality restrictions. | P1 |
| FR-135 | The system shall record discipline incidents, actions, parent meetings, and resolution. | P1 |
| FR-136 | The system shall support anti-bullying, child safety, and safeguarding incident workflows with escalation and restricted access. | P1 |
| FR-137 | The system shall maintain emergency contacts, evacuation drills, fire drill logs, and safety inspection records. | P1 |
| FR-138 | The system shall manage visitor entry, gate pass, late entry, early exit, and pickup authorization. | P1 |

## 19. Certificates and Documents

| ID | Requirement | Priority |
|---|---|---|
| FR-139 | The system shall generate transfer certificate, bonafide certificate, character certificate, conduct certificate, fee certificate, study certificate, and migration-related letters. | P0 |
| FR-140 | The system shall support configurable templates with merge fields, approval, numbering, digital signature image, and QR verification. | P0 |
| FR-141 | The system shall allow parent/student certificate requests and status tracking. | P1 |
| FR-142 | The system shall maintain generated document history and reprint controls. | P0 |
| FR-143 | The system shall support document expiry reminders for school, staff, student, vehicle, safety, and compliance records. | P0 |

## 20. Public Website and Mandatory Disclosure

| ID | Requirement | Priority |
|---|---|---|
| FR-144 | The system shall provide a public website or public pages for school profile, contact, notices, achievements, gallery, admissions, and policies. | P1 |
| FR-145 | The system shall support board-required mandatory public disclosure sections, especially for CBSE schools. | P0 |
| FR-146 | The system shall publish selected certificates, affiliation data, staff details, fee structure, academic calendar, infrastructure details, and transfer certificate data where required. | P0 |
| FR-147 | The system shall separate public content permissions from internal ERP permissions. | P0 |
| FR-148 | The system shall track publish history and approval for public content. | P1 |

## 21. Compliance and Government/Board Reporting

| ID | Requirement | Priority |
|---|---|---|
| FR-149 | The system shall maintain data required for UDISE+ style school, teacher, student, infrastructure, and facility reporting. | P0 |
| FR-150 | The system shall maintain fields required for board affiliation and annual returns where configured. | P0 |
| FR-151 | The system shall support APAAR consent tracking and APAAR ID storage where legally permitted. | P0 |
| FR-152 | The system shall support exportable data templates for UDISE, board portals, RTE reports, and state-specific reports. | P1 |
| FR-153 | The system shall provide compliance completeness dashboards. | P1 |
| FR-154 | The system shall track certificate expiry and renewal tasks. | P0 |

## 22. Reporting and Analytics

| ID | Requirement | Priority |
|---|---|---|
| FR-155 | The system shall provide dashboards for admissions, attendance, fees, academics, HR, transport, hostel, library, and compliance. | P0 |
| FR-156 | The system shall provide export to PDF, CSV, XLSX, and printable formats. | P0 |
| FR-157 | The system shall provide filters by session, campus, class, section, category, gender, board, route, hostel, and fee status. | P0 |
| FR-158 | The system shall provide scheduled email reports to authorized users. | P1 |
| FR-159 | The system shall provide custom report builder for admin users. | P2 |
| FR-160 | The system shall maintain report access logs for sensitive reports. | P0 |

## 23. Alumni

| ID | Requirement | Priority |
|---|---|---|
| FR-161 | The system shall convert graduated/transferred students to alumni records. | P1 |
| FR-162 | The system shall track batch, contact, higher education, occupation, achievements, and communication consent. | P2 |
| FR-163 | The system shall support alumni events and donations only if enabled. | P2 |

## 24. Administration and Configuration

| ID | Requirement | Priority |
|---|---|---|
| FR-164 | The system shall provide configurable numbering series for admission numbers, receipts, certificates, gate passes, purchase orders, and invoices. | P0 |
| FR-165 | The system shall support configurable approval workflows for fees, concessions, certificates, leave, procurement, admissions, and public publishing. | P1 |
| FR-166 | The system shall support feature flags by school/campus. | P1 |
| FR-167 | The system shall support data archival by session. | P1 |
| FR-168 | The system shall support admin-defined custom fields with validation and reportability. | P1 |

