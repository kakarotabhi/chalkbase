# Design artifacts

Source for the design canvases. Each `.dc.html` file is one artboard; `canvas.json` lays them out
into pages.

## First six screens

**Canvas:** https://claude.ai/code/artifact/8794f6d9-0732-445e-bbd1-9c9732411de3

Login, app shell, admin dashboard, school profile form, student list and the shared components —
each at 360 and 1280, with the loading, empty, error and disabled states rather than only the happy
path.

| Page | Artboards |
|---|---|
| Login & shell | login at 360 · four login states · login at 1280 · shell at 360 with the More sheet · rail at 700 · sidebar at 1280 |
| Dashboard, form & list | dashboard 360/1280 · school form 360/1280 · students 360/1280 · four list states |
| Components & overlays | buttons, fields, checkbox, badge, empty state, colour · dialogs, confirmations, toasts |

These are **static mockups**, not a clickable prototype. The brief asked for states, and states are
easier to compare side by side than hidden behind clicks.

Every value is lifted from [`frontend/src/styles/_tokens.scss`](../../frontend/src/styles/_tokens.scss)
— the same greens, spacing, radii and 44px targets the built shell already uses. Breakpoints are
[ADR-0010](../architecture/adr/0010-responsive-and-adaptive-layout.md)'s.

Student names, amounts and receipt numbers are invented. Nothing here is real school data.

## Editing

The `.dc.html` files and `canvas.json` are the source of truth; edit those. The published canvas is
rebuilt from them, so a change made in the canvas and one made here can diverge — pull the canvas
back down before editing if someone has saved into it.

The built canvas file is a ~2.7 MB single-file bundle and is **not committed** — it would be
rewritten wholesale on every change and bloat the history. Rebuild it from these sources when
needed.
