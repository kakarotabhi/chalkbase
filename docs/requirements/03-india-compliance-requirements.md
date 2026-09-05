# India Compliance Requirements

## Compliance Disclaimer

Indian school compliance depends on board, state, local body, school type, recognition conditions, trust/society structure, transport operations, hostel operations, labour laws, and fee regulation rules. This document captures product requirements that should make compliance easier, but the final workflows must be validated for the first target state and board.

## Official Reference Areas

The system should be designed around these official sources and equivalent state/board updates:

- CBSE resources: <https://www.cbse.gov.in/>
- CBSE academic resources: <https://cbseacademic.nic.in/>
- UDISE+: <https://udiseplus.gov.in/>
- APAAR: <https://apaar.education.gov.in/>
- Ministry of Education NEP page: <https://www.education.gov.in/nep/about-nep>
- NCERT NCF resources: <https://ncf.ncert.gov.in/>
- India Code for central legislation: <https://www.indiacode.nic.in/>
- MeitY DPDP resources: <https://www.meity.gov.in/>
- NDMA school safety resources: <https://ndma.gov.in/>
- NCPCR resources: <https://ncpcr.gov.in/>

## Compliance Configuration Model

The product must not hardcode one board or one state. It should define compliance profiles.

Examples:

- CBSE day school.
- CBSE boarding school.
- State board school in a selected state.
- CISCE school.
- Pre-primary only school.
- School with transport.
- School with hostel.
- School with RTE/EWS category admissions.

Each profile should enable:

- Required data fields.
- Required documents.
- Required reports.
- Submission calendar.
- Validation rules.
- Disclosure requirements.
- Retention rules.
- Approval workflow.

## CBSE-Oriented Requirements

The product should support CBSE-affiliated schools with:

- School profile, affiliation number, school code, UDISE code, principal details, manager details, trust/society details.
- Mandatory public disclosure sections and attachments.
- Staff and teacher qualification records.
- Teacher training and workshop records.
- Student strength by class, section, gender, and category.
- Infrastructure records including classrooms, labs, library, playground, sanitation, drinking water, ramps, and safety.
- Safety certificates including fire safety, building safety, water/health/sanitation certificate, land certificate, recognition/NOC where applicable, and their expiry reminders.
- Fee structure and annual academic calendar publication.
- Transfer certificate issue and public verification support where applicable.
- Examination records for Classes 9 to 12, including subject combinations, LOC-like data, attendance eligibility, internal assessment, practical/project marks, and result archives.
- Board circular tracking and internal task assignment.
- Compliance dashboard for data missing against CBSE profile.

Functional requirements:

- The system shall allow school admins to maintain all data commonly requested in CBSE affiliation, annual return, school website disclosure, and inspection workflows.
- The system shall generate CBSE-style public disclosure pages from internal approved data.
- The system shall keep a publish log for all public disclosure changes.
- The system shall maintain evidence files for inspections and renewals.

## UDISE+ Requirements

UDISE+ is a key Indian school data system covering school, teacher, student, infrastructure, and facility data. The product should maintain UDISE-ready fields even if direct API submission is not available.

The system should support:

- UDISE school code.
- School category, management type, location, area, medium, board, school type, and recognition details.
- Student enrollment by class, gender, category, minority status, CWSN/disability, age, medium, and other configured categories.
- Teacher and staff counts, appointment type, qualification, training, subjects, and classes taught.
- Facilities and infrastructure information.
- Digital device, internet, electricity, drinking water, toilet, ramp, library, lab, playground, and safety data.
- Student Permanent Education Number or equivalent UDISE-linked identifier where applicable.
- Export templates for annual reporting.

Functional requirements:

- The system shall keep a mapping layer between internal fields and UDISE fields.
- The system shall generate UDISE completeness checks before export.
- The system shall track report generation date, data owner, approver, and submission acknowledgement.
- The system shall support historical UDISE snapshots by academic session.

