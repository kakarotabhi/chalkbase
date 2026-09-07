# Phase 2 scope: admissions, attendance, fees and communication

[The roadmap](06-roadmap-and-mvp.md#phase-2-admissions-attendance-fees-and-communication) gives
Phase 2 fifteen features in a bare list and three exit criteria. That was enough to *name* Phase 2;
it is not enough to *hand it out*. Phase 1's own status file exists because a roadmap line and a
finished feature are different claims — subjects looked finished because it shared a line with
classes, and roles looked finished because enforcement existed and management did not. Fifteen
roadmap lines map onto sixty-one functional requirements across four sections of
[the functional requirements](02-functional-requirements.md), which means most features carry
several FRs and some FRs are not this phase's to build at all. Saying which is which, before work
starts, is the point of this document.

Four sections follow: a tracker (state, owning module, FRs, blockers), what each feature actually
means and where it can look done without being done, the decisions that have to be taken before work
starts, and what can run in parallel.

## 1. Tracker

Every row starts ⬜ **Not started**. Module names are the ones [module-map.md](../architecture/module-map.md)
already declares as planned: `admission`, `attendance`, `fee`, `communication`. The two portal
features have no owning module of their own — see [§2](#parent-and-teacher-portal-basics) and
[decision 3](#3-the-decisions-that-must-be-taken-before-work-starts) for why that is a real gap
rather than an oversight in this table.

| # | Feature | State | Module | FRs covered | Blocked by |
|---|---|---|---|---|---|
| 1 | Enquiry management | ⬜ Not started | `admission` | FR-016, FR-017 | nothing |
| 2 | Online admission form | ⬜ Not started | `admission` | FR-018, FR-019, FR-023 | nothing |
| 3 | Admission workflow | ⬜ Not started | `admission` | FR-021, FR-022, FR-026 (partial), FR-027 (partial) | decision on admission-fee vs. the fee ledger ([§3](#3-the-decisions-that-must-be-taken-before-work-starts)) |
| 4 | Student conversion | ⬜ Not started | `admission` → `student` | FR-024, FR-025 | admission workflow reaching "admitted" |
| 5 | Daily attendance | ⬜ Not started | `attendance` | FR-044, FR-046, FR-049 (partial), FR-051 | nothing — sections and enrolment already exist |
| 6 | Leave requests | ⬜ Not started | `attendance` | FR-047 | daily attendance (the record a leave adjusts) |
| 7 | Absence alerts | ⬜ Not started | `attendance` + `communication` | FR-048 | daily attendance; `communication`'s notification port |
| 8 | Fee structure | ⬜ Not started | `fee` | FR-075, FR-076 (partial), FR-077, FR-078 | nothing — students and classes already exist |
| 9 | Fee demand | ⬜ Not started | `fee` | FR-079 | fee structure |
| 10 | Online/offline fee collection | ⬜ Not started | `fee` | FR-080 (port only), FR-081 | payment gateway decision blocks the online half only ([§3](#3-the-decisions-that-must-be-taken-before-work-starts)) |
| 11 | Receipts | ⬜ Not started | `fee` | FR-082, FR-083, FR-088 | fee collection |
| 12 | Dues and reminders | ⬜ Not started | `fee` + `communication` | FR-079, FR-084 (partial) | fee demand; `communication`'s notification port |
| 13 | Circulars and notices | ⬜ Not started | `communication` | FR-097, FR-098 (partial), FR-099, FR-100 (partial), FR-101, FR-104 | nothing for class/section targeting; fee- and attendance-status targeting wait on those modules |
| 14 | Parent portal basics | ⬜ Not started | none yet — reads `student`, `attendance`, `fee`, `communication` | FR-105 (partial), FR-108, FR-108a–f, FR-109 | the parent-account and WARD-scope decision ([§3](#3-the-decisions-that-must-be-taken-before-work-starts)); at least one of attendance/fee/communication existing |
| 15 | Teacher portal basics | ⬜ Not started | none yet — reads `attendance`, `student`, `communication` | FR-107 (partial), FR-108, FR-108a–f | attendance module existing |

### What is deferred, and why

Sixty-one FRs, fifteen roadmap lines: most of the deferral is inside a feature, not between features,
which is why the table above marks so many rows "(partial)" rather than leaving whole features off it.

**§3 Admissions (12 FRs).** Deferred: **FR-020** (seat matrix by class/category/quota/gender/stream) —
Phase 0 already rejected a points engine for the same reason a seat matrix earns caution now: it is
real configuration surface a linear pipeline does not need to function, and it belongs with whatever
later work revisits screening (07-phase-0-decisions.md §5). **FR-026**'s Aadhaar-based duplicate
matching is deferred — Aadhaar is a Restricted-tier reference under ADR-0014 and no applicant-side
encryption exists yet; mobile, email and name/date-of-birth matching are not deferred, because the
phone-normalisation this needs already exists for guardians (ADR-0020 §5, `guardian.phone_digits`).
**FR-027**'s offer letters are built as a plain notification rather than a templated PDF — the
template-engine machinery Phase 0 promised for report cards and certificates (07-phase-0-decisions.md
§7, §9) is Phase 3 work, and duplicating a smaller version of it here for one letter is not worth it.

**§6 Attendance (8 FRs).** Deferred: **FR-045**, period-wise attendance, is sequenced into Phase 3
alongside the timetable and period structure (FR-052) it depends on — this is a scheduling call, not
a reopening of Phase 0's decision that both grains ship in v1 (07-phase-0-decisions.md §8); marking a
subject period's attendance needs periods to exist first. **FR-049**'s short-attendance and
board-eligibility reports are deferred with it, since the 75% threshold report is naturally read at
the same time period-wise data would exist; the monthly and percentage report is not deferred.
**FR-050**, biometric/RFID/QR import, is P2 and stays with Phase 5's device integrations.

**§10 Fee Management (14 FRs).** Deferred: **FR-080**'s online half — the `PaymentGateway` port
(ADR-0013) is built, but no adapter goes live until [decision 1](#3-the-decisions-that-must-be-taken-before-work-starts)
is answered; offline collection is not deferred. **FR-085** (gateway settlement and bank
reconciliation) has nothing to reconcile against without a gateway, so it waits on the same decision.
**FR-086** (Tally/CSV export) is explicitly a Phase 4 export layer per ADR-0012's own consequences
section. **FR-087** (non-tuition tax treatment) is deferred — the pilot school's fee heads do not
need it yet, and inventing the configuration ahead of a real case is exactly what ADR-0006 warns
against. **FR-076**'s transport-route and hostel scoping is deferred until those Phase 4 modules
exist to scope against — the same reasoning `docs/status.md` already used to keep transport and
hostel off the student record in Phase 1.

**§12 Communication (8 FRs).** Deferred: **FR-098**'s SMS and WhatsApp channels are declared types
with no adapter (ADR-0013) until TRAI DLT registration completes — [decision 2](#3-the-decisions-that-must-be-taken-before-work-starts).
**FR-100**'s targeting by transport route, hostel, house and club is deferred because none of that
data exists yet — `student.house`/`club`/`stream` are not built (FR-031 is not on Phase 1's done
list); targeting by class, section, fee status, attendance status and a custom group is not deferred.
**FR-102** (PTM scheduling) and **FR-103** (helpdesk/ticketing) are not on the roadmap's fifteen-line
list at all and are deferred wholesale — there is no Phase 2 feature to attach them to.

**§13 Portals (19 FRs).** Deferred: **FR-106**, the entire student portal, is absent from the
roadmap's Phase 2 list — only parent and teacher portals are named — and is deferred wholesale,
presumably to Phase 3 once homework, results and timetables exist for it to show. **FR-105**'s
homework, timetable, results, transport, hostel and certificate-request sections grey out until
their owning modules ship (Phase 3 and Phase 4); student profile, attendance, dues and receipts, and
circulars are what Phase 2 actually delivers. **FR-107** is the same shape: attendance marking,
student search and communication are Phase 2; marks entry, timetable, substitution and lesson plans
wait for Phase 3. **FR-110** (notification preferences) is deferred as a nicety. **FR-108a–g and
FR-110a–g** are cross-cutting conventions already built in Phase 1 (the adaptive shell, the
hand-built component library, server-driven navigation) — they apply automatically to whatever
portal screens get built and are not new work.

## 2. What each feature actually means

### Admissions (`admission`)

**Enquiry management.** A school's front office fields walk-ins, phone calls and website leads long
before anyone applies. The system's job is to hold every one of those as a row with a status, an
assigned counsellor and a follow-up history (FR-016, FR-017) — not to be a form that swallows
enquiries and never surfaces them again. **The trap:** an enquiry-capture form with no assignment and
no follow-up view looks like "enquiry management" is done, but it is a mailbox. The counsellor still
runs their follow-ups from a paper register, which is the exact failure this feature exists to
remove. The smallest honest version has capture, status, an assigned counsellor and a due-date list
of pending follow-ups — a notification schedule for reminders is not required for that to be true.

**Online admission form.** A form that is not "configurable by class and board" (FR-018) is a
website contact form with delusions. The actual requirement is that the fields a Nursery applicant
sees differ from what a Class 9 applicant sees, and that EWS/DG tagging (FR-023) is captured, not
inferred later. **The trap:** a single generic form that captures name, class and contact details
looks like this feature is done, because a parent can submit *something*. Whether the DoE's EWS/DG
allotment reference has anywhere to go, and whether the form differs by class, is the actual
substance and is invisible from a screenshot of one form.

**Admission workflow.** The linear pipeline Phase 0 already chose (07-phase-0-decisions.md §5):
enquiry → application → document verification → screening outcome → approval → admission fee →
student record. Every stage transition needs who, when and why recorded, because that is what makes
a workflow auditable rather than a status dropdown. **The trap:** a set of enum values on an
`application` row, with no transition history, looks like "admissions has a workflow" from the
screen — until someone asks who approved a specific applicant and the answer is not recorded
anywhere, which is precisely the audit gap [Phase 0's own decision on the admission workflow](07-phase-0-decisions.md#5-admission-workflow--simple-linear-pipeline)
(§5) exists to close.

**Student conversion.** FR-025 says "without re-entry", which is the whole feature in three words.
**The trap:** this looks like the smallest item on the list — a button that copies an application
into a student record — so it is the one most likely to be built without the actual hard part: the
admission number generation rule (FR-024), and guardian deduplication against the existing directory
by phone (the same rule ADR-0020 §5 built for import, reused rather than reinvented). A conversion
button that silently creates a duplicate guardian because it did not check the phone directory has
technically satisfied "converts an applicant" and completely failed "without re-entry" — the office
still fixes it by hand.

### Attendance (`attendance`)

**Daily attendance.** Phase 0 already decided the rules (07-phase-0-decisions.md §8): daily by
default, auto-lock at end of day plus 24 hours, and a correction request after that, not an open
edit. **The trap:** a screen where a class teacher taps present/absent for each student looks
finished, and it is the easy 80% of this feature. The lock-and-correction workflow is the actual
requirement (FR-051) — a screen that lets a teacher freely edit attendance from any date is not
"daily attendance", it is an unaudited spreadsheet with a nicer UI, and it is the same shape of trap
Phase 1 hit with roles: enforcement without management, here inverted to marking without locking.

**Leave requests.** FR-047 requires "approval by authorized staff" as part of the feature, not as a
follow-on. **The trap:** a request form that captures dates and a reason looks done without an
approval step, or — more subtly — an approval step that never touches the attendance record it is
meant to explain. If an approved leave does not visibly connect to why a student was marked absent
(or excused) on those dates, the feature has produced a second, disconnected ledger of "reasons"
that nobody reading the attendance register ever sees.

**Absence alerts.** ADR-0013 is explicit that alerts fire on a schedule, not per mark, so a
correction inside the lock window never becomes a false alarm a parent already received and cannot
un-receive. **The trap:** the obvious first implementation is "on absent-mark saved, send a
notification" — it looks done, it demos well, and it is wrong in exactly the way the ADR called out
in advance. This is the one Phase 2 feature where the "looks done" version is not merely incomplete,
it actively damages trust with parents the day a correction is filed.

### Fees (`fee`)

**Fee structure.** ADR-0012 rule 6 requires fee structures to be session-scoped, because Delhi's DoE
needs last year's filed structure to still be reproducible. **The trap:** defining fee heads and
amounts against "the current structure" and editing that structure in place next session looks like
a working fee-setup screen right up until an auditor asks what was charged two sessions ago and the
answer has been overwritten. Versioning by session has to be there from the first structure ever
created, not retrofitted once someone asks.

**Fee demand.** "Generate a fee demand" sounds like a button; the requirement underneath is that the
button produces immutable `fee_charge` rows (ADR-0012), never a mutable invoice row with a paid flag
— option 1 in ADR-0012, rejected by name. **The trap:** an "invoice" table with an `amount_due` and
a `paid` boolean is the natural first design for anyone who has not read the ADR, and it will pass
every demo, because a demo never asks the ledger to explain a part payment, a concession and a
reversal on the same charge in the same term.

**Online/offline fee collection.** This roadmap line hides two very differently sized pieces of work.
Offline collection — cash, cheque, UPI reference, recorded by an operator at a counter — is real
Phase 2 work with nothing external blocking it. Online collection is blocked on
[decision 1](#3-the-decisions-that-must-be-taken-before-work-starts) below. **The trap:** treating
"fee collection" as one item either silently drops the online half (acceptable, if said out loud) or
tempts someone into picking a gateway to keep the checklist item moving — which is exactly the kind
of decision this document exists to stop an agent from inventing.

**Receipts.** FR-082 asks for a *configurable* receipt series and a cancellation/void workflow that
produces a credit note, never a delete — which is also most of what FR-088's immutable ledger-style
audit history asks for, since a receipt is a `fee_ledger_entry` under ADR-0012 and gets that
property by construction rather than as separate work. **The trap:** a receipt PDF with a sequential number looks
like "receipts" is done, but two accountants collecting cash at the same counter at the same moment
is the case that actually tests gaplessness — a naive `max(number) + 1` under concurrent writes will
eventually produce a duplicate or a gap, and a "cancel" button that deletes the row rather than
issuing a credit note quietly undoes ADR-0012's whole guarantee under a different feature name.

**Dues and reminders.** This looks like a report screen. The exit criterion "the fee ledger
reconciles correctly" actually lives here: a balance is `sum(charges) - sum(entries)`, computed live,
never a stored column (ADR-0012 rule 2). **The trap:** the dues screen is the one place performance
pressure will tempt someone to cache a `balance` column "just for the list view" — which is precisely
the anti-pattern ADR-0012 rejected as option 1, reintroduced through a screen that was never asked to
touch the ledger's storage model, only to read it fast.

### Communication (`communication`)

**Circulars and notices.** The requirement is not "send a message to everyone" — it is targeted
delivery (FR-100), per-recipient delivery status (FR-101) and an acknowledgement that can be tracked
(FR-104). **The trap:** a broadcast button that fans out an email to every parent looks like
"circulars" is done, and it is a mailing list. There is also a privacy trap worth naming: audience
targeting by fee status or attendance status is itself Confidential-adjacent information (ADR-0014)
— building the *composer* to show "142 recipients with dues" is fine; leaking who's on that list to
someone who should not see individual fee status is not, and the permission that gates the targeting
query needs to be the module's own, not borrowed from `fee`'s.

### Parent and teacher portal basics

These two lines carry the sharpest version of Phase 1's lesson, because the roadmap line names a
screen and the actual work is underneath it, invisible from the line itself.

**Parent portal basics.** A login screen that shows *something* for a parent looks like this feature
is done. The real work is the account and scope model underneath it — see
[decision 3](#3-the-decisions-that-must-be-taken-before-work-starts) — and the trap is specific and
dangerous: it is easy to build the portal's reads as ordinary `SCHOOL`-scoped queries, the same
pattern every staff screen already uses, because that pattern already exists and WARD-scoping does
not. A parent portal built against the wrong scope does not fail loudly; it works perfectly in a demo
with one family and leaks every student in the school to every parent the day a second family signs
in. FR-105's own list (profile, attendance, fees, receipts, circulars, homework, timetable, results,
transport, hostel, certificate requests) is eleven things; the honest Phase 2 version is five of
them — profile, attendance, dues, receipts, circulars — and the other six should be visibly absent
rather than present with placeholder data.

**Teacher portal basics.** FR-107 lists eight capabilities; attendance, student search and
communication are what Phase 2's other modules can actually back. Marks entry, timetable,
substitution and lesson plans belong to modules that do not exist until Phase 3. **The trap:** "the
teacher portal" as a name suggests one deliverable, so a teacher-facing attendance screen under a
`/portal` route can be mistaken for satisfying the whole roadmap line, the same way subjects once
looked finished for sharing a line with classes. Naming which of the eight are in, in the tracker and
in the release notes, is what keeps this honest.

## 3. The decisions that must be taken before work starts

Four of these are worth the product owner's time before any of these modules are scoped in detail;
two are smaller and lower-urgency, included because leaving them for whoever builds the feature to
invent is exactly the failure this table exists to prevent.

| # | Decision | Why it blocks | Urgency |
|---|---|---|---|
| 1 | Payment gateway | Blocks the online half of fee collection (FR-080) and gateway/bank reconciliation (FR-085). The port and the offline path are not blocked — build them now. | Before online fees ship; not before Phase 2 starts |
| 2 | TRAI DLT registration | Weeks of paperwork, cannot start retroactively. Blocks SMS-based absence alerts and fee reminders. Does not block Phase 2 itself — v1 ships email and web push (ADR-0013). | Start now (unchanged from Phase 1's status.md — this is not a new finding) |
| 3 | Parent (and by extension teacher) portal accounts — the identity question | The largest open item in this document. See below. | Before parent portal basics starts; ideally before fee/attendance/communication finalise their read-permission shapes, since those permissions need a WARD case to grant against |
| 4 | What "the fee ledger reconciles correctly" means as a testable claim | ADR-0012 settles the storage model; it does not define what a reconciliation test checks. See below. | Before fee demand and collection are called "done" |
| 5 | Does admission-fee collection reuse the `fee` module's ledger, or is it a separate one-off charge? | `module-map.md` gives `admission` its own "admission fees", distinct from `fee`'s ledger. If admission fees are meant to end up as ordinary `fee_charge`/`fee_ledger_entry` rows, the admission module needs to write into `fee`'s tables through a named interface, not duplicate ADR-0012's model. If they are deliberately separate — a one-off charge before a student record exists, which cannot yet reference a student id — that is a smaller, self-contained model, but it should be chosen, not discovered mid-build. | Before the admission-fee substage of the admission workflow is built |
| 6 | Receipt series granularity | ADR-0012 rule 4 already decides gapless, per-session, per-school. Not decided: whether a school wants separate series per fee category (a separate range for admission-fee receipts, for tax-filing reasons some schools have) or one series for everything. | Low — a single series per school is a defensible default; revisit if a real school asks |

### Decision 3, in full: what a parent account requires

This is an identity question, not a portal question, and it is the one item in this document that
most needs the product owner's attention before any portal screen is designed.

[ADR-0017](../architecture/adr/0017-identity-model.md) already decided that a guardian is a person
record, not an account — most guardians never sign in, and a dormant account for every one of them
would put thousands of unused rows in the identity tables. An account is created only when a
guardian actually needs portal access. What ADR-0017 leaves open, because nothing needed it yet:

- **How the account gets created.** Phase 1's `/api/access/users` is a one-account-at-a-time screen
  for a school office creating a handful of staff logins. A pilot school onboarding will have
  hundreds of guardians who need an account roughly at once — most plausibly at student conversion
  or at bulk import, not one at a time through a form built for a dozen staff. That is a bulk
  creation flow the existing screen was never designed for, not a smaller version of it.
- **What the username is.** ADR-0017 says login uses a school-issued username, "usually the admission
  number" — which identifies a *student*, not a guardian, and a guardian with two children in the
  school cannot have two usernames pointing at the same account. Guardian phone number is the more
  natural identifier, and it is already normalised for search (`guardian.phone_digits`,
  ADR-0020 §5) — but two guardians sharing a phone number, which the existing guardian search already
  tolerates rather than refuses, is a real collision here in a way it was not for search.
- **How access is enforced.** [ADR-0005](../architecture/adr/0005-authorization-model.md) already
  named `WARD` as a scope and deliberately refuses to let it be assigned — `ScopeType.java` says so
  in a comment, and `RoleManagementService` rejects it outright at the API boundary
  (`in.chalkbase.identity.application.RoleManagementService`, the `validatedScope` check). The
  `PARENT` role template exists and holds **zero permissions**, and `RoleTemplates.java` says exactly
  why: "what they may see is their own child's record... a SELF-scoped read derived from the
  guardian-of relationship, which this module now has the data for but no scope resolution to
  enforce with." That sentence is the actual scope of this decision: `AccessScope` needs a genuine
  `WARD` code path — resolved from `student_guardian` at query time, the same way tenancy resolves
  from `search_path` — before a single permission can be safely granted to `PARENT`. This is real
  backend work in `platform.security` plus a new read interface from `student`, not a configuration
  toggle, and it has to land before fee, attendance or communication can safely expose a
  guardian-facing read.

None of this blocks the `admission`, `attendance`, `fee` or `communication` modules from starting —
none of their core work needs a parent account to exist. It blocks exactly one thing, but that thing
is Parent portal basics in its entirety, which is why it is called out here rather than left for
whoever picks up that feature to discover.

### Decision 4, in full: what "reconciles correctly" has to mean

ADR-0012 settles the storage model — append-only charges and signed ledger entries, balance always
computed as `sum(charges) - sum(entries)`, never a stored field. What it does not settle is what a
reconciliation *test* checks, and the roadmap's exit criterion ("the fee ledger reconciles correctly")
needs an answer before anyone can claim it is met. At minimum this should mean: every balance the
dues report shows is reproducible by summing the ledger for that student and head, with no code path
that reads a cached total instead; every reversal correctly nets to zero against the entry it
reverses; and a receipt's number, once issued, is never reused even under concurrent writes. Bank and
gateway reconciliation (FR-085) is explicitly not part of this, since it needs a gateway
([decision 1](#3-the-decisions-that-must-be-taken-before-work-starts)) that will not exist yet.

### Attendance rules are not on this list, on purpose

Phase 0 already decided attendance's grain, statuses, lock window and alert timing
(07-phase-0-decisions.md §8). The only Phase 2 judgment call is sequencing — period-wise attendance
moves to Phase 3 with the timetable it depends on (§1, deferred FRs, above) — and that is a
scheduling decision already recorded in this document, not a reopened one.

## 4. What can be built in parallel, and what cannot

`admission`, `attendance`, `fee` and `communication` are all new modules with no shipped tables yet,
which is unusually parallel-friendly — four agents, four disjoint sets of migrations, four disjoint
`features/` folders, the same shape that let export and documents run side by side in Phase 1. But
"new" is not "independent": each one's dependencies are just mostly already satisfied by Phase 1
rather than by each other.

| Feature | Depends on | Already built? |
|---|---|---|
| `admission` (enquiry, application, workflow) | `identity` (accounts), `academics` (classes, for the form) | Yes — nothing new to wait for |
| Student conversion | `admission` reaching "admitted"; `student`'s create-student path | `student`'s half is built; `admission`'s half is this phase's own work |
| `attendance` (daily marking) | `academics` (sections), `student` (enrolment) | Yes |
| `fee` (structure, demand, collection, receipts) | `student` (who is being charged), `academics` (class/session) | Yes |
| Leave requests, absence alerts | `attendance`'s own daily-marking record | No — sequenced after `attendance`'s first slice |
| Dues and reminders | `fee`'s demand and collection; `communication`'s notification port | No — sequenced after both |
| Circulars and notices (class/section targeting) | `academics`, `student` | Yes |
| Circulars and notices (fee-/attendance-status targeting) | `fee`, `attendance` | No — the targeting-by-status slice waits on those modules existing |
| Parent portal basics | the identity decision (§3); at least one of `attendance`/`fee`/`communication` shipped | No — the most blocked item in this phase |
| Teacher portal basics | `attendance` shipped (for marking); `communication` shipped (for the communication tab) | No, but less blocked than parent portal — teacher accounts already exist, no identity decision needed |

**The first three that can start immediately, with nothing blocking them and no decision pending:**
enquiry and application intake in `admission`, daily attendance marking in `attendance`, and fee
heads/structure definition in `fee`. Each sits on Phase 1 work that is already merged — identity,
academics, student — and each owns tables no other Phase 2 module touches, so three agents can start
today on three different branches without a collision beyond the usual shared files
([parallel-work.md](../development/parallel-work.md)'s table: `contracts/`, `models.ts`,
`RoleTemplates.java`, `docs/status.md`). `communication`'s class/section-targeted circular sending
could join them as a fourth for the same reason, but it is lower priority against the roadmap's own
backlog ordering, which puts attendance and fees ahead of communication.

**What should not start yet:** parent portal basics, until decision 3 is answered; the online half of
fee collection, until decision 1 is answered; anything depending on `attendance` or `fee` existing
(leave requests, dues and reminders, status-based targeting, teacher portal's non-attendance
sections).
