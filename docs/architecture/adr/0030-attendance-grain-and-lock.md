# ADR-0030: Attendance is one table for both grains, mutable until it locks, then append-only

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [Phase 0 decision 8](../../requirements/07-phase-0-decisions.md#8-attendance-rules--daily-default-period-wise-available)
  (the product decision this ADR records the shape of), [ADR-0006](0006-configurability-model.md)
  (attendance mode is a Tier 2 setting), [ADR-0011](0011-schema-per-tenant.md) (tenancy),
  [ADR-0018](0018-audit-log.md) (audit), [ADR-0012](0012-fee-ledger-model.md) (the other append-only
  model in this product, and the precedent this one borrows from)

## Context

Phase 0 decision 8 settled the product question: daily attendance by the class teacher is the
default (classes 1-8), period-wise by the subject teacher is available (classes 9-12), and **both
ship in v1** because "changing the grain of a high-volume table after real attendance exists is the
expensive kind of migration." A thousand students times two hundred school days is 200,000 rows a
year, per school, in a schema-per-tenant database — this is the first genuinely high-volume table in
the product, so the shape chosen here is expensive to be wrong about twice.

Two questions were still open once that decision was made:

1. **One table for both grains, or two?**
2. **What happens to a mark once the school has moved on from that day?** The decision requires an
   auto-lock at end of day plus 24 hours, a correction workflow after that, and — the sentence that
   forced this ADR — "the original mark and the correction both stay in the audit log." ADR-0018 §3
   already rules out the audit log holding a field's _value_, only its name, so that requirement
   cannot be met by the audit log alone; something has to decide where the value survives.

## Decision

**One table, `attendance_mark`, for both grains.** `period_number` and `subject_id` are nullable
columns present from the first migration; the daily grain leaves both null, the period-wise grain
fills both. A `check` constraint pairs them so a mark cannot half-express one grain and half the
other. Two partial unique indexes give each grain its own uniqueness: at most one daily mark per
student per day (`period_number is null`), and at most one mark per student, per day, per period
(`period_number is not null`).

**Only the daily grain has a write path in this build.** `AttendanceMarkingService` never
constructs a mark with a period number, and no controller endpoint accepts one. Period-wise gets its
table shape and nothing more — see [What this build does not do](#what-this-build-does-not-do).

**Mutable until it locks, then append-only.** A mark may be edited in place — the same row, a plain
`update` — until end of day plus 24 hours after its `attendance_date`. `AttendanceMark#isEditableOn`
computes this from `LocalDate.now()` at read and write time; there is no scheduled job, no stored
`locked` flag, and nothing to drift out of step with the clock. Once that window has passed, the
only path to a changed status is `attendance_correction_request`: a teacher's request, carrying its
own snapshot of what the mark said before, and an administrator's decision on it, which
`AttendanceCorrectionService` applies onto the mark it targets.

**The correction request table holds the value the audit log cannot.** `previous_status` is set once,
when the request is filed, and never edited. This is what "the original mark and the correction
both stay in the audit log" turns out to mean once ADR-0018 is taken literally: the audit log gets
two entries against the same mark id — `ATTENDANCE_MARKS_RECORDED` (or `ENTITY_CREATED`, for a mark
created inside its own edit window) for the original, `ATTENDANCE_CORRECTION_APPLIED` for the
correction — and neither overwrites the other, because `AuditService.recordChange` only ever
appends. Field **names** only, per the rule; the actual before-and-after values live in
`attendance_correction_request`, which is ordinary domain data and always was allowed to hold them.

## Why one table

**A `LEFT JOIN` (or a `UNION`) on every read is the cost of two tables, forever.** The marking
screen, a student's history, a monthly section register and the short-attendance report (FR-049,
not built by this lane but designed for) all ask "what was this student's attendance on this day" —
a question that does not care which grain a school runs, only that the school picked one
(Phase 0 decision 8, ADR-0006). Two tables would put that choice into every query that asks it,
instead of into the one row.

**The alternative — start with daily only, add period-wise later — is the migration the decision
explicitly rejects.** Splitting a live `attendance_mark` table into two after a school has two
hundred days of real rows would mean picking a grain for existing rows that never recorded one,
under load, on a table an admission's worth of screens already query. Two nullable columns, decided
once, cost four bytes and a `check` constraint today.

**A shared table does not force a shared write path**, which is why only the daily grain has one.
Committing to a _shape_ now is cheap and reversible in the ordinary way — add a migration, add a
column. Committing to a _screen_ nobody has designed yet is not, and Phase 0 decision 8 does not ask
for the period-wise screen from this lane; it asks for the table not to need surgery when that
screen is built.

## Why mutable-then-append-only, rather than append-only from the first mark

Fees are append-only from the first charge (ADR-0012), because a charge is money and a mistake in
money needs a paper trail from the moment it exists. A mark taken at 9:05 a.m. and corrected at
9:12 a.m. because the teacher miscounted is not that: it is the same fact, written twice, on the same
morning, by the same person, before anyone outside the classroom has acted on it. Treating that as a
correction — a request, a reason, someone else's approval — would make the routine case (a teacher
double-checking their own roll call) as heavy as the rare one (a dispute raised after the fact), and
a class teacher marking thirty students on a phone does not need a workflow to fix a slipped tap.

