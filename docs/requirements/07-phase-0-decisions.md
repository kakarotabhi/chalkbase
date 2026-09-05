# Phase 0 decisions

Every deliverable of [Phase 0](06-roadmap-and-mvp.md#phase-0-discovery-and-foundation), answered.

- Decided: 2026-09-05
- Decider: Raja

Phase 0 exists to remove ambiguity before the large modules are written. These thirteen answers are
that removal. Each one records the reasoning, because the reasoning is what tells a future reader
whether the decision still applies when the circumstances change.

The four decisions that are structural rather than commercial were promoted to ADRs, since those are
the ones somebody will eventually argue with: [ADR-0012](../architecture/adr/0012-fee-ledger-model.md),
[ADR-0013](../architecture/adr/0013-external-provider-ports.md),
[ADR-0014](../architecture/adr/0014-data-classification.md),
[ADR-0015](../architecture/adr/0015-deployment-baseline.md).

## At a glance

| # | Deliverable | Decision |
|---|---|---|
| 1 | First target board | CBSE only |
| 2 | First target state | Delhi NCR |
| 3 | First target school type | K-12 day school, single campus |
| 4 | MVP module list | The full MVP as written in the roadmap |
| 5 | Admission workflow | Simple linear pipeline, no points engine |
| 6 | Fee heads and accounting | Ledger-style: immutable charges, payments, adjustments |
| 7 | Exam and report card format | Template engine with CBSE defaults per stage |
| 8 | Attendance rules | Daily by default, period-wise available, lock plus correction request |
| 9 | Certificate templates | Six templates, HTML to PDF, approval and QR verification |
| 10 | Payment gateway | None in v1 — offline collection only, behind a gateway port |
| 11 | Communication providers | Email and web push in v1, behind a channel port |
| 12 | Deployment | Hostinger KVM 2 in Mumbai, Coolify, everything on one box |
| 13 | Data classification | Four tiers, enforced in code rather than in prose |

Sample data and the convention outputs are in [Phase 0 output](#phase-0-output) below.

## 1. First target board — CBSE

CBSE is roughly 30,000 affiliated schools under one national ruleset. That matters more than market
size: the assessment scheme, the report card structure, the mandatory public disclosure sections and
the affiliation data requirements are uniform and documented, so Phase 3 has one specification to
build against instead of an argument to have per customer.

State boards were rejected as the first target because their exam, fee and disclosure rules are
state-specific and change, giving no reuse across a border. CISCE is a credible niche but a small
first market. Building CBSE and a state board simultaneously was rejected outright — it doubles the
compliance surface before there is a single paying school, which is the first entry in the roadmap's
own risk table.

**This does not mean hardcoding CBSE.** [ADR-0006](../architecture/adr/0006-configurability-model.md)
already requires compliance profiles; CBSE is the first profile shipped, not the only shape the code
can take.

## 2. First target state — Delhi NCR

Delhi is the most demanding of the plausible options and that is the point. The Directorate of
Education requires approval for fee revision, enforces public disclosure actively, and runs the EWS
and disadvantaged-group admission allotment centrally. A product that satisfies Delhi satisfies most
states; the reverse is not true.

It is also the highest willingness to pay, which matters for a product that has to fund its own
development.

What Delhi specifically pulls into the model:

- The filed fee structure has to be reproducible and auditable — see
  [ADR-0012](../architecture/adr/0012-fee-ledger-model.md).
- Development Fee is capped as a proportion of tuition, so fee heads need a category that can carry
  a cap rule rather than being free-text labels.
- EWS and DG seats are allotted by the DoE, not by the school. The system **records and reconciles**
  an allotment; it never computes a government lottery.

## 3. First target school type — K-12 day school, single campus

Widest slice of the CBSE market, and it exercises every core module: pre-primary through class 12,
including the board-exam classes 9 to 12 where subject combinations and internal assessment are
real. Hostel, transport and multi-campus stay as Phase 4 work; they remain
schema-compatible under [ADR-0011](../architecture/adr/0011-schema-per-tenant.md) without being
built, because a second campus is a second schema, not a redesign.

## 4. MVP module list — the full MVP as written

The roadmap's [MVP recommendation](06-roadmap-and-mvp.md#mvp-recommendation) ships as written:
school setup, academic session, roles and permissions, students and guardians, admissions,
attendance, fees with receipts and dues, communication, teacher portal, parent portal, basic exams
and report cards, certificates, document storage, audit logs, basic compliance fields, backups and
Coolify deployment.

Payroll, hostel, inventory, GPS, biometrics and the custom report builder stay out.

This is the larger of the two options considered and it accepts the roadmap's own 9-to-18-month
estimate. The trimmed alternative — deferring exams, report cards, certificates and the admissions
portal — would have reached revenue sooner but would have shipped a system a Delhi CBSE school
cannot run a full academic year on.

**The mitigation for the added scope is sequencing, not descoping.** Report cards are the module the
risk table calls out as most commonly underestimated, so real report card samples from the pilot
school are collected during Phase 1, long before Phase 3 starts building against them.

## 5. Admission workflow — simple linear pipeline

```
Enquiry → Application → Document verification → Screening outcome → Approval
       → Admission fee → Student record created
```

Every stage transition is recorded with who, when and why. The screening outcome is data an admin
records, not a score the system computes.

**No points engine.** Delhi's entry-class points criteria (neighbourhood distance, sibling, alumni,
first-born, girl child) were considered and rejected for v1: the point table changes by circular,
varies per school, and applies only to Nursery and KG. Schools will run their points sheet outside
the system and enter the result. If the pilot school makes this painful enough to matter, it becomes
a [Tier 3 named strategy](../architecture/adr/0006-configurability-model.md) slotted into the
screening stage — the pipeline is shaped so that is an addition, not a rewrite.

EWS and DG applicants are flagged as a category on the application and carry the DoE allotment
reference. The system reconciles against the allotment; it does not decide it.

## 6. Fee heads and accounting — ledger-style

Full detail in [ADR-0012](../architecture/adr/0012-fee-ledger-model.md).

Fee heads (tuition, admission, annual/development, transport, exam, activity, late fee) compose into
a fee structure per class, session and category. A fee demand generates **immutable charge rows**.
Payments, concessions, refunds and write-offs are separate signed rows. A balance is always a sum,
never a stored mutable field, and a correction is a credit note, never an edit.

Full double-entry accounting was rejected as a Phase 4 export concern rather than a v1 build. An
invoice with a paid flag was rejected outright: part payments, mid-year concessions and transport
changes all become edits to a settled invoice, and reconciliation stops being possible.

## 7. Exam and report card format — template engine, CBSE defaults per stage

Four built-in stage templates, each a default that a school can adjust:

| Stage | Scheme |
|---|---|
| Classes 1–5 | Grades only, scholastic plus co-scholastic, no percentages |
| Classes 6–8 | Term 1 and Term 2 · Periodic Test 10 + Multiple Assessment 5 + Portfolio 5 + Subject Enrichment 5 + Term Exam 80 |
| Classes 9–10 | Internal 20 + Annual/Board 80 · grades A1–E on the 9-point scale · 33% pass |
| Classes 11–12 | Unit tests, half-yearly, annual · streams and elective combinations |

Grading scales, weightages and co-scholastic descriptors are
[Tier 2 session-scoped settings](../architecture/adr/0006-configurability-model.md), which is the
part that matters: **last year's report card must still render under last year's rules.** Retrofitting
session-scoping onto historical results is a data migration over records people have already
received.

Report cards render server-side to PDF with the school's own header. A hardcoded single format was
rejected because even two CBSE schools weight Multiple Assessment differently. A drag-and-drop report
designer is Phase 5 at the earliest.

## 8. Attendance rules — daily default, period-wise available

Attendance mode is a [Tier 2 per-school setting](../architecture/adr/0006-configurability-model.md):

- **Daily** — once per day by the class teacher. The default, and correct for classes 1–8.
- **Period-wise** — by subject teacher, available for classes 9–12.

Both grains ship in v1. Deferring period-wise was rejected because changing the grain of a
high-volume table after real attendance exists is the expensive kind of migration.

Statuses: present, absent, late, half-day, excused leave, holiday.

Rules:

- Auto-locks at end of day plus 24 hours. After that a teacher files a correction request that an
  admin approves. The original mark and the correction both stay in the audit log.
- Absence alerts to guardians fire on a schedule, not per mark — so a correction inside the window
  does not send a false alarm to a parent.
- Working days, week start and holidays come from the academic calendar, not from per-teacher habit.
- The CBSE 75% board-exam eligibility threshold is a session-scoped setting driving a short-attendance
  report.

## 9. Certificate templates — six, with approval and verification

Transfer Certificate (CBSE prescribed format — the one with legal weight), Bonafide, Character,
Study/Attendance, Fee payment, and Migration/School-leaving.

Each is a configurable HTML template carrying the school header, rendered server-side to PDF.
DOCX templates the school edits in Word were rejected: deterministic rendering and serial integrity
matter more here than layout freedom, and Word compatibility is a permanent support burden.

Integrity rules, which are the actual product:

- Issuing requires an approval step.
- Each issue takes a gapless serial from a per-session register.
- An issued certificate is immutable. Cancellation is a new row; duplicate issue is a distinct,
  logged action.
- The QR code resolves to a public verification endpoint returning **only** certificate number,
  student name, class, issue date and validity. No address, no guardian, no photo, no marks — see
  [ADR-0014](../architecture/adr/0014-data-classification.md) and `AGENTS.md` rule 9.

## 10. Payment gateway — none in v1

**Offline collection only for the first release**, behind a `PaymentGateway` port with an
`OfflinePayment` implementation. Razorpay is the intended first adapter when online payment ships.

The deferral is a scope decision, not an architecture one. The data model is built gateway-ready
now — payment intent, gateway reference, and an idempotent webhook receipt table — so that adding
Razorpay is one adapter and zero migrations. See
[ADR-0013](../architecture/adr/0013-external-provider-ports.md).

## 11. Communication providers — email and web push in v1

**Transactional email and web push only**, behind a `NotificationChannel` port. SMS and WhatsApp are
unregistered channels that become registered when their providers are chosen.

This sidesteps TRAI DLT entirely for the first release. The tradeoff is real and should be stated
plainly: Indian parents read SMS and WhatsApp far more reliably than email, so absence alerts and fee
reminders lose reach until those channels land.

**TRAI DLT registration still starts now**, independently of when SMS ships. Entity registration,
sender ID and per-template approval take weeks, and every path to phone-OTP login or a fee reminder
runs through it.

See [ADR-0013](../architecture/adr/0013-external-provider-ports.md).

## 12. Deployment — one Hostinger VPS in Mumbai

Hostinger **KVM 2** — 2 vCPU, 8 GB RAM, 100 GB NVMe — in the Mumbai region, running Coolify with
PostgreSQL 17, the Spring Boot backend, the Angular static build and MinIO on the same box.

Mumbai gives roughly 20–30 ms to Delhi NCR against the 150–200 ms of the current Seoul Supabase
project, and it answers the "where is our children's data" question a Delhi principal will ask.

KVM 2 is deliberately the smaller plan: enough for one pilot school, and Hostinger upgrades in place.
The known pressure point is bulk PDF generation for report cards at end of term, where the JVM and
PostgreSQL will contend on 8 GB. **Watch it; do not pre-buy for it.**

Documents, certificates and report card PDFs go to MinIO behind a `StorageService` port, one bucket
per school to mirror the schema-per-school tenant boundary. Access is always a short-lived pre-signed
URL issued after a permission check — never a public bucket, never a guessable path.

Full sizing, backup and restore detail in [ADR-0015](../architecture/adr/0015-deployment-baseline.md).

## 13. Data classification — four tiers, enforced in code

Full detail in [ADR-0014](../architecture/adr/0014-data-classification.md).

| Tier | Examples |
|---|---|
| **Restricted** | Biometrics, health records, caste/community, religion, disability/CWSN, guardian income, EWS/BPL/RTE category, APAAR and Aadhaar references |
| **Confidential** | Student and guardian names, date of birth, address, phone, photographs, marks, fee ledger |
| **Internal** | Class and section structure, timetables, fee heads, staff roles |
| **Public** | School profile, mandatory public disclosure pages, the certificate verification response |

The tiers are not a document to remember. A `@Classification` annotation on DTO fields drives log
redaction and export masking, so a violation fails loudly at the boundary instead of depending on
somebody having read this file.

Three tiers were rejected as too coarse — student names would share a bucket with health records.
Two tiers matching DPDP's own personal/non-personal split were rejected because almost everything in
a school ERP is personal data, so the classification would stop telling anyone anything.

Under the DPDP Act 2023 all of this is children's data: verifiable parental consent is required, and
behavioural tracking and targeted advertising are barred. Consent records and a retention clock are
therefore part of the data model, not a policy page.

## Phase 0 output

### Sample data

One full synthetic Delhi CBSE school, produced by a **`dev` profile seeder** — not committed SQL
fixtures. `AGENTS.md` rule 9 rules out real student data, and anonymising children's records well
enough to commit them is harder than generating plausible ones.

Shape:

- 14 classes × 2–3 sections, roughly 600 students, generated Indian-plausible names.
- Guardians with sibling links across classes.
- 40 staff across teaching and non-teaching roles.
- A full fee structure including concession cases.
- One term of attendance, and Term 1 marks.

Deterministic from a fixed seed, so tests are reproducible.

It must include the ugly cases, because those are what break demos and what thin fixtures never
find: a student admitted in December, a fee refund, a part payment, an attendance day that was locked
and then corrected, a guardian with children in two different classes, a student with no photograph.

### Database naming conventions

- `snake_case` everywhere. Table names **singular**: `student`, `fee_charge`, `attendance_mark`.
- Join tables `<a>_<b>`. Foreign keys `<referenced_table>_id`.
- Booleans read as predicates: `is_active`, `has_transport`.
- Timestamps `created_at` / `updated_at`, always `timestamptz`.
- Money is `numeric(12,2)`. Never a float, anywhere, for any reason.
- Indexes `ix_<table>_<columns>`; constraints `uq_`, `ck_`, `fk_`.
- Flyway files `V<version>__<snake_case_description>.sql`, immutable once merged.

**Primary keys are UUIDv7.** The reason is
[schema-per-tenant](../architecture/adr/0011-schema-per-tenant.md): identifiers from different
schools meet whenever a group-of-campuses rollup, a cross-school report or a support export happens,
and bigint sequences would collide there. UUIDv7 keeps the time-ordering that makes UUID primary keys
index acceptably. The cost is larger indexes and less readable IDs in support conversations; that is
accepted.

### API conventions

[ADR-0007](../architecture/adr/0007-api-response-envelope.md) already settles the envelope, error
codes and trace ids. Phase 0 adds the one thing it left open:

**Offset pagination.** `?page=0&size=25&sort=name,asc`, returning items plus `page`, `size`,
`totalElements` and `totalPages` inside the standard envelope as `PageResponse<T>`.

Cursor pagination was rejected for the general case. Every list in a school ERP is a bounded,
admin-facing table — students in a class, fee defaulters, staff on leave — where the user wants "page
7 of 12" and a total count. Cursor pagination cannot answer "how many defaulters are there", which is
the question actually being asked, and its scale advantage appears at volumes this product will not
reach. It stays available for the two genuinely unbounded, append-heavy logs — audit and notification
delivery — if they need it.

Also fixed: `/api/v1/<plural-resource>` paths in kebab-case, ISO-8601 dates, and amounts as decimal
strings rather than floats.

### UI design system

Already delivered and unchanged by Phase 0 — design tokens, the contrast-verified palette,
[ADR-0009](../architecture/adr/0009-hand-built-component-library.md) and
[ADR-0010](../architecture/adr/0010-responsive-and-adaptive-layout.md), and the mockups for the first
six screens in [`docs/artifacts`](../artifacts/README.md).

One Phase 0 addition: **parent and student access is PWA only.** The same Angular app, installable,
with web push — which is exactly why web push was chosen as a v1 notification channel. One codebase,
one deploy, one auth story, and a fix reaches every parent without an app store review.

The limits are accepted knowingly: on iOS, web push requires the parent to install the PWA first, and
there is no SMS auto-read for OTP. Native apps stay where the roadmap already put them, in Phase 5,
"if justified".

### Deployment baseline

[ADR-0015](../architecture/adr/0015-deployment-baseline.md).

### Product backlog

The roadmap's [MVP epics](06-roadmap-and-mvp.md#suggested-mvp-epics) stand as written, in the
[backlog order](06-roadmap-and-mvp.md#backlog-ordering-recommendation) already given: identity and
school setup, students and guardians, attendance, fees, communication, admissions, exams and report
cards, certificates, compliance. Current position is tracked in [`docs/status.md`](../status.md),
which is the file to read rather than this one for what happens next.