## APAAR ID Requirements

APAAR is positioned as a student academic identity linked with the broader digital education ecosystem. The system should support APAAR without forcing it where consent, eligibility, or operational readiness is not present.

The system should support:

- APAAR consent form capture.
- Consent status: not requested, requested, consented, refused, withdrawn, expired, correction required.
- Parent/guardian consent evidence.
- Student demographic data used for matching or correction.
- APAAR ID field with restricted access.
- APAAR verification status.
- History of consent and corrections.

Functional requirements:

- The system shall not make APAAR mandatory for all schools by default.
- The system shall allow schools to collect, store, and audit consent before processing APAAR-related data.
- The system shall hide sensitive identity fields from users who do not need them.
- The system shall support export/import or integration when official access details are available.

## RTE and Inclusion Requirements

The Right of Children to Free and Compulsory Education Act, 2009 and state rules affect admission, attendance, school management, and inclusion workflows.

The system should support:

- RTE/EWS/disadvantaged group category tagging.
- Admission category, neighbourhood, income certificate, caste/category certificate, disability certificate, and other state-defined documents.
- No capitation/screening compliance notes where applicable.
- Age proof records.
- Student support plans.
- CWSN accommodation records.
- School Management Committee records where applicable.
- School development plan records where applicable.
- Child safety and no corporal punishment incident policies.

Functional requirements:

- The system shall support category-wise admissions and reports without exposing sensitive category data to unnecessary users.
- The system shall support state-specific RTE portal export fields.
- The system shall maintain document verification status and expiry where applicable.
- The system shall allow separate treatment of RTE/EWS fee demand, reimbursement tracking, and reporting.

## DPDP and Privacy Requirements

The Digital Personal Data Protection Act, 2023 requires strong privacy discipline. Schools handle personal data of children, parents, staff, and vendors, so the system must be privacy-first.

The system should support:

- Clear consent records for optional processing.
- Parent/guardian consent for child data where required.
- Data minimization by feature.
- Purpose tags for sensitive data fields.
- Role-based and attribute-based access.
- Masking of sensitive identifiers.
- Data access logs.
- Data correction workflow.
- Data export for authorized requests.
- Retention and deletion workflow.
- Breach incident tracking.
- Processor/sub-processor records for SMS, WhatsApp, email, payment, cloud, GPS, biometric, and analytics providers.

Functional requirements:

- The system shall classify fields as public, internal, confidential, sensitive, or restricted.
- The system shall mask identity, health, disability, caste/category, income, payment, and bank fields by default.
- The system shall record why a sensitive field was accessed where required by policy.
- The system shall support consent withdrawal and downstream processing review.
- The system shall log exports containing personal data.
- The system shall support school-defined retention policies by record type.

## Child Safety, Wellbeing, and School Safety

The product should support Indian child safety expectations reflected in board circulars, NCPCR resources, NDMA school safety guidance, and school policies.

The system should support:

- Visitor entry and exit logs.
- Student gate pass and early pickup authorization.
- Emergency contact and pickup authorization.
- Incident reporting.
- Bullying, harassment, abuse, and safeguarding escalation workflows.
- Counselling referral and confidential notes.
- Health room records.
- Fire drill, evacuation drill, and disaster preparedness records.
- Safety certificate expiry reminders.
- Transport incident records.
- Hostel incident records.

Functional requirements:

- The system shall allow confidential incident types visible only to authorized safeguarding roles.
- The system shall preserve incident audit history.
- The system shall support emergency broadcast messages.
- The system shall generate drill and inspection logs.
- The system shall support parent notification with acknowledgement.

## Attendance and Board Examination Requirements

Many Indian boards enforce minimum attendance rules and documentation for examination eligibility. CBSE commonly requires attendance tracking and shortage/condonation workflows for board classes.

The system should support:

