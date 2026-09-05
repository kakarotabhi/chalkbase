# Technical Architecture

## Recommended Architecture

Start with a modular monolith:

- Angular frontend.
- Spring Boot backend.
- PostgreSQL database.
- Redis for caching, queues, rate limiting, and short-lived state where needed.
- Object storage for documents.
- Docker-based deployment through Coolify.

This gives simpler development, deployment, and debugging while still allowing the codebase to be split into services later if scale requires it.

## Backend

Recommended stack:

- Java 21 or latest stable LTS supported by the team.
- Spring Boot 3.x.
- Spring Web or Spring WebFlux only if reactive needs are real.
- Spring Security.
- Spring Data JPA with Hibernate.
- Flyway or Liquibase for database migrations.
- Bean Validation.
- MapStruct for DTO mapping if useful.
- Spring Modulith or package-level modular boundaries if the team wants enforcement.
- OpenAPI/Swagger for API documentation.
- Quartz, JobRunr, or Spring Scheduler for background jobs depending on complexity.

Backend modules:

- platform.
- identity-access.
- organization.
- student.
- admissions.
- academics.
- attendance.
- timetable.
- exams.
- fees.
- communication.
- documents.
- compliance.
- hr-payroll.
- transport.
- hostel.
- library.
- inventory.
- safety-wellbeing.
- reporting.
- integrations.

Backend rules:

- Keep controllers thin.
- Put business rules in services/domain components.
- Use transactions deliberately.
- Use DTOs for API contracts.
- Do not expose JPA entities directly.
- Use database constraints for important invariants.
- Use optimistic locking for records edited by many users.
- Use idempotency keys for payments, imports, and integrations.

## Frontend

Recommended stack:

- Angular latest stable version.
- Angular Router.
- Reactive forms.
- Signals for local UI state where appropriate.
- Angular Material, PrimeNG, or a consistent internal component library.
- Tailwind CSS only if the team commits to a consistent design system.
- PWA support after core workflows stabilize.
- OpenAPI generated client or typed API client.

Frontend requirements:

- Responsive layouts for desktop and mobile.
- Role-specific navigation.
- Fast global search for students, staff, receipts, applications, books, and vehicles.
- Data tables with saved filters, column preferences, bulk actions, and exports.
- Form autosave for long admission/compliance forms.
- Clear validation errors.
- Offline-tolerant drafts for teacher attendance/marks if possible.
- Accessible keyboard navigation for admin-heavy screens.

## Database

Use PostgreSQL as the primary database.

Requirements:

- Use UUID primary keys.
- Use foreign keys and check constraints.
- Use partial indexes for active records.
- Use composite indexes for academic_session_id, class_id, section_id, and status filters.
- Use JSONB for custom fields only where relational modeling is not practical.
- Use generated columns or materialized views where reporting needs justify them.
- Row-level security is not used: the tenant boundary is the schema (ADR-0011).
- Use database migrations for every schema change.

Tenancy approach — decided in [ADR-0011](../architecture/adr/0011-schema-per-tenant.md):

- One PostgreSQL schema per school in a single database, served by one application and one
  connection pool. Business tables carry **no** `school_id`.
- The schema is selected per connection from the authenticated session, and reset when the
  connection returns to the pool.
- Reference data and the tenant registry live in `public`; migrations are split into a shared set
  and a per-tenant set that fans out to every school.
- Add automated tests that prove one tenant cannot read another's rows.
- A school needing physical isolation can later be moved to its own database without changing
  application code.

## Document Storage

Documents include student photos, certificates, admission documents, health records, staff files, vehicle documents, generated report cards, receipts, and compliance evidence.

Recommended options:

- S3-compatible storage such as MinIO on VPS.
- External S3-compatible provider if VPS storage is limited.

Requirements:

- Store file metadata in PostgreSQL.
- Store files outside the database.
- Use private buckets by default.
- Generate short-lived signed URLs.
- Scan uploads if a scanning service is configured.
- Enforce file type and size limits.
- Keep access logs for sensitive files.
- Support retention and deletion workflows.

