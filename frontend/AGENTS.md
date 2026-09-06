# Frontend — agent instructions

Angular 22 · TypeScript 6 · standalone components · signals · zoneless · SCSS · Vitest.
Read with the root `AGENTS.md`.

## Layout

```
src/app/
├── core/         Singletons. api/ (HTTP services + models), interceptors/, config/. Injected, never
│                 imported into templates.
├── shared/       Reusable presentational components, directives, pipes. No HTTP, no router state.
├── layout/       App shell: header, navigation, routed outlet.
└── features/     One folder per backend module: schools/, admissions/, fees/, attendance/…
                  Lazy-loaded from app.routes.ts.
src/styles/       _tokens.scss (design tokens), _reset.scss, _mixins.scss.
src/environments/ apiBaseUrl per build configuration.
```

## Rules

- **Standalone components only**, `ChangeDetectionStrategy.OnPush`, `inject()` over constructor
  params. No NgModules.
- **Signals for component state.** `toSignal` at the edge of an Observable; keep RxJS in `core/api`.
- **Components never call `HttpClient`.** Go through a service in `core/api`.
- **Selectors are `cb-` prefixed.** Files are named after the thing (`school-list.ts`), no
  `.component` suffix.
- **No UI component library, and no utility CSS framework.** Components are hand-designed here.
  Build them in `shared/components` and compose features from them (ADR-0009).
- **Angular CDK is allowed and preferred for behaviour** — `Overlay`, `A11y` focus trap, `Portal`,
  virtual scroll. It ships no CSS. Do not hand-roll focus trapping or overlay positioning.
- **Shared components take inputs and emit outputs.** No HTTP, no router, no store. Form controls
  implement `ControlValueAccessor`. Keyboard and screen-reader support is part of "done".
- **Navigation comes from the server** (ADR-0008). The menu is built from `/api/me`, and the
  server sends stable ids that this app maps to its own routes — never URLs. An unknown id is
  dropped and logged, never rendered. Never treat a hidden menu item as access control.
- **No literal colours, spacings or font sizes in a component style** — only
  `var(--cb-*)` tokens. New token, new entry in `_tokens.scss`, and it must have a dark value.
- **Control flow uses `@if` / `@for` / `@let`**, never `*ngIf` / `*ngFor`.
- **Mobile first, and it is not optional** (ADR-0010). Write the compact layout as the default and
  layer larger ones with `from-medium` (600px) / `from-expanded` (840px). `compact-only` exists but
  use it sparingly.
- **`100dvh`, never `100vh`.** Fixed bottom chrome pads by `var(--cb-safe-bottom)`, and content pads
  so nothing hides behind it.
- **Tap targets are at least `var(--cb-touch-target-min)`** (44px).
- **The page never scrolls horizontally.** Tables become cards, expandable rows, or scroll inside
  their own container — decided per screen by task, not `overflow-x: auto` everywhere.
- **Render navigation once** and move it with CSS. Three copies means three landmarks for screen
  readers and triple the DOM on the cheapest devices.
- **API models come from the backend contract, and are now generated.** `core/api/models.ts` is a
  list of aliases onto `contracts/api-types.ts`, which `npm run contracts:types` generates from
  `contracts/openapi.json`, which the backend build exports. **Never hand-write a shape there** —
  an alias is a claim the compiler checks. What that file still owns is the prose explaining *why*
  a field is what it is; keep it, and put a field-level note under the type it belongs to. A field
  the backend does not send has no business in this app: if you need one, it goes in the backend
  first. CI fails on a `contracts/` diff, so regenerate after a backend change and commit both
  files.
- **The response envelope is unwrapped in `core/api`,** not in components. Components receive plain
  payloads; error handling reads `error.code`, never `error.message` (ADR-0007).
  **Adding a field, a record or an endpoint has a recipe** — see
  [`contracts/README.md`](../contracts/README.md), "What to do when you change the API". The
  nullable case is the one with a trap in it.

## Tests

Vitest via `ng test`. Each feature gets a spec covering the rendered happy path and the error state,
using `provideHttpClientTesting`. Test what the user sees, not internal signals.

## Formatting

Prettier (2-space, 100 columns, single quotes) via the pre-commit hook and checked in CI.
Run `npx prettier --write <file>` rather than adjusting whitespace by hand.
