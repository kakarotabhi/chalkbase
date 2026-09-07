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
