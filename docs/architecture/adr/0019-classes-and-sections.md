# ADR-0019: Classes and sections are structural, not session-scoped

- Status: Accepted
- Date: 2026-09-06
- Deciders: Raja
- Related: [ADR-0011](0011-schema-per-tenant.md) (tenancy), [ADR-0006](0006-configurability-model.md) (configurability), [ADR-0018](0018-audit-log.md) (audit)

## Context

Phase 1 needs classes and sections before it can have students: a student's enrolment names a class,
a section and a session, and roll numbers are unique per class-section-session.

That last constraint is where the decision hides. If a roll number is unique *per session*, is the
class-section itself a per-session thing? Two shapes are possible and they are not equally easy to
change later.

**Session-scoped.** "Class 5 Section A" exists once per academic year. Creating the 2027-28 session
copies every class and section into it. History is exact: if Class 5 ran two sections in 2026-27 and
three in 2027-28, the rows say so.

**Structural.** A class and a section are facts about the school, not about a year. `Class 5` and
its `Section A` are one row each, and the session appears on the *enrolment* that puts a student in
them.

## Decision

**Structural.** A class and a section are rows about the school. Session-scoping lives on whatever
references them — enrolment first, and later the class-teacher assignment, which is genuinely
per-year and will carry `(section_id, session_id, staff_id)`.

## Why

The session-scoped shape answers a question nobody is asking. "How many sections did Class 5 have in
2026-27" is answered exactly as well by counting the sections that had enrolments that year — and
that answer is *better*, because a section that was created and never used should not be counted as
having run.

The costs of session-scoping are not small:

- **Every academic row doubles its key.** Timetable, subject allocation, attendance and marks all
  hang off a section. If a section is per-session, every one of them carries the session too, and
  every query joins one more table to find out which "Class 5 A" it means.
- **Rolling over a year becomes a copy.** Creating the next session would duplicate every class and
  section, and a school with 14 classes and 40 sections gains 54 rows a year that say nothing new.
  ADR-0011 names table *count* as the ceiling of schema-per-tenant; this is the row-count version of
  the same pressure, multiplied by every school.
- **Correcting a typo becomes a decision.** Renaming "Secton A" to "Section A" in the structural
  shape is one update. In the session-scoped shape it is a question: this year only, or every year?
  Someone has to answer that in a dialog, and they will answer it differently each time.

## What this gives up, honestly

**A section's history is not recoverable from the section.** If a school deletes Section C, nothing
in `section` remembers it existed. This is why sections are **deactivated, not deleted**: `active`
is on the table from the first migration rather than retrofitted once rows are referenced, because
by the time something references a section it is too late to decide that deleting it was wrong.

**A class that ran only in some years looks like it always ran.** A school that added Class 11 in
2027 has a Class 11 row with no start date. If that becomes a real question, the answer is a
`ran_in_session` table, not a rewrite of this one — additive, and only if asked for.

## Consequences

- `academic_session` moves from the `school` module into a new `academics` module. It is the time
  axis the academic model hangs off, and leaving it beside the school registry would make every
  academics query reach across a boundary for it. The table is unchanged; only the Java package
  moves.
- The table is `school_class`, not `class`. `class` is a Java keyword, so the entity has to be named
  something else regardless, and a table whose name does not match its entity is a small confusion
  repeated forever. Same reasoning that made `user` into `user_account`.
- `sequence` orders the ladder — Nursery before Class 1, Class 10 before Class 11 — and is unique so
  "which comes first" always has an answer. The constraint is `deferrable initially deferred`, so
  reordering two classes is one transaction rather than a dance through a temporary value.
- **No standard ladder is seeded.** Schools genuinely disagree: some start at Nursery, some at
  Class 1, some end at 10 and some at 12 (ADR-0006's test — a setting earns its place when two real
  schools disagree — is met). Seeding a guess means every school deletes rows on their first day.
  A "create the usual classes" convenience on the screen is worth adding once someone has set a
  school up by hand and found it tedious, and not before.
- Streams (Science, Commerce, Arts in Classes 11 and 12) are **not** in this decision. They attach
  to subject-eligibility rules, which do not exist yet, and guessing their shape now would be
  guessing about a model nobody has needed to write down.
