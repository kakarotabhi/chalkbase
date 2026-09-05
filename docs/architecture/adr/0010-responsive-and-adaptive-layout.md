# ADR-0010: Responsive and adaptive layout

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0008](0008-server-driven-navigation.md) (navigation), [ADR-0009](0009-hand-built-component-library.md) (components)

## Context

India is one of the most mobile-dominant markets in the world: measurements put mobile at roughly
65-80% of web traffic depending on methodology. For a school product the split is even sharper by
audience, and that is what makes "make it responsive" the wrong framing — it is three problems, not
one:

| Audience | Device reality | Menu size |
|---|---|---|
| Parents, students | Phone, almost exclusively. Often a cheap Android on a slow connection. | 3-5 destinations |
| Teachers | Phone and tablet in the classroom for attendance and marks; desktop occasionally. | 5-8 |
| Admin, accounts, principal | Desktop for fee counters, reports and bulk entry; a tablet at the counter. | 10-16 |

A single responsive rule cannot serve all three. The usual shortcut — collapse the sidebar into a
hamburger below some width — is acceptable for an administrator who is mostly on a desktop anyway,
and poor for a parent, for whom the phone *is* the product.

## The bottom-bar question

The proposal was a bottom bar on mobile, expanding upward. That is the right instinct, and it needs
one correction.

Material's guidance is that a bottom navigation bar carries **three to five destinations**; below
three use tabs, above five use a drawer, because a sixth item makes every target too narrow to hit.

Chalkbase has sixteen modules. A principal can legitimately see a dozen top-level items. A bottom
bar cannot hold that, so "use a bottom bar on mobile" is right for parents and wrong for admins.

The resolution comes free from [ADR-0008](0008-server-driven-navigation.md): navigation is served
per user and already filtered by permission, so **the client knows how many top-level items this
particular user has** and can adapt to the count rather than guess from the device.

## Decision

### Breakpoints follow the platform, not invented numbers

Window size classes, matching Android's own boundaries — most of these users are on Android, so the
layout should change where their devices actually change:

| Class | Width | Navigation | Shape |
|---|---|---|---|
| compact | < 600px | Bottom bar | Header on top, content, nav fixed at the bottom |
| medium | ≥ 600px | Icon rail (80px) | Header on top, rail on the left |
| expanded | ≥ 840px | Sidebar (260px) | Header on top, labelled sidebar on the left — the desktop layout |

Defined once in `frontend/src/styles/_mixins.scss` as `from-medium`, `from-expanded` and
`compact-only`.

### Compact navigation adapts to the item count

Driven by what the server returned, not by a guess:

- **≤ 5 top-level items** → all of them in the bottom bar.
- **> 5** → the first four by `order`, plus a **More** item opening a **bottom sheet** with the full
  tree. This is the "expandable from the bottom" behaviour, applied where it is actually needed.

So a parent gets a clean four-item bar; a principal gets four plus More. Same code, same contract.

### One navigation element, three arrangements

The menu is rendered **once** and moved by CSS — not rendered three times with two hidden. Three
copies would give screen readers three navigation landmarks and triple the DOM on the cheapest
devices, which are exactly the devices that cannot afford it.

In the rail, labels are visually hidden rather than removed, so links are still announced by name.

### Rules that will otherwise bite

1. **`100vh` is wrong on mobile.** It is the viewport with browser chrome hidden, so the bottom of
   the page sits under the address bar. The shell uses `100dvh`; fixed bottom chrome is sized so it
   is never clipped. These units are baseline-available and safe to use now.
2. **`env(safe-area-inset-bottom)`** on the bottom bar, with `viewport-fit=cover` in the viewport
   meta, or the bar sits under the home indicator on gesture-navigation phones.
3. **Content must clear fixed chrome.** The content area pads by nav height + safe area. A "why is
   the last row unreachable" bug is otherwise guaranteed.
4. **Touch targets: 44px.** WCAG 2.2 SC 2.5.8 (AA) requires 24×24 CSS px; SC 2.5.5 (AAA) and both
   platform conventions say 44. Given teachers marking attendance and clerks at a fee counter
   tapping at speed, 44 is the working default (`--cb-touch-target-min`), not the floor.
5. **The page never scrolls horizontally.** Wide content scrolls inside its own container.

### Data tables are the real problem

A fee ledger, a student list and a marks grid are the heart of this product, and tables do not fit a
phone. Three sanctioned patterns, chosen per screen by task:

- **Card list** — default for browsing. Each row becomes a card with two to four priority fields,
  the rest on the detail screen.
- **Priority columns with row expansion** — when the user is scanning for one value across rows.
- **Horizontal scroll with a frozen first column** — last resort, only where the task is genuinely
  tabular comparison, such as a marks grid. The container scrolls, never the page.

Deciding this per screen is the point. A table that is merely `overflow-x: auto` everywhere is how
mobile ends up technically working and practically unusable.

### Forms

Single column on compact, labels above fields, and a correct `inputmode` for admission numbers,
amounts and phone numbers. A fee counter operator typing amounts on an alphabetic keyboard is a
small detail that costs real minutes every day.

Dialogs become full-screen on compact; confirmations and pickers become bottom sheets.

### Some things are desktop-first, and that is stated rather than discovered

Timetable construction, bulk import, report-card template design and multi-column financial reports
are desktop tasks. They will be built desktop-first and are **listed as such**, with a clear message
on small screens rather than a broken layout.

The line: anything a parent, student or teacher does routinely must work fully on a phone.
Administrative bulk work may be desktop-first, by explicit decision, never by accident.

### Performance is part of responsiveness

A layout that reflows correctly but takes eight seconds on a ₹8,000 tablet is not responsive. Lists
over ~100 rows use virtual scroll (CDK, ADR-0009); heavy shadows, blurs and large images are
avoided; routes stay lazy.

### Test matrix

Every feature is checked at **360×640** (still the most common low-end Android), **390×844**
(typical phone), **768×1024** (tablet portrait), and **1280×800**. The fee-counter tablet is a real
device and should be tested on, not emulated only.

## Consequences

- Every feature PR states how the screen behaves at compact width. "Mobile later" is how mobile
  never happens.
- Compact is the default in CSS and the larger layouts are layered on, so a new screen is
  mobile-correct before anyone opens a desktop browser.
- Each shared component's spec includes a narrow-viewport assertion.
- The bottom sheet and the More behaviour land with the identity module, since they need real
  server-driven navigation to be meaningful.

## Sources

- [Material Design 3 — Navigation bar guidelines](https://m3.material.io/components/navigation-bar/guidelines) (3-5 destinations)
- [Android — Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes) (600dp / 840dp; bottom bar, rail, drawer)
- [W3C — Target Size (Minimum), WCAG 2.2 SC 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) and [SC 2.5.5 Target Size (Enhanced)](https://www.w3.org/WAI/WCAG21/Understanding/target-size.html)
- [The Large, Small, and Dynamic Viewports](https://www.bram.us/2021/07/08/the-large-small-and-dynamic-viewports/) (`dvh`/`svh`/`lvh`)
- [Mobile vs desktop market share, India](https://kinsta.com/mobile-vs-desktop-market-share/)
