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
  Build them in `shared/components` and compose features from them.
- **No literal colours, spacings or font sizes in a component style** — only
  `var(--cb-*)` tokens. New token, new entry in `_tokens.scss`, and it must have a dark value.
- **Control flow uses `@if` / `@for` / `@let`**, never `*ngIf` / `*ngFor`.
- **Mobile first.** Fee counters and attendance run on low-end Android tablets; use the `from-tablet`
  / `from-desktop` mixins to layer desktop on top.
- **API models come from the backend contract.** Until the generated client lands, `core/api/models.ts`
  mirrors the backend records exactly — do not invent fields.
- **The response envelope is unwrapped in `core/api`,** not in components. Components receive plain
  payloads; error handling reads `error.code`, never `error.message` (ADR-0007).

## Tests

Vitest via `ng test`. Each feature gets a spec covering the rendered happy path and the error state,
using `provideHttpClientTesting`. Test what the user sees, not internal signals.

## Formatting

Prettier (2-space, 100 columns, single quotes) via the pre-commit hook and checked in CI.
Run `npx prettier --write <file>` rather than adjusting whitespace by hand.
