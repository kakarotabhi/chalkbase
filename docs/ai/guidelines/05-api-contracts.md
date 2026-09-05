# API Contract Guidelines

## API Style

Use REST APIs first.

Default:

- Base path: `/api/v1`.
- JSON request/response bodies.
- OpenAPI documentation.
- Consistent pagination, sorting, filtering, and error formats.
- Server-side authorization for every endpoint.

Do not add GraphQL or gRPC unless there is a clear product need.

## Resource Naming

Use plural resource names:

- `/api/v1/students`
- `/api/v1/admissions/applications`
- `/api/v1/attendance/daily-records`
- `/api/v1/fees/receipts`
- `/api/v1/exams/report-cards`
- `/api/v1/compliance/disclosures`

Use domain actions only when a simple CRUD model is misleading:

- `POST /api/v1/admissions/applications/{id}/approve`
- `POST /api/v1/attendance/daily-records/{id}/corrections`
- `POST /api/v1/fees/receipts/{id}/void`
- `POST /api/v1/exams/{id}/publish-results`
- `POST /api/v1/certificates/{id}/generate`

## Request Rules

- Never trust client-supplied calculated totals.
- Never trust client-supplied role, permission, school, or tenant values.
- Derive actor, tenant, and permission context server-side.
- Validate all input.
- Use idempotency keys for unsafe retryable actions.
- Use multipart upload only where needed.
- Keep request DTOs specific to the use case.

## Response Rules

- Return only fields the user is authorized to see.
- Mask sensitive values.
- Avoid returning large nested graphs.
- Use pagination for list endpoints.
- Include stable IDs and display labels needed by the UI.
- Include lifecycle status and lock status where important.

## Pagination

All list endpoints must be paginated.

Suggested shape:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

Use cursor pagination for very large append-only feeds if offset pagination becomes slow.

## Filtering and Sorting

Rules:

- Define allowed filter fields.
- Define allowed sort fields.
- Reject unknown filter/sort fields.
- Keep filters typed.
- Do not pass raw SQL fragments from clients.

Common filters:

- `academicSessionId`
- `campusId`
- `classId`
- `sectionId`
- `status`
- `dateFrom`
- `dateTo`
- `search`

## Error Format

Use a consistent error model.

Suggested shape:

```json
{
  "type": "https://example.com/problems/validation-error",
  "title": "Validation failed",
  "status": 422,
  "code": "VALIDATION_ERROR",
  "detail": "One or more fields are invalid.",
  "requestId": "req_123",
  "fieldErrors": [
    {
      "field": "student.dateOfBirth",
      "code": "DATE_OUT_OF_RANGE",
      "message": "Date of birth is outside the configured admission age range."
    }
  ]
}
```

Common status codes:

- `400`: malformed request.
- `401`: not authenticated.
- `403`: authenticated but not authorized.
- `404`: resource not found or not visible.
- `409`: conflict with current state.
- `422`: validation failed.
- `429`: rate limited.
- `500`: unexpected server error.

## Idempotency

Require idempotency keys for:

- Online payment confirmation.
- Payment gateway webhooks.
- Fee receipt generation.
- Admission conversion.
- Import commit.
- Certificate generation.
- Bulk actions.
- Message send batches.

Store:

- Idempotency key.
- Actor/school context.
- Request hash.
- Response summary.
- Status.
- Expiry.

Reject the same key if the request body differs.

## Versioning

- Use `/api/v1` from the start.
- Do not make breaking changes inside a version.
- Add fields in a backward-compatible way.
- Deprecate before removing.
- Keep frontend generated clients aligned with OpenAPI.

## OpenAPI

OpenAPI must be updated when:

- Endpoint is added/removed.
- Request/response shape changes.
- Error code changes.
- Auth requirement changes.
- File upload/download behavior changes.

Generated clients should be committed only if the project decides to commit generated code.

## Public APIs

Public endpoints must be minimal:

- Admission enquiry/application if enabled.
- Public disclosure pages.
- Certificate/report-card verification by token.
- Public notices.

Rules:

- Rate-limit public endpoints.
- Never reveal whether a private internal ID exists.
- Use opaque verification tokens.
- Do not expose student private data through public verification.

## File APIs

Upload rules:

- Enforce size limits.
- Enforce content type allowlist.
- Verify extension and detected type where possible.
- Store privately.
- Return file metadata, not storage paths.
- Scan files if scanner integration exists.

Download rules:

- Authorize every request.
- Use short-lived signed URLs or streamed downloads.
- Log sensitive file access.
- Watermark official documents where required.

## Webhooks

Webhook rules:

- Verify signatures.
- Store raw event metadata safely.
- Process idempotently.
- Acknowledge only after safe persistence.
- Retry safely.
- Separate provider adapter from domain processing.
- Alert on repeated failures.

## API Security Checklist

For every endpoint:

- Is the user authenticated if required?
- Is function-level authorization checked?
- Is object-level authorization checked?
- Is tenant/school/session scope enforced?
- Are sensitive fields masked?
- Is input validated?
- Is the action audited if sensitive?
- Is the endpoint rate-limited if public or abuse-prone?
- Are list endpoints paginated?
- Are exports logged?