## API Design

Use REST APIs with OpenAPI documentation.

API requirements:

- Version APIs as `/api/v1`.
- Use pagination for list endpoints.
- Support sorting and filtering.
- Return structured validation errors.
- Use consistent error codes.
- Use idempotency keys for payment, import, and document generation actions.
- Use ETags or version fields for optimistic concurrency where useful.
- Use WebSocket or Server-Sent Events for live notifications only where needed.

## Authentication and Authorization

Requirements:

- Local login for staff/admin.
- Parent login through password or OTP-based flow.
- Optional Google/Microsoft SSO for staff/students.
- Password hashing using a strong adaptive algorithm.
- Account lockout and rate limiting.
- Optional MFA for admins.
- Device/session management.
- Fine-grained permissions.
- Sensitive field masking.

Permission model should include:

- Module permission.
- Action permission.
- Scope permission.
- Field-level restrictions for sensitive categories.
- Approval authority levels.

## Security Requirements

Minimum requirements:

- HTTPS everywhere.
- Secure cookies.
- CSRF protection for browser sessions.
- CORS locked to known domains.
- Rate limiting for login, OTP, payment, and public endpoints.
- Input validation and output encoding.
- SQL injection prevention through parameterized queries/JPA.
- File upload validation.
- Malware scanning option.
- Secrets only through environment variables or secret manager.
- No sensitive data in logs.
- Audit logs for sensitive actions.
- Regular dependency scanning.
- Backup encryption where feasible.

Sensitive data:

- Aadhaar-related data if ever collected.
- APAAR ID.
- PEN/UDISE-linked student identifiers.
- Caste/community/religion/minority data.
- Income and EWS/BPL records.
- Health and disability records.
- Counselling and safeguarding records.
- Bank and payroll data.
- Payment identifiers.

## Non-Functional Requirements

### Performance

Targets:

- Common list pages should load within 2 seconds for normal school data.
- Attendance entry for a class should be usable on mobile with minimal taps.
- Fee receipt generation should complete within 5 seconds after payment confirmation.
- Reports with large data should run asynchronously if they exceed normal request time.
- Search should return common student/staff records within 1 second for typical school scale.

Expected scale for initial version:

- 1 to 10 schools per installation.
- 500 to 5,000 students per school.
- 50 to 500 staff per school.
- 1,000 to 50,000 fee transactions per year per school.
- Millions of attendance records over multiple years.

### Availability

Targets:

- 99 percent monthly availability for self-hosted MVP.
- Planned maintenance windows.
- Health checks for API, frontend, database, Redis, storage, and background jobs.
- Graceful degradation when SMS/payment/GPS providers fail.

### Backup and Recovery

Requirements:

- Automated PostgreSQL backups.
- Object storage backups.
- Redis should not be the only store for permanent data.
- Daily backup minimum.
- Point-in-time recovery if feasible.
- Regular restore drills.
- Backup retention policy.
- Backup encryption.
- Offsite backup copy.

Recovery targets:

- RPO: 24 hours for MVP, improve to 1 hour for mature production.
- RTO: 4 to 8 hours for MVP, improve with automation.

### Accessibility and Usability

Requirements:

- Responsive design.
- Keyboard navigation for admin forms.
- Sufficient color contrast.
- Clear labels and validation.
- Avoid cluttered screens for teachers and parents.
- Support English and local language labels/templates.
- Low-bandwidth friendly parent portal.

### Observability

Requirements:

- Structured logs.
- Request IDs.
- Audit logs separate from application logs.
- Metrics for API latency, errors, job failures, queue depth, email/SMS failures, payment failures, and database health.
- Error tracking.
- Admin-visible integration health.

## Coolify Deployment

Coolify can run Docker-based applications and manage services such as databases, environment variables, domains, proxy/TLS, and deployments. The product should be packaged to fit that model cleanly.

