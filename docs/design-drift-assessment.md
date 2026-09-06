# Design drift assessment

**Date:** 6 September 2026
**Designed:** `docs/artifacts/*.dc.html` (the six-screen canvas)
**Built:** https://chalkbase-web.onrender.com, signed in as `principal` / `DEMO-001`, plus
`frontend/src/` at `wt/design-drift`
**Screenshots:** [`docs/design-drift-shots/`](./design-drift-shots/) — mockup and app pairs, so you
can check the claims below rather than take them on trust.

---

## Verdict

The design system has not drifted. The product is small, and it looks small. The mockups were drawn
*from* `frontend/src/styles/_tokens.scss` — the artifacts README says so outright — so the greens,
the radii, the 44px targets and the 15px base all still match one-for-one, and there is not a single
raw hex value anywhere in the component styles. Of the six designed screens, **sign-in and the
school-profile form are near-pixel-faithful**; the students list is the one screen genuinely built
differently; the dashboard was never built at all. The nav is short because six of its nine modules
do not exist, and the nav is server-driven, so its length is a direct readout of what has shipped.

What is actually wrong is narrower than "largely drifted" but not nothing, and it is concentrated in
three places: **the empty, loading and error states** — four designed cards with an icon, a
headline and an action, built as a single line of grey text on a blank page; **the page gutter** —
32px designed, 16px built, which makes every screen sit oddly close to the sidebar; and **the active
nav item**, which is white in the app where the design has it green-tinted, so the sidebar reads as
having nothing selected. Those three, plus the missing dashboard, are almost certainly what produced
the impression. One thing outside the brief matters more than any of them: **the shell overflows
horizontally on any phone narrower than about 375px**, including the 360px width the whole
responsive design targets. That is a live bug, not drift, and it is a one-line fix.

Worth being blunt about one systemic thing: the shared component library stops at form controls.
`button`, `text-input`, `select`, `checkbox`, `form-field`, `dialog`, `bottom-sheet` all exist and
are faithful. **Badge, card, empty state and page header do not** — so a card surface is hand-rolled
in 13 SCSS files and a status badge in 6, each copy slightly different from the design and from each
other. That is the mechanism by which the remaining drift got in, and it is the thing to fix if you
want it to stop.

---

## Pile 1 — modules that do not exist yet

Not drift. The mockups depict the finished product; the app is at screen six of that product. Say
so and move on.

| Designed | Status |
|---|---|
| Dashboard (`Dashboard1280`, `Dashboard360`) | **No route exists.** `app.routes.ts` redirects `''` → `students`, with a comment explaining the landing screen was deliberately moved to Students. |
| Attendance, Fees, Exams, Communication, Transport, Reports | Not built. Nav is server-driven (ADR-0008): `NavigationStore` drops anything the server sends that has no route, so the sidebar showing three items *is* the honest state of the build. |
| Header global search ("Search students, staff, receipts…") | Cross-module by definition — it searches receipts and staff, neither of which exist. Pile 1 by dependency. |
| Header notification bell + unread dot | Depends on Communication. Pile 1 by dependency. |
| Students table **Fees** and **Attendance** columns | Blocked on the Fees and Attendance modules. Not drift — there is no data to put there. |
| User menu **My profile** and **Settings** | Deliberately shown `aria-disabled` rather than dropped, with a written rationale in `user-menu.html:48`. Correct call. |

Pairs: [`mock-dashboard-1280.png`](./design-drift-shots/mock-dashboard-1280.png) has no counterpart
at all. [`mock-students-1280.png`](./design-drift-shots/mock-students-1280.png) vs
[`app-students-1360.png`](./design-drift-shots/app-students-1360.png) shows the nine-item sidebar
against the three-item one.

**Do not build any of this in response to a drift review.** It is a roadmap question.

---

## Pile 2 — genuine drift: built, but built differently

### 2.1 The students table drops Guardian, and the data is right there

- **Designed:** Admission no · Student · Class · **Guardian** · **Fees** · **Attendance**
  (`Students1280.dc.html`)
- **Built:** Admission no. · Name · Class and section · Roll no. · Status
  (`frontend/src/app/features/students/student-list.html:150-160`)
