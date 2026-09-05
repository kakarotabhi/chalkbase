# Domain Rules: Indian School ERP

## Core Rule

Indian school rules differ by board, state, school type, session, and local policy. Agents must not hardcode rules that can vary. Use configuration, templates, and compliance profiles.

## Academic Sessions

Requirements:

- Academic sessions should support labels like `2026-27`.
- Session start/end dates must be configurable.
- Most academic records must reference an academic session.
- Cross-session history must be preserved.
- Promotion creates a new enrollment record for the next session.

Do not overwrite previous session enrollment when promoting students.

## Classes, Sections, Streams, and Subjects

Support:

- Pre-primary naming such as Nursery, LKG, UKG.
- Classes 1 to 12.
- Sections.
- Streams for senior secondary classes.
- Subject groups and electives.
- Second and third language choices.
- Houses, clubs, and activities.

Rules:

- Subject choices must be validated against configured class/stream/board rules.
- Roll numbers are session and class-section scoped.
- Class teacher and subject teacher assignments must be session-aware.

## Student and Guardian Records

Rules:

- Student and guardian are separate entities.
- One guardian can be linked to multiple students.
- One student can have multiple guardians.
- Parent login should support multiple children.
- Emergency contact and pickup authorization must be explicit.
- Sensitive category and health fields require restricted permissions.

Student status should preserve history:

- Applicant.
- Active.
- Inactive.
- Long absent.
- Withdrawn.
- Transferred.
- Graduated.
- Alumni.

## Admissions

Admissions should support:

- Enquiry.
- Application.
- Document verification.
- Interaction/test stage if legally and institutionally appropriate.
- Selection/waitlist/rejection.
- Offer.
- Fee collection.
- Admission confirmation.
- Student record creation.

Rules:

- Admission number generation must be configurable.
- Duplicate detection should not rely on one field only.
- RTE/EWS/disadvantaged group fields must be configurable by state/school.
- Application data must convert into student/guardian records without re-entry.
- Rejected/withdrawn applications must remain auditable.

## RTE, EWS, Category, and Inclusion

Support:

- RTE admission category.
- EWS/BPL or state-specific economic categories.
- Caste/community/category fields where required.
- Minority status where required.
- Disability/CWSN fields.
- Income certificate and category documents.
- Accommodation/support notes.

Rules:

- Treat these fields as sensitive or restricted.
- Do not display category/income/disability fields to users without a clear need.
- Fee handling for RTE/EWS students must be configurable.
- Reimbursement tracking should be separate from normal parent fee dues.

## Attendance

Support:

- Daily attendance.
- Period-wise attendance where enabled.
- Late, half-day, leave, medical, excused, activity duty, holiday.
- Leave applications.
- Parent absence alerts.
- Attendance locks.
- Corrections with reason and approval.

Rules:

- Board attendance thresholds must be configurable.
- Working days must come from the academic calendar.
- Attendance percentage calculation rules must be versioned/configurable.
- Locked attendance cannot be overwritten directly.

## Fees

Support:

- Fee heads.
- Fee structures.
- Installments.
- Due dates.
- Late fee/fine rules.
- Concessions.
- Scholarships.
- Sibling discounts.
- Staff-child concessions.
- RTE/EWS fee treatment.
- Transport and hostel fee linkage.
- Online and offline payments.
- Receipts.
- Refunds.
- Adjustments.
- Write-offs.

Rules:

- Use ledger-style accounting.
- Use `numeric`, not floating point.
- Do not hard-delete fee records.
- Do not update posted receipt amounts.
- Use reversal/void workflows.
- Every concession must have approval where configured.
- Every cancellation/refund/write-off needs reason and audit trail.
- Gateway success must be verified server-side.

## Exams and Report Cards

Support:

- Terms.
- Unit tests.
- Periodic tests.
- Internal assessment.
- Practical/project/viva.
- Co-scholastic grading.
- Skill/competency-based remarks.
- Grade scales.
- Weightage.
- Report card templates.
- Result publishing.