Recommended services:

- `web`: Angular static frontend served through Nginx or a lightweight web server.
- `api`: Spring Boot application.
- `postgres`: PostgreSQL database.
- `redis`: Redis.
- `minio`: S3-compatible object storage, optional if using external object storage.
- `worker`: optional separate background worker if jobs become heavy.
- `backup`: optional scheduled backup container/job if not handled by Coolify or VPS tooling.

Deployment requirements:

- Provide Dockerfile for backend.
- Provide Dockerfile for frontend.
- Provide docker-compose.yml for complete deployment.
- Use environment variables for all secrets and environment-specific settings.
- Add health checks.
- Add database migration execution on deployment.
- Use persistent volumes for PostgreSQL, MinIO, and backups.
- Use separate production and staging environments.
- Provide seed/admin bootstrap command.
- Document restore process.

Environment variables should cover:

- Database URL, username, password.
- Redis URL.
- JWT/session secrets.
- Object storage endpoint, bucket, access key, secret key.
- Public frontend URL.
- API URL.
- SMTP settings.
- SMS provider keys.
- WhatsApp provider keys.
- Payment gateway keys and webhook secrets.
- Encryption keys.
- Backup settings.

## CI/CD

Minimum pipeline:

- Build backend.
- Run backend tests.
- Run database migration validation.
- Build frontend.
- Run frontend tests.
- Lint frontend.
- Build Docker images.
- Deploy to staging.
- Smoke test staging.
- Manual approval for production.

Quality gates:

- No critical dependency vulnerabilities.
- No failing tests.
- No migration rollback risk without review.
- OpenAPI contract generated and checked.

## Testing Requirements

Backend:

- Unit tests for domain rules.
- Integration tests with PostgreSQL Testcontainers.
- Security permission tests.
- Payment webhook idempotency tests.
- Fee ledger tests.
- Attendance calculation tests.
- Exam calculation tests.
- Import validation tests.
- Audit log tests.

Frontend:

- Component tests for complex forms.
- E2E tests for major workflows.
- Mobile viewport tests for teacher and parent screens.
- Accessibility checks for core screens.

Critical E2E workflows:

- Admission to student conversion.
- Student attendance and parent alert.
- Fee payment and receipt generation.
- Marks entry to report card publishing.
- Student promotion.
- Transfer certificate generation.
- Compliance document expiry alert.
- Parent login for multiple children.

## Development Environments

Recommended:

- Local Docker Compose for PostgreSQL, Redis, and MinIO.
- Backend local run through IDE or Gradle/Maven.
- Frontend local dev server.
- Seed data for demo school.
- Separate test data for CBSE-like and state-board-like configurations.

## Architectural Decisions to Make Early

1. Maven or Gradle.
2. Angular component library.
3. Authentication token strategy: server session or JWT plus refresh token.
4. Object storage provider.
5. Payment gateway.
6. SMS and WhatsApp provider.
7. Single-school self-hosted mode vs multi-tenant mode from day one.
8. Whether to include Keycloak or keep authentication inside the app.
9. Report generation library.
10. PDF generation approach.

## Suggested Backend Package Boundaries

Example:

```text
com.example.school
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
  payroll
  transport
  hostel
  library
  inventory
  safety
  reporting
  integrations
```

Each module should expose service interfaces and avoid directly modifying another module's tables except through approved services or events.

## Event Model

Useful domain events:

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
- StaffJoined.
- VehicleDocumentExpiring.
- ComplianceDocumentExpiring.
- IncidentReported.
- MessageDeliveryFailed.

Events can initially be in-process and stored in an outbox table. Later, the system can move to a message broker if needed.

## Audit Logging

Audit events should capture:

- Actor user ID.
- Actor role.
- School ID.
- Entity type.
- Entity ID.
- Action.
- Old value and new value for important fields.
- Timestamp.
- IP address and user agent where available.
- Reason/comment where required.
- Request ID.

Audit logs should be immutable to normal users.