The lock is what divides the two. Before it, nobody outside the classroom has had a chance to act on
the mark — no absence alert has fired (Phase 0 decision 8 explicitly schedules those separately, for
exactly this reason: "a correction inside the window does not send a false alarm to a parent"), no
report has been run against it. After it, the mark is a fact other people and other processes may
already be relying on, and a silent edit becomes indistinguishable from a memory eighteen months
later at "why does the register say something different from what I remember." That is the point at
which a reason, an approval and a permanent, queryable record of the disagreement start earning
their cost.

## What this build does not do

- **No period-wise screen.** The table accepts the shape; nothing writes it. A subject teacher's
  marking screen, and whatever timetable concept tells it which subject a period is, are later work.
- **No academic calendar.** [FR-015](../../requirements/02-functional-requirements.md) asks for one
  and it does not exist. Phase 0 decision 8 says working days, week start and holidays should come
  from it, not from per-teacher habit; without it, this build does not validate the date marked
  against a working-days calendar at all. A teacher may mark a Sunday or a holiday — the `HOLIDAY`
  status exists precisely so a school can record one manually — and nothing here computes "this
  school does not work Sundays" to stop them. This is a real gap for the reports FR-049 asks for
  (a working-days denominator needs the calendar this build does not have), not an oversight the
  requirement failed to describe. It is the calendar module's job to fill in, additively, when
  built — this table needs no migration for that day to come.
- **No absence alerts.** Phase 0 decision 8 requires they fire on a schedule, against locked data,
  through a communication module and channel ports ([ADR-0013](0013-external-provider-ports.md))
  that do not exist. The lock this ADR defines is what a scheduled alert job will read against once
  built: "locked and absent" is a stable question from the day this table exists, even though
  nothing yet asks it.
- **No 75% eligibility report.** A session-scoped setting and a report over it — out of scope for
  this lane, noted so it is not mistaken for forgotten.

## Leave requests: how an approval reaches the register, resolved 2026-09-09

FR-047 asks for leave applications with an approval step. Phase 2's own scope document names the
trap directly: "a request form that captures dates and a reason looks done without an approval
step, or — more subtly — an approval step that never touches the attendance record it is meant to
explain." `EXCUSED_LEAVE` already exists as one of the six statuses this ADR's Decision section
settles on; an approved leave request that does not connect to it is that second, disconnected
ledger.

**The connection is a read, made when a section's register is opened for the covered date — not a
write made when the request is approved.** `attendance_leave_request` is a new table (its own
migration, `V2026_09_09_0143`), with no foreign key to `attendance_mark` in either direction.
Approving a request never inserts or updates a mark. Instead, `AttendanceMarkingService.view` —
and therefore `mark`, which calls the same builder for its own response — reads
`attendance_leave_request` for the section's roster and the date opened, and offers
`AttendanceStudentMark.approvedLeave` as a signal the marking screen uses to default an unmarked
student to `EXCUSED_LEAVE`. The teacher's own save is still the only thing that writes
`attendance_mark`, and a teacher who sees the signal and marks the student `PRESENT` anyway — the
child came after all — is not overridden by anything here.

**Why not write the mark at approval time.** Two provisions this ADR and its sibling DTOs already
settle make it the wrong shape, not merely an alternative one:

- `MarkAttendanceRequest.attendanceDate` is `@PastOrPresent`, and `AttendanceErrorCode.FUTURE_DATE_NOT_ALLOWED`
  is the service-level twin of that refusal. A leave request is advance notice, most often of a date
  that has not happened yet; writing a mark for it at approval time means writing a mark for a
  future date, which every other path into this table refuses.
