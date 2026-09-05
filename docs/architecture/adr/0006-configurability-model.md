# ADR-0006: How configurable Chalkbase is, and where

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja

## Context

Indian schools differ in ways that matter: boards, session dates, grading scales, fee cycles,
attendance styles, promotion rules, house systems, working days. A product that hardcodes one
school's habits cannot sell to the next one. The stated goal is therefore "configurable as much as
we can".

That goal needs a boundary, because configurability is not free. Every toggle is a branch in the
code, a row in the test matrix, a question during onboarding and a hazard during migration. A system
with 200 independent settings has more states than can ever be tested, and its bugs arrive as "only
at this one school". Products die of this at least as often as they die of being too rigid.

So the question is not *whether* to be configurable, but **what kind of configurable, where**.

## Decision

Four tiers. Each has a different mechanism and a different cost, and a change belongs in the
cheapest tier that fits.

### Tier 1 — Master data. Always configurable, because it is only data.

Classes, sections, subjects, fee heads, designations, departments, houses, grading scales,
categories, document types, boards. Already FR-009 and FR-010.

These are rows a school edits, with no branching in code. **When something can be modelled as master
data instead of a setting, model it as master data** — it is the tier with no combinatorial cost.

### Tier 2 — Typed per-school settings. The default answer for behaviour.

Session start and end dates, attendance mode (daily or period-wise), working days, week start,
receipt numbering format, late-fee rule, result-publishing visibility, currency and date display.

Mechanism — and this part matters for scale:

- Each module **declares** its settings in code: key, type, default, validation, description, and
  whether it varies per academic session.
- Values are stored per school as typed key/value rows, read through one `SettingsService`.
- **Not** a `school_settings` table with a column per setting. That table becomes 200 columns wide,
  needs a migration for every new option, and every module ends up depending on one table.

Two constraints:

- **Every setting has a default that makes the product work.** A school must be usable before anyone
  opens the settings screen. Onboarding cannot begin with 200 questions.
- **Session-scoped where it matters.** Fee rules, grading scales and promotion criteria change
  between academic sessions, and last year's results must still render under last year's rules. A
  setting that a school will change year to year is versioned by session from the start; retrofitting
  that is a data migration over historical records.

### Tier 3 — Named strategies with parameters. For genuine workflow differences.

Approval chains, grading formulas (percentage, CGPA, grade bands), promotion criteria, fee
proration, attendance percentage calculation.

Here schools differ structurally, not just by value. The mechanism is a **small set of named
strategies implemented in code, each taking parameters** — not a rules engine and not scripting.
`GradingStrategy = PERCENTAGE | CGPA_10 | GRADE_BANDS(bands…)`, chosen per school and per session.

A rules engine or embedded scripting is rejected: it is unbounded configurability with the debugging
cost pushed onto whoever answers the support call, and it makes correctness untestable. If a school
needs a strategy that does not exist, that is a product decision — write the strategy.

### Tier 4 — Not configurable, on purpose.

- Anything a regulator defines: UDISE+ field formats, APAAR handling, statutory report structure.
- Anything that changes data integrity: audit logging, tenant isolation, permission enforcement.
- Module boundaries and the API contract.

Making these configurable does not serve schools; it just means the compliance bug is now the
school's fault.

## The rule for adding a setting

> **A setting earns its place when two real schools disagree about it.**

Until then, pick the sensible default and hardcode it. Speculative configurability is the expensive
kind: it is designed without a real second opinion to design against, and it is usually wrong in a
way that is discovered only after someone depends on it.

The corollary: it is far cheaper to turn a constant into a setting later than to remove a setting
that schools have already configured.

## Consequences

- Every module ships a settings registry alongside its permission registry, and both are part of
  what "adding a module" means.
- The settings screen is generated from declared metadata rather than hand-built per module, so a
  new setting costs a declaration, not a UI change.
- Test fixtures must not assume defaults. Any behaviour driven by a Tier 2 or Tier 3 setting needs
  tests at more than one value, or the non-default configuration is untested.
- Support needs a per-school "effective configuration" view — settings, active strategies and role
  definitions in one place — or diagnosing a school-specific report becomes archaeology.
