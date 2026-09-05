# ADR-0009: Hand-built components, Angular CDK for behaviour

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0006](0006-configurability-model.md)

## Context

Chalkbase builds its own presentational components — button, input, select, dialog, table — rather
than adopting Angular Material, PrimeNG, CoreUI or a Tailwind component kit. Every pixel is designed
by hand and lives in `frontend/src/app/shared/components`.

The reason is ordinary: a school ERP is looked at all day by the same people, and looking like a
default component kit is a real disadvantage in a market where the incumbents all look identical.
Design tokens (`frontend/src/styles/_tokens.scss`) already exist for exactly this.

The risk is equally ordinary, and worth naming: **the hard part of a component library is never the
button.** It is the select with keyboard navigation and typeahead over 2,000 students, the dialog
that traps focus and restores it on close, the overlay that flips when it would fall off a small
screen, the table that stays usable at 1,000 rows on a ₹8,000 tablet. Teams that hand-build these
usually rebuild them badly, discover the accessibility gap late, and lose more time than the
component library would have cost.

## Decision

**Build the components. Do not build the behaviour underneath them.**

- **No UI component library.** No Angular Material, PrimeNG, CoreUI, Bootstrap, or Tailwind UI kit.
  Presentation is ours.
- **Angular CDK is adopted**, and is not a contradiction. The CDK is behaviour, not appearance: 
  `Overlay` (positioning, flip, scroll strategies), `A11y` (focus trap, live announcer, focus
  monitor), `Portal`, `ScrollingModule` (virtual scroll), `DragDrop`, `Clipboard`. It ships no CSS
  and imposes no visual design.

  Writing our own focus trap and overlay positioning would be rebuilding, worse, the one part of a
  component library that is genuinely hard and genuinely security- and accessibility-relevant.

- **No raw values in component styles.** Only `var(--cb-*)` tokens, and every new token needs a dark
  value (already the rule in `frontend/AGENTS.md`).

### Conventions for a shared component

- Selector prefixed `cb-`, standalone, `ChangeDetectionStrategy.OnPush`, signal inputs.
- **No HTTP, no router, no store.** A shared component takes inputs and emits outputs. The moment
  one fetches data it stops being reusable and starts being a feature.
- Anything usable in a form implements `ControlValueAccessor`, so it works with reactive forms
  rather than needing a wrapper.
- Keyboard and screen-reader support is part of "done", not a later pass. Accessibility is where
  hand-built components quietly fail, and Indian government and CBSE tenders increasingly ask about
  it.
- Mobile first. Fee counters and attendance run on low-end Android tablets; a component that is only
  comfortable with a mouse is not finished.

### Build order

Components are built when a feature needs them, not speculatively up front. Expected order, roughly
matching the roadmap: button → form field wrapper → text input → select → checkbox/radio → dialog →
toast → data table → date picker (Indian formats and academic session ranges) → file upload.

The date picker and the data table are the two that will take much longer than expected. Budget for
them rather than discovering them.

### Documentation

A `/dev/components` gallery route, available in development builds only, showing every component in
its states. Deliberately not Storybook to begin with: another toolchain, another build, another
thing to keep green. If the team grows past a couple of people, revisit.

## Consequences

- Feature work is blocked on component work early on. The first few screens will feel slow, and that
  is the cost being accepted knowingly.
- Accessibility and keyboard support have to be tested, because nothing else will catch their
  absence. Each shared component needs a spec covering keyboard interaction, not just rendering.
- If a component turns out to need weeks — a rich text editor, a full calendar, a spreadsheet grid —
  the right answer is a focused single-purpose library for that one thing, not abandoning this
  decision or hand-rolling it. Adding a dependency needs a decision either way (root `AGENTS.md`).
