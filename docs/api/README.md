# API conventions

The generated reference is at `/v3/api-docs` and `/swagger-ui.html` while the backend runs. The
committed spec is `contracts/openapi.json`.

## Every response uses the same envelope

See [ADR-0007](../architecture/adr/0007-api-response-envelope.md).

```jsonc
// 201 Created
{
  "success": true,
  "data": { "id": "…", "code": "DPS-RKP", "name": "Delhi Public School, R. K. Puram" },
  "timestamp": "2026-09-05T10:22:31.004Z",
  "traceId": "7f1c…"
}

// 400 Bad Request
{
  "success": false,
  "error": {
    "code": "VAL_001",
    "message": "Some of the information provided is not valid",
    "details": { "code": "must not be blank", "name": "must not be blank" }
  },
  "timestamp": "2026-09-05T10:22:31.004Z",
  "traceId": "7f1c…"
}
```

Exactly one of `data` and `error` is present; the other is omitted.

## Rules

- **The HTTP status is truthful.** A failure is never `200 OK` with `success: false`. The envelope
  adds a code and a trace id on top of the status; it does not replace it.
- **Branch on `error.code`, never on `error.message`.** Codes are part of the contract; messages are
  wording and will change.
- **`traceId` is on every response** and in the `X-Request-Id` header. A client may send its own
  `X-Request-Id` and it will be echoed. Show it in user-facing error screens — it is what a school
  quotes when they report a problem.
- **The API is not versioned** (ADR-0016). Endpoints are `/api/schools`, with no version segment.
  A path describes the resource; it does not carry a version number nobody increments.
- **Evolve additively.** Add fields; do not remove or rename them. Both sides ignore fields they do
  not model, so an added field never breaks anyone. A genuinely breaking change is made atomically
  across backend and frontend in one pull request, because they ship together.
- **Never accept a tenant id from the client.** No `?schoolId=`; the server resolves the tenant from
  the session ([ADR-0011](../architecture/adr/0011-schema-per-tenant.md)).
- **UUID identifiers, ISO-8601 UTC timestamps, decimal strings for money** — never a float.

## Bootstrap: `GET /api/me`

One call after login returns everything the client needs to render the shell — user, school,
effective permissions, navigation and settings — instead of a waterfall of separate requests on a
school's broadband.

```jsonc
{ "success": true,
  "data": {
    "user": { "id": "…", "displayName": "…" },
    "school": { "id": "…", "name": "…" },
    "permissionsVersion": "3f7a1c9e04b2d5a8",
    "permissions": ["fee:invoice:create", "attendance:mark:write"],
    "navigation": [
      { "id": "fees", "labelKey": "nav.fees", "icon": "receipt", "order": 30,
        "children": [ { "id": "fees.collect", "labelKey": "nav.fees.collect", "order": 10 } ] }
    ]
  } }
```

Navigation carries stable **ids**, never URLs — the client maps ids to its own routes
([ADR-0008](../architecture/adr/0008-server-driven-navigation.md)). A `403` tells the client its
view is stale: refetch this endpoint before showing the error.

`permissionsVersion` is a **hash of the permission set, not a revision number.** It changes exactly
when the set changes and is identical for two users holding the same permissions, even at different
schools. Compare it for equality; never treat it as ordered or increasing.

## Error codes

Cross-cutting codes come from `PlatformErrorCode`; each module declares its own.