- Attendance percentage by student, subject, class, and session.
- Board-class attendance eligibility.
- Medical and authorized leave documents.
- Shortage alerts.
- Condonation request record where applicable.
- Parent notification history.
- Principal approval.

Functional requirements:

- The system shall allow board-specific attendance thresholds to be configured.
- The system shall generate shortage lists and evidence packs.
- The system shall lock published attendance summaries after approval.

## Public Disclosure Requirements

Indian schools, especially CBSE-affiliated schools, often need to publish official information on their website.

The product should provide a disclosure content model for:

- General information.
- Documents and certificates.
- Result and academic information where required.
- Staff details.
- School infrastructure.
- Fee structure.
- Annual academic calendar.
- School management committee or parent-teacher association data where applicable.
- Transfer certificate links or verification where applicable.

Functional requirements:

- The system shall distinguish internal records from public records.
- The system shall require approval before publishing disclosure data.
- The system shall keep old versions for audit.
- The system shall warn when disclosure documents are missing or expired.

## Fee Regulation and Accounting Compliance

School fee rules vary significantly by state. The system must be configurable enough to support state-specific controls.

The system should support:

- Fee approval history.
- Fee head categorization.
- Fee increase history.
- Installment schedules.
- Concession approval.
- Refund policy.
- Late fee/fine policy.
- Receipt series and cancellation controls.
- Audit trail for every fee transaction.
- Configurable tax/accounting treatment for optional items such as books, uniforms, transport, hostel, activities, or taxable services where applicable.
- RTE/EWS reimbursement records.

Functional requirements:

- The system shall not allow hard deletion of financial transactions.
- The system shall support void/reversal entries with reason and approval.
- The system shall export data for accountant review.
- The system shall provide fee reports by category, class, section, head, date, payment mode, and concession type.

## HR and Labour Compliance

The system should support records needed for school staff administration and payroll.

The system should maintain:

- Appointment letter.
- Contract type.
- Joining date.
- Qualification certificates.
- Experience certificates.
- Background verification records where policy requires.
- Leave records.
- Attendance records.
- Salary structure.
- Bank details.
- PF, ESI, professional tax, TDS, PAN, and other payroll fields where applicable.
- Payslip and Form 16 support where implemented.

Functional requirements:

- Payroll rules must be configurable and reviewed by the school's accountant.
- Sensitive payroll fields must be restricted.
- Payroll changes must require audit history.

## Transport Compliance

Transport rules vary by state and local transport authority. The system should support compliance records without assuming one state's rule set.

The system should track:

- Vehicle registration.
- Fitness certificate.
- Insurance.
- Permit.
- Pollution certificate.
- Driver license.
- Driver police verification where school policy/state rule requires.
- Attendant/conductor details.
- Route chart.
- Student pickup/drop authorization.
- Emergency contacts.
- GPS provider data where integrated.
- Incident logs.

Functional requirements:

- The system shall trigger expiry alerts for vehicle and driver documents.
- The system shall maintain route-wise student allocation history.
- The system shall maintain incident and parent notification logs.

## Record Retention

The product should allow configurable retention policies. Suggested categories:

- Student permanent records.
- Admission applications.
- Fee receipts and ledgers.
- Exam marks and report cards.
- Attendance registers.
- Staff service records.
- Payroll records.
- Medical records.
- Consent records.
- Incident records.
- Visitor logs.
- Transport logs.
- Audit logs.
- Public disclosure snapshots.

Functional requirements:

- Retention policy must be configurable by school and jurisdiction.
- Deletion must be soft-delete or archive-first for regulated records.
- Exports and deleted records must be audited.

## Compliance Dashboard

The product should provide a dashboard showing:

- Missing mandatory school profile fields.
- Missing student fields for UDISE/board export.
- Missing staff qualification/training records.
- Expiring certificates.
- Pending consent forms.
- Attendance shortage.
- Missing exam marks.
- Pending public disclosure approvals.
- Pending RTE/EWS document verification.
- Pending transport/hostel safety records.