- `AttendanceMarkingService.mark` validates every entry against `StudentLookup.rosterOfSection`
  **as of the day marked** — a roster that can genuinely change before the date arrives (a student
  moves section, or leaves the school). Writing a mark at approval time commits to a roster that is
  not yet the fact of record.

A read made at the moment marking actually happens has neither problem: the date being read for is,
by construction, the date the roster and the lock window are both being evaluated against already.

**Why a read rather than nothing (the "mailbox" failure).** A leave request that only ever sits in
its own table, checked by nobody marking a register, is exactly the disconnected ledger Phase 2's
scope document warns about — a form with an approval step that produces a fact nobody downstream
ever reads. The read is what makes the approval visible where it matters, on the register itself,
without asking this module to predict a future roster or pre-empt a teacher's own judgment on the
day.

**A backdated leave request is refused, not routed anywhere.** `AttendanceErrorCode.LEAVE_DATE_IN_PAST`
refuses a `startDate` before today outright. This module already has an approval workflow for a day
that has already happened — `attendance_correction_request`, decided by
`AttendanceCorrectionService` — and a leave request for a past date is a request about a day that
either already has a mark (a correction is the right tool) or does not yet (in which case the
school marks the day honestly first, then corrects it if the absence should read as excused). Adding
a second approval mechanism that reaches the same outcome by a different name is the "module becomes
unmaintainable" failure `AGENTS.md`'s own conventions warn against, so this lane does not build one:
one date range in the future, one decision, one place a correction is ever requested from.

**A locked day.** Ordinarily an approved leave request is acted on before its date locks — the
teacher opens the register some time before end-of-day-plus-24h and either accepts the
`EXCUSED_LEAVE` default or marks otherwise. If nobody opens the register at all before the window
closes, the day locks with that student simply unmarked, exactly as it would for any student on any
day nobody marked — this lane adds no scheduled job to write a mark on a school's behalf, the same
restraint ADR-0030's own lock already keeps ("computed from `LocalDate.now()` ... nothing to drift
out of step with the clock"). A day that locks unmarked already needed a human to notice and act;
an approved leave request does not change who that is or invent a job to replace them. If it is
later noticed and needs a record, the correction-request workflow is what creates one, the same as
any other missed mark.

**Who may request, who may approve, and why a parent slots in later without reshaping the record.**
`ScopeType.WARD` is deliberately unassignable (see `RoleTemplates`' own note on `PARENT` holding no
permissions yet), so nobody requests a leave as a parent today — `attendance_leave_request.requested_by`
is always a staff `user_account`, someone acting on a parent's behalf, the same shape
`attendance_mark.marked_by` already has. Nothing about the table or the DTO shape needs to change
when a parent gets a login of their own: `requested_by` stays "whoever's account filed this",
whether that account belongs to a class teacher today or, later, to a guardian with a `WARD`-scoped
session of their own — a new caller of the same `POST .../leave-requests` endpoint, not a new column.
`AttendancePermissions.LEAVE_REQUEST` and `LEAVE_APPROVE` are two permissions, not one — see that
class's own Javadoc for why a school may reasonably want to let staff file without letting the same
staff decide, and `RoleTemplates`' note on why `CLASS_TEACHER` holds both today regardless.

## Consequences

- `attendance_mark` and `attendance_correction_request` are per-tenant, owned by the new
  `attendance` module. Reaches `academics.api.AcademicsLookup` (the section being marked, the current
  session) and `student.api.StudentLookup` (the roster, and a name for a bare student id) — never a
  join, never a domain import.
- Two indexes carry every query this module makes: `idx_attendance_mark_section_date` (a section, a
  date — the marking screen, a monthly register) and `idx_attendance_mark_student_date` (a student,
  a range — one child's history, and the eligibility report when it is built). Neither is a text
  search; this table does not repeat the sequential-scan trap `docs/status.md` records for guardian
  search.
- A correction changes two rows in one transaction: the request (decided) and the mark (corrected),
  each with its own audit entry. Approving is the only path that writes a mark's `status` once its
  edit window has closed — `AttendanceCorrectionService`, never `AttendanceMarkingService`.
- `attendance_leave_request` is a third per-tenant table, also owned by `attendance`, also reaching
  `academics` and `student` only through their named interfaces. Deciding one changes exactly one
  row — see the amendment above for why that is one audit entry rather than the two a correction's
  decision produces.