| Code | HTTP | Meaning |
|---|---|---|
| `VAL_001` | 400 | Validation failed. `details` carries field to reason. |
| `VAL_002` | 400 | Body missing or unparseable. |
| `VAL_003` | 413 | Upload too large. |
| `VAL_004` | 405 | Wrong HTTP method for this address. |
| `VAL_005` | 415 | Unsupported content type. |
| `AUTH_001` | 401 | Invalid username or password. |
| `AUTH_002` | 401 | Authentication required. |
| `PERM_001` | 403 | Authenticated, but not allowed. |
| `NF_001` | 404 | Resource not found. |
| `NF_002` | 404 | No such endpoint. |
| `CONF_001` | 409 | Conflicts with existing data. |
| `CONF_002` | 409 | Concurrent update; reload and retry. |
| `GEN_001` | 500 | Unexpected server failure. Quote the trace id. |
| `GEN_002` | 500 | A stored value could not be decrypted (ADR-0022) — the key is wrong or the row is corrupt. Quote the trace id. |
| `SCHOOL_001` | 409 | A school with this code already exists. |
| `SCHOOL_008` | 400 | That is not a time zone Chalkbase knows. |
| `ATT_001` | 422 | A student named in a mark is not actively enrolled in that section and session. |
| `ATT_002` | 400 | Attendance marked for a date after today. |
| `ATT_003` | 409 | This attendance date is locked (end of day plus 24 hours); file a correction request instead. |
| `ATT_004` | 400 | A correction request was filed for a mark still inside its own edit window — edit it directly instead. |
| `ATT_005` | 409 | A correction request for this mark is already awaiting a decision. |
| `ATT_006` | 409 | This correction request has already been decided. |
| `ATT_007` | 409 | This student already has a mark for that date (a concurrent-write race; the API's own upsert should not reach this). |
| `ATT_008` | 409 | This school has not set a current academic session yet. |
| `ADM_001` | 409 | A follow-up was logged against an enquiry already Converted or Lost. |
| `ADM_002` | 422 | The account named as counsellor is not an active user of this school. |
| `ADM_003` | 400 | A follow-up tried to set an enquiry's status back to New. |
| `ADM_004` | 400 | A follow-up left an enquiry open (New or In progress) with no next follow-up date. |

`AUTH_001` is returned for both a wrong password and an unknown user, deliberately — distinguishing
them turns the login form into a way to discover which parents are registered.

## Paging a list

Offset paging, settled in [Phase 0](../requirements/07-phase-0-decisions.md#api-conventions):

```
GET /api/students?page=0&size=25&sort=name,asc
```

The payload is a `PageResponse<T>` inside the usual envelope:

```json
{ "data": { "items": [], "page": 0, "size": 25, "totalElements": 613, "totalPages": 25 } }
```

Offset rather than cursor because every list here is a bounded admin table where the user wants
"page 7 of 12" and a total — and because "how many fee defaulters are there" is the question actually
being asked, which a cursor cannot answer. Cursor paging stays available for the two unbounded,
append-heavy logs (audit and notification delivery) if they ever need it.

`size` is capped server-side. An unbounded list endpoint is a review blocker.

## Adding an endpoint

1. Return `ApiResponse<T>` (`ApiResponse.success(payload)`). Do not wrap automatically — explicit
   wrapping keeps the generated OpenAPI schema honest.
2. Throw `ChalkbaseException` with a module error code for business failures. Do **not** throw
   `IllegalArgumentException` to signal a bad request: the JDK throws it for real bugs, and mapping
   it to 400 would hide them.
3. If the change adds a unique or check constraint, add a `ConstraintMapping` in the same commit, or
   violations surface as a generic conflict instead of a useful sentence.

## Exports, and the other endpoints outside the envelope

Two shapes of response are deliberately not `ApiResponse<T>`: a document's bytes
(`GET /api/documents/{id}/content`) and a CSV export (`GET /api/students/export`,
`GET /api/students/export/unmasked`, `GET /api/guardians/export`). Both answer with the file
itself — a browser needs the raw bytes and a `Content-Disposition: attachment` header, not a JSON
envelope wrapped around them — and both are still exactly as much JSON as everywhere else when they
fail: a 403 or a 404 from either controller is the ordinary envelope.

An export additionally:

- **Streams, on the request thread.** The controller writes straight into
  `HttpServletResponse#getOutputStream()` as the file is built, row by row — never assembled as a
  `String` or a `byte[]` first, and deliberately not `ResponseEntity<StreamingResponseBody>`, whose
  async dispatch thread does not carry the tenant schema `SessionTenantFilter` bound to the request
  thread. See [ADR-0027](../architecture/adr/0027-export-masking.md) §5.
- **Masks by `@Classification`, not by a column list.** `platform.export.ClassificationCsvExporter`
  reads each exported row's own `@Classification` and omits every `RESTRICTED` column from the
  default (masked) file entirely — no empty cell, no marker. `CONFIDENTIAL` and `INTERNAL` columns
  are never masked (ADR-0027 §§1–2).
- **Is audited unconditionally.** Every export — masked included — writes
  `AuditAction.DATA_EXPORTED` naming the columns actually in the file and the row count, never a
  value (ADR-0027 §3).
- **Needs a permission of its own to go unmasked.** `student:student:export_unmasked`
  (`StudentPermissions`) is held by no shipped role template; `PERM_001` is what a caller without it
  gets from `/export/unmasked`, the same code every other authorization failure uses.

## Frontend client

`frontend/src/app/core/api` unwraps the envelope, so components receive plain payloads. Until the
generated client lands, `core/api/models.ts` mirrors the backend records by hand and inventing a
field there is a review blocker.
