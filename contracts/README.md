# Contracts

The API contract shared by `backend/` and `frontend/`.

- `openapi.json` — generated from the running backend (`/v3/api-docs`). Committed so a contract
  change shows up as a reviewable diff.
- The frontend's typed client is generated from this file into `frontend/src/app/core/api/`.

## Not wired up yet

Today `frontend/src/app/core/api/models.ts` is hand-written and mirrors the backend records. The
generation step lands once the API surface stops churning; the intent is:

1. Backend build exports `/v3/api-docs` to `contracts/openapi.json`.
2. Frontend build generates the TypeScript client from it.
3. CI fails if regenerating produces a diff — so a backend change that breaks the frontend fails at
   build time, not in the browser.

Until then, `models.ts` mirrors the backend exactly and inventing a field is a review blocker.
