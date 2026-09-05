# Data Model and Integrations

## Data Architecture Principles

The database should be designed as a normalized PostgreSQL model with clear module ownership. Avoid storing critical records only as JSON. JSONB can be used for configurable custom fields, imported payloads, and integration metadata, but core business data should remain relational and queryable.

Core principles:

- Every business table should include school/tenant context.
- Most academic records should include academic session context.
- Sensitive records should have field-level classification.
- Records should use stable internal UUIDs plus human-readable numbers.
- Financial records should be append-only or reversal-based.
- Auditable workflows should store status history, not only current status.
- Reports should be generated from source data, with snapshots where legal/audit needs require.

## Identifier Strategy

Use:

- Internal UUID primary keys.
- Human-readable school-facing numbers such as admission number, receipt number, certificate number, employee code, accession number, vehicle number, and application number.
- External identifiers such as UDISE code, PEN, APAAR ID, board registration number, payment gateway transaction ID, GPS device ID, and accounting voucher ID.

Requirements:

- Human-readable numbers must be generated from configurable series.
- External identifiers must be nullable unless mandatory for a specific configured compliance profile.
- Sensitive identifiers must be masked in UI, reports, logs, and exports unless explicitly permitted.

## Tenant and Organization Entities

Key entities:

- Tenant.
- School group.
- School.
- Campus.
- Academic session.
- Board/curriculum.
- Department.
- Building.
- Room.
- Facility.
- Bank account.
- Compliance document.
- Public disclosure item.

Relationships:

- A tenant may own one or more school groups.
- A school group may own one or more schools.
- A school may have one or more campuses.
- A school has many academic sessions.
- Most operational data belongs to one school and one academic session.

## Identity and Access Entities

Key entities:

- User account.
- Role.
- Permission.
- User-role assignment.
- Staff-user link.
- Guardian-user link.
- Student-user link.
- Login session.
- MFA method.
- Password reset token.
- Audit event.
- Data export event.

Access rules:

- A parent can access only linked children.
- A teacher can access assigned classes/subjects/students.
- A class teacher can access additional class-level data.
- An accountant can access fee records but not private health/counselling data.
- Health and safeguarding records require explicit restricted roles.
- Public website users cannot access internal ERP APIs.

## Student Domain Entities

Key entities:

- Applicant.
- Admission application.
- Admission stage history.
- Student.
- Guardian.
- Student-guardian relationship.
- Sibling relationship.
- Student academic enrollment.
- Class.
- Section.
- Stream.
- Subject.
- Subject group.
- Student subject enrollment.
- House.
- Club.
- Student document.
- Student identifier.
- Student medical profile.
- Student status history.
- Promotion record.
- Transfer certificate.
- Alumni profile.

Important constraints:

- A student can have multiple guardians.
- A guardian can be linked to multiple students.
- A student can have one active enrollment per school/session.
- Subject enrollment must match allowed class/stream/board rules.
- Roll number uniqueness should be per class-section-session.
- Admission number uniqueness should be configurable per school or school group.

## Attendance Entities

Key entities:

- Attendance calendar.
- Attendance session.
- Student daily attendance.
- Student period attendance.
- Staff attendance.
- Leave application.
- Attendance correction request.
- Short attendance alert.
- Attendance lock.

Important constraints:

- Attendance cannot be overwritten after lock without correction workflow.
- Attendance status definitions should be configurable.
- Reports must compute attendance percentage using board/school configured rules.

## Academic and Timetable Entities

Key entities:

- Academic term.
- Syllabus unit.
- Lesson plan.
- Lesson plan review.
- Homework.
- Assignment.
- Assignment submission.
- Teaching allocation.
- Timetable.
- Timetable slot.
- Room allocation.
- Substitution.
- Academic calendar event.

Important constraints:

- Teacher, room, class, and lab conflicts should be prevented.
- Published timetable should be versioned.
- Homework visibility should respect class, section, subject, and date.

## Exam Entities

Key entities:

- Exam group.
- Exam.
- Assessment component.
- Exam subject.
- Exam schedule.
- Grade scale.
- Mark entry.
- Mark entry lock.
- Result calculation rule.
- Report card template.
- Report card snapshot.
- Student exam result.
- Board registration record.

Important constraints:

- Marks entry should support draft, submitted, verified, locked, and published states.
- Published report cards should be snapshotted.
- Result formulas should be configurable and versioned.
- Report card template changes must not alter old published results.

## Fee and Finance Entities

Key entities:

- Fee head.
- Fee structure.
- Fee installment.
- Fee assignment.
- Concession type.
- Concession approval.
- Fee demand.
- Fee transaction.
- Receipt.
- Receipt line.
- Payment mode.
- Payment gateway order.
- Payment gateway settlement.
- Refund.
- Adjustment.
- Fine/late fee rule.
- Accounting export batch.

Important constraints:

- Receipt numbers must be unique within configured series.
- Payments must not be hard-deleted.
- Cancellations must be recorded as void/reversal entries.
- Online payment webhook handling must be idempotent.
- Financial exports must be reproducible from recorded data.

## HR and Payroll Entities

Key entities:

- Staff.
- Staff document.
- Qualification.
- Experience.
- Appointment.
- Department.
- Designation.
- Staff attendance.
- Leave type.
- Leave balance.
- Payroll structure.
- Payroll component.
- Payroll run.
- Payslip.
- Payroll adjustment.
- Training record.
- Appraisal.
- Exit record.

Important constraints:

- Payroll runs should be locked after approval.
- Payroll corrections should use adjustment entries.
- Staff document expiry should trigger alerts.

## Transport Entities

Key entities:

