# Backend Guidelines: Spring Boot

## Backend Direction

Build the backend as a Spring Boot modular monolith.

Recommended defaults:

- Java LTS.
- Spring Boot.
- Spring MVC for REST APIs unless reactive requirements are proven.
- Spring Security.
- Spring Data JPA where suitable.
- Flyway or Liquibase for migrations.
- PostgreSQL as the primary database.
- OpenAPI for API documentation.
- Testcontainers for integration tests.

Do not split into microservices at the start.

## Package Organization

Prefer feature/module packages over technical layers at the top level.

Example:

```text
com.yourorg.school
  platform
  identity
  organization
  admissions
  student
  academics
  attendance
  timetable
  exams
  fees
  communication
  documents
  compliance
  hr
  transport
  hostel
  library
  inventory
  safety
  reporting
  integrations
```

Inside each module, use consistent internal structure:

```text
student
  api
  application
  domain
  infrastructure
```

Use this only where the module is large enough to benefit from it. Do not create empty architecture folders.

## Controller Rules

Controllers should:

- Define HTTP contract.
- Validate request DTOs.
- Call application services.
- Return response DTOs.
- Avoid business rules.
- Avoid direct repository access.
- Avoid returning entities.

Use request and response objects for public APIs. Do not bind web requests directly to JPA entities.

## Service Rules

Application services should:

- Own use cases.
- Enforce authorization-sensitive business checks.
- Start and control transactions.
- Coordinate repositories and module services.
- Emit domain events or audit events.
- Return DTOs or domain results.

Keep domain calculations testable without requiring Spring where possible.

## Transaction Rules

- Put `@Transactional` on service methods, not controllers.
- Keep transactions as short as possible.
- Do not perform slow external calls inside database transactions.
- Use `readOnly = true` for query-only service methods.
- Use optimistic locking for concurrently edited aggregates.
- Use explicit lock or unique constraint strategies for numbering, receipts, and seats.
- Use idempotency tables for webhooks and retryable commands.

## DTO and Mapping Rules

- Separate create, update, search, and response DTOs.
- Never trust client-provided tenant, school, session, role, payment status, or calculated amounts.
- Derive sensitive values server-side.
- Use MapStruct or explicit mappers for non-trivial mappings.
- Keep mapping logic out of controllers.
- Do not expose internal IDs where a public verification token is required.

## Validation Rules

- Use Jakarta Bean Validation on DTOs.
- Use custom validators for domain-specific checks.
- Validate cross-field and cross-record business rules in services.
- Return structured validation errors with field paths.
- Validate uploads for file type, size, and allowed business context.
- Validate imports in dry-run mode before writing.

Examples of domain validation:

- Student date of birth within configured admission rules.
- Roll number unique within class-section-session.
- Fee concession does not exceed configured limit without approval.
- Receipt amount matches fee transaction lines.
- Marks are within configured max marks.
- Attendance cannot be edited after lock without correction workflow.

## Exception Handling

Use centralized exception handling.

Recommended:

- Domain exceptions for expected business failures.
- Validation exception mapping.
- Not found exception mapping.
- Authorization failure mapping.
- Conflict exception mapping.
- RFC 9457-style problem details if supported by the selected Spring version.

Error responses should include:

- Machine-readable code.
- Human-readable message.
- Field errors if applicable.
- Request ID.

Do not expose stack traces to clients.

## Security Rules

- Use Spring Security for authentication and authorization.
- Apply request-level authorization and service/domain-level checks for sensitive operations.
- Use method security where it keeps rules close to use cases.
- Default deny unknown routes.
- Verify object-level authorization for every record access.
- Never rely only on frontend route guards.
- Rate-limit login, OTP, payment, upload, and public endpoints.
- Secure actuator endpoints.

## Query Rules

- Scope all business queries by tenant/school.
- Scope academic data by academic session unless deliberately cross-session.
- Use projections for list views.
- Avoid loading large graphs through JPA entity relationships.
- Use fetch joins or entity graphs only when measured and appropriate.
- Prefer explicit queries for reporting.
- Paginate list endpoints.

## Entity Rules

- Entities should represent persisted domain state.
- Avoid exposing setters for fields that require business rules.
- Keep bidirectional relationships limited.
- Avoid cascade remove on important records.
- Use enums carefully. If values are school-configurable, use tables instead.
- Use `created_at`, `updated_at`, and actor fields where appropriate.
- Use version columns for optimistic locking.

## Domain Events

Useful events:

- ApplicantSubmitted.
- ApplicantAdmitted.
- StudentPromoted.
- StudentTransferred.
- AttendanceMarked.
- StudentAbsent.
- FeeDemandGenerated.
- FeePaymentReceived.
- ReceiptGenerated.
- MarksSubmitted.
- ResultPublished.
- CertificateGenerated.
- ConsentWithdrawn.
- ComplianceDocumentExpiring.

Start with in-process events plus an outbox table when external side effects are involved.

## Background Jobs

Use jobs for:

- Fee demand generation.
- Reminder scheduling.
- Message retries.
- Import processing.
- Report generation.
- Backup checks.
- Certificate expiry reminders.
- Payment reconciliation.

Jobs must be:

- Idempotent.
- Observable.
- Retry-safe.
- Audited when they change business data.

## Integration Rules

External integrations should use adapter interfaces:

- PaymentGatewayClient.
- SmsProviderClient.
- WhatsAppProviderClient.
- EmailProviderClient.
- ObjectStorageClient.
- GpsProviderClient.
- BiometricProviderClient.
- AccountingExportClient.

Do not spread provider-specific payloads across business services.

## Actuator and Health

- Expose only safe health endpoints publicly.
- Secure detailed actuator endpoints.
- Add readiness checks for database, Redis, object storage, and critical queues.
- Do not make liveness depend on external providers like SMS or payment gateways.

## Official References

- Spring Boot reference: <https://docs.spring.io/spring-boot/reference/>
- Spring Framework reference: <https://docs.spring.io/spring-framework/reference/>
- Spring Security reference: <https://docs.spring.io/spring-security/reference/>