- **Deliberate?** Fees and Attendance, yes — pile 1. **Guardian, no.** The data exists and is
  first-class: [`app-student-detail.png`](./design-drift-shots/app-student-detail.png) shows a
  Guardians panel with name, relation, phone, occupation and an explicit **Main contact** flag,
  which is exactly the field the column would render. There is also a whole `/students/guardians`
  screen. Nothing suggests a decision was made to leave it out of the list.
- **Cost note:** this is *not* a frontend-only change. `contracts/openapi.json` `StudentSummary`
  carries only `admissionNumber`, `currentEnrolment`, `fullName`, `gender`, `id`, `status` — there
  is no guardian field on the list payload. Adding the column means a backend DTO change, a contract
  regeneration and a frontend column. Size it accordingly.

### 2.2 Row identity: avatar and one line vs link and two lines

- **Designed:** a 30px circular initials avatar beside a plain-weight name; one line per row.
- **Built:** no avatar; the name is an underlined green link with the gender on a second line
  beneath it, and the class cell likewise carries the session on a second line.
- **Deliberate?** Half. The link is deliberate and correct — `student-list.scss` has a written
  rationale for the name being the only link so the row can hold a badge without everything becoming
  link text. The two-line stacking is not commented anywhere and is the direct cause of the row
  height change in §3.3.

### 2.3 No bulk selection

- **Designed:** a checkbox column, a live "1 selected" count, and contextual **Assign section** /
  **Remove** actions.
- **Built:** nothing. No checkbox column, no bulk actions.
- **Deliberate?** No evidence either way — no comment, no ADR. Reads as unbuilt scope rather than a
  decision. Note that "Assign section" as a bulk action is genuinely useful given the app already
  models enrolments per session.

### 2.4 Pagination reduced to Previous / Next

- **Designed:** numbered pages `‹ 1 2 3 … 72 ›` plus "1–20 of 1,428 students".
- **Built:** `Showing 1–25 of 60` with **Previous** and **Next** buttons
  (`student-list.html:214-222`).
- **Deliberate?** Probably — it is the cheaper correct thing, and it degrades gracefully. At 60
  demo students it is fine. At 1,428 real students (72 pages) "Next" alone is a bad experience. Flag
  for later, not now.

### 2.5 Filters: a labelled form grid where the design has an inline pill row

- **Designed:** one flat row — a 36px search box, then pill-shaped `Class IX` / `All sections` /
  `Fee status` dropdowns and a **More filters** affordance. No visible labels. Roughly 60px tall.
- **Built:** a three-column grid of `cb-form-field`s, each with a visible label above and a hint
  below (`student-list.scss:52-76`). Roughly 100px tall, plus the lede above it.