- Vehicle.
- Vehicle document.
- Driver.
- Attendant.
- Route.
- Route stop.
- Student transport assignment.
- Transport fee assignment.
- Trip.
- Pickup/drop attendance.
- GPS device.
- Vehicle maintenance.
- Fuel log.
- Transport incident.

Important constraints:

- Vehicle and driver document expiry must be tracked.
- Student transport assignment should be session-aware.
- Route changes should preserve history.

## Hostel Entities

Key entities:

- Hostel.
- Hostel building.
- Hostel floor.
- Room.
- Bed.
- Bed allocation.
- Warden assignment.
- Hostel attendance.
- Hostel leave.
- Gate pass.
- Visitor.
- Mess menu.
- Meal attendance.
- Hostel incident.

Important constraints:

- A bed can have only one active allocation at a time.
- Hostel leave and gate pass should require approval.
- Visitor access should be logged.

## Library Entities

Key entities:

- Library branch.
- Book title.
- Book copy.
- Accession record.
- Author.
- Publisher.
- Category.
- Shelf.
- Member.
- Issue.
- Return.
- Renewal.
- Reservation.
- Fine.
- Stock verification.

Important constraints:

- Accession number must be unique.
- Book copy status should reflect circulation state.
- Fines should integrate with fee collection where enabled.

## Inventory and Asset Entities

Key entities:

- Vendor.
- Item category.
- Stock item.
- Stock location.
- Stock transaction.
- Purchase requisition.
- Purchase order.
- Goods receipt.
- Vendor invoice.
- Asset.
- Asset maintenance.
- Asset issue.
- Disposal record.

Important constraints:

- Stock changes should be transaction-based.
- Procurement approvals should be configurable.
- Asset maintenance and warranty dates should trigger alerts.

## Health, Safety, and Wellbeing Entities

Key entities:

- Health profile.
- Allergy.
- Medication.
- Infirmary visit.
- Counselling referral.
- Counselling session.
- Discipline incident.
- Safeguarding incident.
- Visitor entry.
- Student gate pass.
- Emergency contact.
- Drill record.
- Safety inspection.
- Incident attachment.

Important constraints:

- Health, counselling, and safeguarding records must have stricter permissions than normal student data.
- Sensitive incident exports must be logged.
- Parent notification must be recorded.

## Document and Template Entities

Key entities:

- Document template.
- Template field.
- Generated document.
- Certificate request.
- Document approval.
- QR verification token.
- File object.
- File access log.

Important constraints:

- Generated official documents should be snapshotted.
- Verification tokens should not expose internal IDs.
- Document files should be virus-scanned if an upload scanning service is configured.

## Integration Requirements

### Payment Gateway

Capabilities:

- Create payment order.
- Verify payment.
- Receive webhook.
- Handle failed, pending, successful, refunded, and reversed statuses.
- Reconcile settlement.
- Store gateway response safely.
- Generate receipt only after verified success or authorized offline approval.

Possible providers:

- Razorpay.
- Cashfree.
- PayU.
- PhonePe.
- Other UPI/card/netbanking providers.

### SMS, Email, Push, and WhatsApp

Capabilities:

- Provider abstraction.
- Template management.
- Delivery status.
- Retry.
- Cost tracking.
- Consent and opt-out where required.
- Message logs.

Possible providers:

- SMS gateway.
- WhatsApp Business API provider.
- SMTP or transactional email provider.
- Firebase Cloud Messaging or web push.

### UDISE, APAAR, Board, and Government Portals

Capabilities:

- Maintain export-ready data.
- Field mapping.
- Validation.
- Submission tracking.
- Acknowledgement upload.
- Snapshot by session.
- Direct API integration only when official API access and terms are available.

### Accounting

Capabilities:

- Export fee receipts, refunds, concessions, bank collections, and expense vouchers.
- Tally-compatible export where required.
- Configurable chart of accounts mapping.
- Reconciliation report.

### Biometric, RFID, QR, and GPS

Capabilities:

- Device registry.
- Import or webhook handling.
- Idempotency.
- Manual correction workflow.
- Device health status.
- Provider-specific adapter layer.

### SSO and Identity

Capabilities:

- Local username/password login.
- Optional Google Workspace or Microsoft Entra ID integration for staff/students.
- Optional parent OTP login.
- Session management.
- Account linking.

## Reporting Data Model

For early releases, reports can query transactional tables with optimized indexes and materialized views. For larger deployments, add:

- Reporting schema.
- Materialized views.
- Scheduled aggregation jobs.
- Snapshot tables for compliance reports.
- Export history.

Suggested dashboards:

- Admissions funnel.
- Enrollment and strength.
- Daily attendance.
- Fee collection and dues.
- Academic performance.
- Staff attendance.
- Transport usage.
- Hostel occupancy.
- Library circulation.
- Compliance readiness.
- Certificate expiry.
- Communication delivery.

## Data Migration

The product must support migration from spreadsheets and old ERP systems.

Requirements:

- Provide import templates for students, guardians, staff, fees, opening balances, books, vehicles, and hostel allocation.
- Validate imported data before commit.
- Show row-level errors.
- Support dry run.
- Support duplicate detection.
- Store import batch history.
- Allow rollback before approval.
- Require explicit approval before imported fee ledgers become active.

## Data Quality

The system should enforce:

- Required fields by board/state/profile.
- Pincode format.
- Mobile number format.
- Email format.
- Date of birth and class age validation where configured.
- Duplicate guardian detection.
- Unique admission number and receipt number.
- Fee balance reconciliation.
- Missing document alerts.
- Inconsistent category/fee assignment alerts.

