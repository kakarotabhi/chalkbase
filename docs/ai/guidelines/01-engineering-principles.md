# Engineering Principles

## Priorities

Optimize in this order:

1. Correctness of school, student, fee, attendance, exam, and compliance records.
2. Data privacy and authorization.
3. Maintainability.
4. Operational reliability.
5. Performance.
6. Developer convenience.

Convenience never justifies breaking auditability, financial correctness, data protection, or tenant isolation.

## Clean Code Rules

- Keep code boring and explicit.
- Use meaningful names from the school domain.
- Keep functions small enough to understand without scrolling heavily.
- Keep files focused on one concept.
- Prefer composition over inheritance.
- Avoid global mutable state.
- Avoid clever abstractions that hide business rules.
- Avoid premature generic frameworks inside the app.
- Delete unused code when it is part of the current change and clearly owned by the change.
- Keep comments rare and useful. Explain why, not what.

## Domain Naming

Use domain terms consistently:

- Use `Student`, not `Child` or `Kid`.
- Use `Guardian` for parent/local guardian relationship data.
- Use `AcademicSession`, not `Year`.
- Use `ClassSection`, not ambiguous `Batch` unless the domain requires a batch.
- Use `FeeDemand`, `FeeReceipt`, `FeeTransaction`, `Concession`, and `Refund` distinctly.
- Use `AttendanceRecord`, `LeaveApplication`, and `AttendanceCorrection`.
- Use `Exam`, `AssessmentComponent`, `MarkEntry`, `ReportCard`.
- Use `ComplianceProfile`, `ComplianceDocument`, `DisclosureItem`.

Do not reuse one term for multiple concepts.

## Module Boundaries

Each module should own:

- Its domain entities.
- Its business rules.
- Its database tables or migrations.
- Its application services.
- Its API controllers.
- Its tests.

Cross-module access should go through services, APIs, domain events, or well-defined read models. Avoid direct repository access across modules.

## Configuration Over Hardcoding

Use configuration for anything that can vary by school:

- Board.
- State.
- Academic session dates.
- Classes and sections.
- Subjects and streams.
- Fee heads and due dates.
- Attendance statuses and thresholds.
- Exam patterns and grade scales.
- Certificate templates.
- Public disclosure fields.
- Communication templates.
- Numbering series.
- Approval workflows.

Hardcoded constants are acceptable only for stable technical values, not school policy.

## Data Integrity

- Enforce critical invariants in the database where practical.
- Validate again in application services for clear user errors.
- Use transactions around multi-step state changes.
- Use optimistic locking for records that multiple users edit.
- Use idempotency keys for payments, imports, webhooks, and document generation.
- Use append-only or reversal records for finance.
- Snapshot published documents and report cards.

## Error Handling

- Fail with a clear domain-specific error.
- Do not swallow exceptions silently.
- Do not expose stack traces or internal IDs to end users.
- Use structured API errors.
- Include a correlation/request ID in logs.
- Log enough context to debug without leaking sensitive data.

## Dependency Rules

Before adding a dependency:

- Check whether the framework already provides the capability.
- Check maintenance status and license.
- Check security history.
- Check size and transitive dependencies.
- Add it only at the layer where it is needed.
- Document why it was added if the choice is non-obvious.

Prefer mature libraries for:

- Payment integrations.
- PDF generation.
- Excel import/export.
- Authentication protocols.
- Object storage.
- Testcontainers.
- Validation.

Avoid dependencies for simple string formatting, trivial utilities, or one small function.

## Performance Rules

- Design list APIs with pagination from day one.
- Avoid N+1 database queries.
- Add indexes for common filters.
- Keep reports async if they can become slow.
- Use projections/read models for heavy dashboards.
- Cache reference data cautiously.
- Never use cache as the only source of truth.
- Measure before doing complex optimization.

## Auditability

Add audit events for:

- Login and failed login.
- Role and permission changes.
- Student sensitive field changes.
- Guardian contact changes.
- Admission approval/rejection.
- Attendance correction after lock.
- Fee payment, receipt, cancellation, concession, refund, write-off.
- Marks lock/publish.
- Report card publish.
- Certificate generation/reprint/cancellation.
- Consent creation/withdrawal.
- Public disclosure publication.
- Sensitive export.
- Configuration changes.

Audit logs should be immutable to normal app users.

## Documentation Rules

Update documentation when a change affects:

- Product behavior.
- API contracts.
- Database schema.
- Configuration.
- Deployment.
- Security model.
- Compliance behavior.
- Data import/export formats.

Prefer short local docs over hidden knowledge in chat.

## Review Rules

When reviewing code, prioritize:

- Authorization gaps.
- Tenant/school/session scoping gaps.
- Financial integrity bugs.
- Incorrect academic/session handling.
- Data leaks.
- Missing audit logs.
- Missing migrations.
- Missing tests around risky behavior.
- Race conditions and idempotency gaps.
- Hardcoded board/state/school rules.

Style-only feedback is secondary unless it blocks maintainability.