- **Deliberate?** Yes, and defensibly — visible labels beat placeholder-only pills for
  accessibility, and the grid is commented ("the search box is the control this screen is actually
  used through, so it gets the room"). But it costs real vertical space: on the deployed app the
  first student row starts at y≈425 where the mockup's starts at y≈315. **I would keep the built
  version and update the mockup**, not the reverse.

### 2.6 Page-header actions moved below the filters

- **Designed:** **Import** (secondary) and **Add student** (primary, with a `+`) sit top-right on
  the title row.
- **Built:** a `.toolbar` *below* the filter grid, with `Add a student` as a button and `Import from
  a spreadsheet` as an underlined text link (`student-list.html:96-106`).
- **Deliberate?** The demotion of Import is deliberate and commented ("typing one child in is the
  daily act, and importing six hundred happens once"). The *position* is not commented. The effect
  is that the primary action on the screen is the fourth thing down the page, and on a phone it is
  below the fold — see [`app-students-360.png`](./design-drift-shots/app-students-360.png).

### 2.7 The subtitle lost the count

- **Designed:** `1,428 enrolled · Session 2026–27` — a fact about the school.
- **Built:** *"Every student at this school. Search by name or admission number to find out which
  class and section a child is in."* — instructions.
- **Deliberate?** It is a house pattern, not an accident: every screen has one of these `.page-lede`
  paragraphs, and on `academics/classes` it runs to **seven lines** (see
  [`app-academics-classes.png`](./design-drift-shots/app-academics-classes.png)). But the count is
  already computed — the pager renders `Showing 1–25 of 60` from it. Losing it costs the one number
  a principal opens this screen to see.

### 2.8 Sign-in gained a School code field

- **Designed:** Username · Password, with the school named in the subtitle
  ("Greenfield Public School").
- **Built:** **School code** · Username · Password, subtitle "Use the details your school issued
  you."
- **Deliberate?** Unambiguously. The product is schema-per-tenant (ADR-0011); the school cannot be
  known before the code is entered. **The mockup is wrong, not the code.** Everything else on this
  screen matches to the pixel, down to the 40%/60% split — `auth-layout.scss:26` literally cites
  "512 of 1280 in the approved desktop mock". Compare
  [`mock-login-1280.png`](./design-drift-shots/mock-login-1280.png) and
  [`app-login-1360.png`](./design-drift-shots/app-login-1360.png); this is what a faithful build
  looks like.

### 2.9 Breadcrumbs added everywhere (drift in the other direction)

- **Designed:** breadcrumbs appear on `SchoolForm1280` only. `Shell1280` and `Students1280` have
  none.
- **Built:** every in-shell screen has a `.crumbs` line.
- **Deliberate?** Yes, and it is a fine extension. Two small faults came with it: the crumbs are
  plain muted text rather than links on most screens (only `student-detail` links the parent), and
  `student-detail.html:2-4` renders **"Students› Record"** with no space — Angular strips the
  inter-element whitespace because the crumb is an element rather than a text node. Visible in
  [`app-student-detail.png`](./design-drift-shots/app-student-detail.png).

---

## Pile 3 — visual and system drift

Everything here was measured: the mockup values by `getComputedStyle` on the `.dc.html` artboards
served locally, the built values by `getComputedStyle` on the deployed app at a 1360px viewport.

### 3.1 Measured comparison

| Property | Designed | Built | Verdict |
|---|---|---|---|
| Header height | 56px | 56px | ✅ |
| Header background | `#f6f8f9` | `#f6f8f9` | ✅ |
| Header horizontal padding | 20px | 16px | trivial |
| Sidebar width | 260px | 260px | ✅ |
| Sidebar padding / item height | 12px / 44px | 12px / 44px | ✅ |
| Nav item gap | 2px | 4px | trivial |
| **Nav item — active background** | **`#e6f1ee` (`--cb-primary-surface`)** | **`#ffffff` (`--cb-bg`)** | ❌ **accident** |
| Nav item — inactive weight | 500 | 400 | trivial |
| **Page content padding** | **28px 32px** | **24px 16px** | ❌ **accident** |
| `h1` | 24px / 600 | 24px / 600 | ✅ |
| Body font / size | Inter 15px | Inter 15px | ✅ |
| **Table header cell** | **12px / 600, `.02em` tracking, 38.5px tall** | **13px / 700, no tracking, 43.5px tall** | ❌ |
| Table header padding | 10px 12px | 12px 16px | minor |
| **Table row height** | **53px** | **67px** | ❌ **26% looser, not tighter** |
| Table cell padding | 11px 12px | 12px 16px | minor |
| Table container | 1px border, `radius-md`, **`--cb-shadow-1`** | 1px border, `radius-md`, **no shadow** | ❌ |
| **Status badge** | **`999px` pill, 12px, `3px 9px`** | **`12px` radius, 13px, `0 8px`** | ❌ |
| Focus ring | `0 0 0 3px rgba(31,95,79,.35)` | same token, `--cb-focus-ring` | ✅ |
| Input outline | `--cb-border-strong`, 44px min | identical (`_controls.scss`) | ✅ |
| Button | 44px min, `radius-sm`, 600, 15px | identical (`button.scss`) | ✅ |

### 3.2 The active nav item is white instead of green

`frontend/src/app/layout/main-layout/main-layout.scss:157`:

```scss
&.is-active {
  background: var(--cb-bg);   // #ffffff — designed value is --cb-primary-surface
  ...
}
```

The sidebar sits on `--cb-surface` (`#f6f8f9`), so a `--cb-bg` selection reads as a barely-there
white notch rather than the design's green-tinted pill. `--cb-primary-surface` exists, is documented
in `_tokens.scss` as being *for* "selected rows, active nav", and is used correctly in seven other
places. This looks like a typo, not a decision. Compare the two sidebars in
[`mock-students-1280.png`](./design-drift-shots/mock-students-1280.png) and
[`app-students-1360.png`](./design-drift-shots/app-students-1360.png).

### 3.3 Table density went the wrong way, and it is not "tighter"

Worth correcting the usual assumption: the built rows are **taller**, not tighter — 67px against a
designed 53px — because the name cell stacks the gender under the name and the class cell stacks the
session under the placement. The net effect is still less information on screen, because the design
fits **six** data columns into 53px where the build fits **five** into 67px. Roughly half the
information density, at 26% more vertical cost per row.

The header row is also weaker than designed: `student-list.scss:389-397` sets `font-size` and
`color` on `th` but never `font-weight`, so it inherits the browser's default `bold` (700) at 13px
instead of the designed 600 at 12px with `.02em` tracking. The result is a header row that competes
with the data instead of labelling it.

### 3.4 The page gutter is half the designed width

`main-layout.scss` `.shell__content` uses `padding: var(--cb-space-6) var(--cb-space-4)` — 24px
vertical, **16px horizontal**. The mockups all use `padding: 28px 32px`. At desktop this puts every
screen's content 16px from the sidebar edge, which is why the app reads as cramped against the chrome
even though the chrome itself is correct. One token change (`--cb-space-4` → `--cb-space-8` at
`from-expanded`) fixes it everywhere.

### 3.5 Empty, loading and error states are one line of grey text

This is the largest single visual gap and the most likely source of "the app looks unfinished".

`StudentsStates.dc.html` designs four distinct states, each a full bordered card with an icon tile, a
bold headline, an explanatory line and an action:

- *No students yet* → **Import from file** / **Add student**
- *No students match these filters* → **Clear filters**
- Loading → skeleton rows shaped like real rows, so nothing jumps when data lands
- *Could not load students* → the reason, a trace id, **Try again**

The build renders three of the four as a bare `<p class="page-status">`:

```html
<p class="page-status" role="status">Loading students…</p>
<p class="page-status" role="status">No student matches this search. Try part of a name, or clear the filters.</p>
```

Only the error case gets structure, as an inline `banner--danger` strip. There are no skeletons
anywhere in the app. Compare
[`mock-students-states.png`](./design-drift-shots/mock-students-states.png) with
[`app-students-noresults.png`](./design-drift-shots/app-students-noresults.png) — the same state, one
as a designed card, one as a sentence floating in 400px of white space.

The copy itself is good. It is the container that is missing.

### 3.6 No shared Badge, Card, EmptyState or PageHeader — so composites drift by construction

The primitives are faithful because they are components. The composites are not components, so they
are copies:

- **Card surface** (`background` + `1px --cb-border` + `radius-md`) is hand-written in **13** SCSS
  files. Exactly **one** of them (`school-profile.scss:50`) carries the designed
  `--cb-shadow-1`. Every other card in the app is flat where the design has depth.
- **Badge** is hand-written **6** times — `.status` in `student-list.scss:247` and
  `student-detail.scss:85` (verbatim copies, modifiers and all), `.badge` in `academic-sessions`,
  `student-enrolments` and `student-guardians`, `.chip` in `audit-log`. All six use
  `--cb-radius-lg` (12px). **All six are wrong the same way** — the design specifies a full `999px`
  pill at 12px. A consistent mistake, because it was copied.
- **Content width** has four different values across four screens: 46rem, 52rem, 54rem, 60rem —
  and the students table has **none**, so it stretches to 1053px at a 1360px viewport while the
  school-profile card beside it in the nav stops at 864px. Screens visibly fail to line up with
  each other.

None of this is visible in any single screenshot. It is visible when you click between screens, and
it is the reason drift will keep accumulating.

### 3.7 Two smaller things

- **User menu** matches the design closely (name, org, My profile, Settings, Sign out) except the
  design shows the user's **role** ("Principal") on the second line where the build shows the
  **school name** — redundant, since the school name is already in the header two inches to the
  left. See [`app-usermenu.png`](./design-drift-shots/app-usermenu.png).
- **Sign-in left panel:** the designed panel has a third element at the bottom ("Session 2026–27 ·
  CBSE") which holds the headline in the middle of the panel. The build has no footer, and
  `auth-layout.scss` uses `justify-content: space-between`, so the headline drops to the very bottom
  edge. Compare the two login screenshots — same components, different vertical rhythm.

### 3.8 Outside the brief: the shell overflows horizontally below ~375px

Not drift — a bug, and the most consequential thing in this document.

At a 360px viewport the whole app has a horizontal scrollbar, the school name runs full-bleed
instead of ellipsing, and the account menu is pushed off the right edge
([`app-students-360.png`](./design-drift-shots/app-students-360.png)). Measured on the deployed app:

```
documentElement.clientWidth  345
documentElement.scrollWidth  375     ← 30px of overflow
.shell grid column           374.83px
.shell__header  min-content  375px   ← the culprit
.shell__content min-content  225px   (fine)
.shell__school  min-content  259px, min-width: auto
```

`main-layout.scss` puts `min-width: 0` on `.shell__identity` with a comment saying exactly what it is
for — *"Without this the school name refuses to shrink and pushes the account menu off a 360px screen
instead of ellipsing."* That element is not the problem, and neither is `.shell__school`.

**The cause is one level further up, and it was found by testing the fix rather than reasoning about
it.** Three candidates were applied to the live DOM at a 360px viewport and measured:

| Candidate | `scrollWidth` | School name |
|---|---|---|
| baseline | 375 | 259px, not ellipsing |
| `min-width: 0` on `.shell__school` | **375 — no change** | 259px, not ellipsing |
| `min-width: 0` on `.shell__header` | 345 | 229px, ellipsing |
| `grid-template-columns: minmax(0, 1fr)` on `.shell` | 345 | 229px, ellipsing |

`.shell__school` already carries `overflow: hidden`, and per the flexbox spec an item with a
non-`visible` overflow has an automatic minimum size of **zero** — so it was always free to shrink.
Setting `min-width: 0` on it is a no-op.

What actually pins the width is that `.shell__header` is a **grid item** of `.shell`, and in the
compact layout `.shell` declares no `grid-template-columns` at all. The implicit column is sized
`auto`, which is floored at its items' min-content — and a grid item's own `min-width` defaults to
`auto`, so the header contributes its full 375px. The column is 374.83px wide inside a 345px
container. Nothing below the header ever feels any pressure to shrink, which is exactly why the
ellipsis never engages.

Two things make this worse than it looks. It scales with **school name length**, so it is data
dependent — the mockup's "Greenfield Public" is short enough to hide it, the demo tenant's "Chalkbase
Demo Public School" is not, and a real school with a longer name will be worse. And 375px CSS is not
an edge case: it covers 360px Android, the iPhone SE and the 12 mini. ADR-0010 names 360px as the
compact target; the compact target is broken.

The fix is `grid-template-columns: minmax(0, 1fr)` on `.shell`, applied to all three layouts.
Fixing the track rather than the header covers every grid item at once — the content track is
the next one that will overflow, the first time a wide table lands on it.

---

## What to actually fix, cheapest first

| # | Fix | Where | Size | Why |
|---|---|---|---|---|
| 1 | `minmax(0, 1fr)` grid tracks on `.shell` | `main-layout.scss` | **three lines — done, in this branch** | Stops the whole app scrolling sideways on every phone ≤375px. Highest value per character in this document. |
| 2 | Active nav `--cb-bg` → `--cb-primary-surface` | `main-layout.scss:157` | **one line** | The sidebar currently looks like nothing is selected. Token already exists for this purpose. |
| 3 | Content gutter 16px → 32px at `from-expanded` | `main-layout.scss` `.shell__content` | **one line** | Fixes the cramped feeling on every screen at once. |
| 4 | Add the missing space to the student-detail breadcrumb | `student-detail.html:2-4` | **one line** | "Students› Record" is visible on a shipped screen. |
| 5 | `th { font-weight: 600; font-size: 12px; letter-spacing: .02em }` | `student-list.scss:389` | **a few lines** | Header row stops competing with the data. |
| 6 | Extract a shared **Badge** component; migrate the 6 copies; pill radius `999px`, 12px | new `shared/components/badge/` | **an afternoon** | Fixes six wrong badges at once and stops a seventh. |
| 7 | Extract a shared **EmptyState** component (icon tile, headline, body, action) and use it for the three text-only states on Students | new `shared/components/empty-state/` + `student-list.html` | **an afternoon** | The single biggest visual gap. Copy already exists — only the container is missing. |
| 8 | One content-width token, applied in `.shell__content`; drop the four per-screen `max-width`s | `_tokens.scss` + 4 feature SCSS | **an afternoon** | Screens start lining up with each other. |
| 9 | `--cb-shadow-1` on the card surface — best done as a shared **Card** mixin or component and applied to the 13 sites | `styles/` + 13 files | **a day** | Do it *with* #8, not separately; the value is the consolidation, not the shadow. |
| 10 | Guardian (main contact) column on the students list | `StudentSummary` DTO + contract + `student-list` | **a day, backend included** | Real, useful data the design called for and the system already holds. Not frontend-only — see §2.1. |
| 11 | Loading skeletons shaped like rows | `student-list` (+ EmptyState work) | **a day** | Do it only after #7; on its own it is polish. |
| 12 | Move the primary action to the title row | `student-list.html` | **a day incl. the 360 layout** | Improves the phone experience meaningfully; needs care not to break the compact stack. |

**Not worth fixing:**

- **Numbered pagination** (§2.4). Previous/Next is correct and cheap; revisit when a tenant actually
  has 70 pages, not before.
- **The pill-shaped filter row** (§2.5). The built labelled form is more accessible and better
  commented. Change the mockup instead.
- **Bulk selection** (§2.3). It is a feature, not drift. Decide it on product value, not on the fact
  that a mockup has checkboxes in it.
- **Header padding 20 → 16px, nav gap 2 → 4px, cell padding 11/12 → 12/16px.** Sub-pixel-perfect
  chasing. Nobody will see it and it will regress.
- **The 30px row avatar** (§2.2). Decorative, costs a request or a colour-hash per row, and the
  design's own justification for it is thin. Skip unless you are also doing photos.

---

## Mockups that are now obsolete — update the design, not the code

The designs are older than the code and several were overtaken by better decisions. These should be
corrected in `docs/artifacts/` so the next person comparing them does not re-raise settled questions:

1. **`LoginDesktop.dc.html`, `Main.dc.html`, `LoginStates.dc.html` — add the School code field.**
   The product is schema-per-tenant (ADR-0011); the school genuinely cannot be known before the code
   is typed, so the mockups' "Greenfield Public School" subtitle is not reachable. Three artboards
   are wrong by one field.
2. **`Students1280.dc.html` / `Students360.dc.html` — replace the unlabelled filter pills with the
   built labelled form grid.** The build is the better pattern; the mockup is the one that should
   move.
3. **`Overlays.dc.html` — drop or heavily caveat the toast section.** `styles/_banner.scss` records
   an explicit decision against toasts: *"Not a toast: a banner belongs to the thing it is about and
   stays until that changes."* There is no toast component and there should not be one. The mockup
   still shows three.
4. **`Shell1280.dc.html` / `Shell360.dc.html` — mark the nine-item nav as aspirational.** The nav is
   server-driven; a static nine-item mockup will read as drift to every reviewer forever. A note on
   the artboard, or a second artboard showing today's three items, would stop the question recurring.
5. **`Components.dc.html` — add the primitives the build has that the design never covered:**
   the banner (four tones, used on every screen), the bottom sheet, and the disabled-menu-item
   pattern. Also add the empty state's *loading* variant as a real skeleton spec, since the design
   shows one but never specifies it.
6. **No artboard exists for Academic sessions, Classes and sections, Student detail, Guardians, or
   Student import** — five shipped screens with no design. They were built after the canvas (ADR-0019,
   ADR-0020, ADR-0021) and they follow the tokens well, but they invented conventions the canvas
   never ruled on: the multi-paragraph `page-lede`, the `Rename` / `Stop running` inline link-button
   pair, the highlighted "current" card. Those conventions are now load-bearing across the app and
   should be written down in `Components.dc.html` before a sixth screen invents a seventh variant.

---

## What was checked

Read: all 17 `.dc.html` artboards and `canvas.json`; `frontend/src/styles/` (`_tokens`, `_mixins`,
`_page`, `_controls`, `_banner`, `_reset`); `layout/main-layout`, `layout/user-menu`; all nine
`shared/components`; `features/students/student-list`, `student-detail`; `features/auth/login`,
`auth-layout`; `features/schools/school-profile`; `features/academics/school-classes`;
`app.routes.ts`; `contracts/openapi.json`.

Rendered and measured: all six primary artboards served over `http://127.0.0.1` (the `file:`
protocol is blocked to the browser tool); the deployed app signed in as `principal` at 1360px and
360px, across Students (populated, no-results), Student detail, School profile, Academics/Classes,
Sign-in and the user menu.

Not run: any build or test suite, per the brief. No application code was changed.