Rules:

- Exam patterns must be configurable by class, board, and session.
- Marks cannot exceed configured maximum.
- Absent/exempted/medical/withheld states must be distinct from zero.
- Published report cards must be snapshotted.
- Template changes must not alter old published report cards.

## Timetable

Support:

- Class timetable.
- Teacher timetable.
- Room/lab timetable.
- Breaks.
- Substitutions.
- Special days.

Rules:

- Detect teacher conflicts.
- Detect room/lab conflicts.
- Preserve published timetable versions.
- Substitution should notify affected teachers/classes where communication is enabled.

## Communication

Support:

- Circulars.
- Notices.
- Homework.
- Fee reminders.
- Attendance alerts.
- Emergency alerts.
- Consent forms.
- PTM messages.

Rules:

- Audience targeting must be explicit.
- Message templates should support English, Hindi, and local languages.
- Delivery status should be stored.
- Sensitive messages should not expose restricted data in SMS previews.
- Consent/acknowledgement records must be auditable.

## Certificates and Official Documents

Support:

- Transfer certificate.
- Bonafide certificate.
- Character certificate.
- Study certificate.
- Fee certificate.
- ID card.
- Admit card/hall ticket where applicable.
- Report card.

Rules:

- Numbering series must be configurable.
- Generated documents must be snapshotted.
- Reprints must be logged.
- Public verification must use opaque QR/token, not internal IDs.
- Templates must not directly expose unauthorized fields.

## Compliance

Support compliance data for:

- CBSE-style disclosure.
- UDISE-style school/student/teacher/facility data.
- APAAR consent and identifier tracking.
- RTE/state reporting.
- Safety certificate tracking.
- Staff qualification/training records.
- Infrastructure records.

Rules:

- Store data once and map to reports.
- Snapshot generated compliance exports.
- Track submission status and acknowledgement.
- Keep certificate expiry reminders.
- Do not direct-submit unless official API access is confirmed.

## Transport

Support:

- Vehicles.
- Routes.
- Stops.
- Drivers.
- Attendants.
- Pickup/drop allocation.
- Vehicle documents.
- Driver documents.
- GPS provider integration where enabled.
- Transport fees.

Rules:

- Vehicle document expiry must generate reminders.
- Route allocation must preserve history.
- Parent pickup/drop alerts must not leak other students' data.
- GPS provider data must be treated as sensitive location data.

## Hostel

Support:

- Hostels.
- Rooms and beds.
- Allocation.
- Warden assignment.
- Night attendance.
- Leave/gate pass.
- Visitors.
- Mess.
- Incidents.

Rules:

- A bed cannot have two active allocations.
- Gate pass requires authorization.
- Hostel incidents may be sensitive.
- Medical escalation must preserve audit history.

## Library

Support:

- Book titles.
- Copies.
- Accession numbers.
- Issue/return.
- Renewal.
- Reservation.
- Lost/damaged books.
- Fines.

Rules:

- Accession number must be unique.
- Book copy state must be consistent.
- Library fines should integrate with fees only through a clear posting workflow.

## HR and Payroll

Support:

- Staff records.
- Qualifications.
- Experience.
- Appointments.
- Documents.
- Attendance.
- Leave.
- Payroll.
- Payslips.
- Statutory deduction fields where applicable.

Rules:

- Payroll rules must be configurable and accountant-reviewed.
- Bank and salary fields are restricted.
- Payroll runs must be locked after approval.
- Corrections should use adjustment entries.

## Public Disclosure

Support:

- School profile.
- Affiliation/recognition details.
- Documents and certificates.
- Staff details.
- Fee structure.
- Academic calendar.
- Infrastructure.
- Transfer certificate verification where required.

Rules:

- Public data must come from approved records.
- Publish actions must be audited.
- Expired/missing certificates should be flagged.
- Public pages must not expose private student data.

