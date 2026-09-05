# API conventions

The generated reference lives at `/v3/api-docs` (JSON) and `/swagger-ui.html` while the backend is
running. The committed spec is `contracts/openapi.json`.

## Rules

- **Versioned prefix**: every endpoint is under `/api/v1/`. A breaking change means `/v2`, never a
  silent change to `/v1`.
- **Errors are RFC 9457 problem details**, produced centrally by
  `platform.error.GlobalExceptionHandler`:

  ```json
  {
    "type": "https://chalkbase.in/problems/validation",
    "title": "Validation failed",
    "status": 400,
    "errors": ["code: must not be blank"]
  }
  ```

- **Never accept a tenant id from the client.** No `?schoolId=`. The server resolves the tenant from
  the authenticated session — see [ADR-0002](../architecture/adr/0002-multi-tenancy-strategy.md).
- **Identifiers are UUIDs** in path segments; time is ISO-8601 UTC (`Instant`); money is a decimal
  string with an explicit currency, never a float.
- **Lists paginate** once a collection can exceed a few hundred rows: `?page=&size=&sort=`.

## Frontend client

Today `frontend/src/app/core/api/models.ts` mirrors the backend records by hand. Once the OpenAPI
spec is published to `contracts/`, that file is replaced by a generated client and editing it by hand
becomes a review blocker.
