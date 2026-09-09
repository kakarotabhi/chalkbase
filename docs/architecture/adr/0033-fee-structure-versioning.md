# ADR-0033: A fee structure is a versioned, immutable row — and a new session copies, never inherits silently

- Status: Accepted
- Date: 2026-09-09
- Deciders: Raja
- Related: [ADR-0012](0012-fee-ledger-model.md) (the ledger model this amends), [ADR-0006](0006-configurability-model.md)
  (Tier 2 session-scoped configuration), [ADR-0019](0019-classes-and-sections.md) (the "structural
  vs per-session" split this ADR's targeting decision borrows), [ADR-0030](0030-attendance-grain-and-lock.md)
  (the other append-only-after-lock model in this product, and a precedent for treating "locked" as
  a rule about time rather than a stored flag)

## Context

ADR-0012 rule 6 says a fee structure is session-scoped, so that "last year's fee structure renders
last year's receipts." It does not say how an edit within a session is represented, or what happens
to a class's structure when the school rolls into a new academic year. Two questions were open when
`fee` (Phase 2, structure-only) was built:

1. **What does "editing" a fee structure actually do to the row?** An editable "current version"
   row per (session, class) — update it in place, keep going — is the obvious design, and it is
   also exactly the shape ADR-0012 rejected for the ledger under the name "invoice with a status
   column": a design that looks like a working fee-setup screen until an auditor asks what was
   charged two sessions ago and the stored row has already been overwritten. The 08-phase-2-scope.md
   scope document names this trap directly: "versioning by session has to be there from the first
   structure ever created, not retrofitted once someone asks."
2. **What happens when a new session starts?** A school changes a handful of amounts and keeps the
   rest most years. Starting empty makes the office retype a class ladder's worth of fee heads,
   installments and due dates every April. Copying forward silently risks a class being charged
   last year's fees by accident, with nobody having decided that on purpose.

## Options considered — question 1: what an edit does

1. **One mutable row per (session, class), updated in place.** Simplest to build, and the exact
   option-1 shape ADR-0012's own "Options considered" section rejected for `fee_charge` —
   reappearing one layer up, in the structure that later feeds a charge, where nothing was watching
   for it. Rejected.
2. **A row per (session, class) that is replaced wholesale on each save, with no history kept.**
   Satisfies "session-scoped" for the _current_ session but still throws away what a class was
   charged before its structure was corrected mid-year, and gives a future `fee_charge` nothing
   stable to pin its "source structure version" to (ADR-0012's own diagram names this field).
   Rejected.
3. **A versioned, append-only row per (session, class, version).** Never updated once written;
   an edit supersedes the previous version and becomes the new one, in the same transaction.

## Decision — question 1

**Option 3.** `fee_structure` carries `version`, starting at 1, and `superseded_at`, null exactly
for the one row that is live for its `(academic_session_id, school_class_id)` pair — enforced by a
partial unique index, the same technique `academic_session.is_current` uses for "at most one" of
something. `FeeStructureService#save` always writes a brand new row with the next version number
and, if one existed, marks it superseded in the same transaction. There is no update path on
`FeeStructure`, `FeeStructureItem` or `FeeInstallment` at all — not merely a convention, there is no
method that would let one exist.

This is **stronger than ADR-0012's own letter**, which names `fee_charge` and `fee_ledger_entry` as
the append-only rows and says only that structures are session-scoped. Making the structure itself
version-immutable is this ADR's addition: it is what lets a later `fee_charge` reference "structure
version 3" and have that mean something concrete, and it is what makes "what did we charge two
sessions ago" answerable by reading a row rather than by trusting that nobody has since corrected it
in place.

**The lock, in force since a session has run its course.** Versioning alone would still let a school
rewrite a filed session's history indefinitely by adding version after version. So a _second_
version is refused once its session is no longer open: `FeeStructureService.isEditable` treats a
session as open while it is the school's current one, or has not started yet (`startsOn` in the
future — a school preparing next year's structure ahead of the rollover). A session that has started
and is not current can only be a past one, because "current" moves forward only in this build. The
very first version a class ever gets in a session is **never** subject to this check, on any
session — a school onboarding after a session has already ended can still record what it charged,
once, for the audit trail, which is a deliberate and narrow exception to the lock rather than a hole
in it.

This is expressed in terms of `startsOn` and `current` rather than an end date, because
`academics.api.AcademicSessionRef` — the only way this module may see a session — does not carry
`endsOn` today. Extending that cross-module contract for one field was weighed against expressing
the rule without it; the narrower change won, and is recorded here rather than silently in code so
that whoever eventually adds `endsOn` to the contract can revisit whether the rule should tighten to
use it.

## Options considered — question 2: what a new session starts with

1. **Start empty.** Honest, and the "type it all again" complaint 08-phase-2-scope.md predicts.
   Rejected as the default, kept as what naturally happens if nobody acts.
2. **Copy silently**, e.g. the instant a new session is created or made current. Risks a class being
   charged last year's amounts because nobody looked, which is a worse failure than retyping,
   because it is invisible until a parent is billed. Rejected.
3. **Copy as an explicit, named action, per pair of sessions, skipping any class that already has a
   structure in the destination.**

## Decision — question 2

**Option 3.** `POST /api/fees/structures/copy` takes `{fromSessionId, toSessionId}` and copies every
class's current structure from the source into the destination as that class's version 1 there,
**except** a class that already has a structure in the destination — which is left untouched, never
overwritten, and named back to the caller in `skippedClassNames` so the result is never silent about
what it did and did not do. Due dates shift by the gap between the two sessions' start dates (a term
due "10 days into the year" lands 10 days into the new one), rather than carrying a literal calendar
date forward, which would place a July due date before the new session had even started for a
session that runs on a different day of the year.

The screen offers this as a labelled button next to the ordinary "set up from scratch" path, and the
response is rendered as a sentence — how many copied, which classes were left alone and why — never
as a silent background effect of switching sessions or picking one in a dropdown.

## Consequences

**Easier.** "What did we charge two sessions ago" is a `select … where superseded_at is not null`
query answerable without an audit-log excavation. A future fee demand can pin a charge to an exact
structure version and have that survive every later correction to the same session's structure.
Rolling into a new session is optional retyping _or_ one confirmed action, never an ambiguous third
thing that might be either.

**Harder.** A class's fee structure accumulates versions over a session if the office corrects a
typo more than once; nothing here prunes them, on the same reasoning `attendance_mark`'s correction
history and the audit log itself are never pruned by anything but a deliberate retention job.
Reading "the current structure" always means "the one row with `superseded_at is null`", which is a
detail every future reader of this table (starting with fee demand) has to get right; the partial
unique index is what makes getting it wrong a constraint violation rather than a silent bug.

**To revisit.** When fee demand is built, `fee_charge.source structure version` (ADR-0012's own
name for the field) should reference `(fee_structure.id)` directly rather than a bare integer, now
that a concrete row exists to reference. If `academics.api.AcademicSessionRef` ever gains `endsOn`,
the lock rule in this ADR should be revisited to use it directly rather than inferring "has this
session run its course" from `startsOn` and `current`.
