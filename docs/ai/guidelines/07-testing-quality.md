# Testing and Quality Guidelines

## Testing Principle

Test risk, not just lines.

This school ERP has high-risk areas:

- Permissions.
- Student identity.
- Fees.
- Receipts.
- Concessions.
- Payments.
- Attendance.
- Exam marks.
- Report cards.
- Promotions.
- Certificates.
- Compliance exports.
- Sensitive files.

Changes in these areas require tests unless the user explicitly says not to add tests.

## Backend Test Types

Use:

- Unit tests for pure domain logic.
- Service tests for use cases and transactions.
- Repository/integration tests with PostgreSQL.
- Controller tests for API behavior and error mapping.
- Security tests for permissions and scope.
- Contract tests where API clients depend on stable schemas.

Prefer Testcontainers for PostgreSQL-dependent tests when the project supports it.

## Frontend Test Types

Use:

- Component tests for complex forms and state.
- Unit tests for utilities and facades.
- E2E tests for critical workflows.
- Accessibility checks for important screens.
- Mobile viewport checks for parent and teacher flows.

Do not test implementation details that make refactoring painful.

## Critical Test Scenarios

### Authorization

Test:

- User cannot access another school's data.
- Teacher cannot access unassigned classes.
- Parent cannot access another parent's child.
- Accountant cannot access counselling records.
- Health user cannot access fee concessions.
- Public verification token exposes only allowed fields.

### Fees

Test:

- Fee demand generation.
- Concession approval.
- Partial payment.
- Online payment webhook idempotency.
- Receipt number uniqueness.
- Receipt cancellation/reversal.
- Refund.
- Due report correctness.
- Payment mismatch handling.

### Attendance

Test:

- Daily attendance marking.
- Leave approval.
- Attendance lock.
- Correction workflow.
- Percentage calculation.
- Short-attendance alerts.
- Holiday/working-day handling.

### Exams

Test:

- Marks entry validation.
- Absent/exempted states.
- Weightage calculation.
- Grade calculation.
- Marks lock.
- Report card publish.
- Snapshot stability after template changes.

### Admissions

Test:

- Enquiry to application.
- Application validation.
- Document upload.
- Admission approval.
- Fee payment.
- Student conversion.
- Duplicate detection.

### Compliance

Test:

- Mandatory field completeness.
- Export generation.
- Export audit log.
- Consent status transitions.
- Disclosure approval and publish history.

## Regression Testing Rules

When fixing a bug:

- Add a failing test that reproduces the bug where practical.
- Fix the smallest relevant area.
- Keep the test focused on externally visible behavior.
- Mention any untested residual risk.

## Test Data

Maintain realistic seed/test data:

- CBSE-like school.
- State-board-like school.
- Academic session 2026-27.
- Multiple classes and sections.
- Siblings sharing guardians.
- RTE/EWS student.
- CWSN student.
- Transport student.
- Hostel student.
- Fee concessions and partial payments.
- Locked attendance and exams.

Do not use real student data in tests.

## Build Quality Gates

Before completion, run relevant commands:

- Backend compile/build.
- Backend tests.
- Frontend build.
- Frontend tests/lint if configured.
- Database migration validation.
- E2E smoke tests for broad workflow changes.

If a command cannot be run, report why.

## Static Analysis

Recommended:

- Java formatting and checkstyle/spotless.
- TypeScript linting.
- Dependency vulnerability checks.
- Secret scanning.
- SQL migration linting where available.
- OpenAPI validation.

Do not add tooling that the team cannot run consistently.

## Manual QA Checklist

For UI workflows, verify:

- Empty state.
- Loading state.
- Error state.
- Permission-denied state.
- Validation errors.
- Mobile layout.
- Long names and Indian address data.
- Slow network behavior where relevant.
- Print/PDF output where relevant.

## Performance Checks

Check high-volume workflows:

- Student search.
- Attendance entry for large class.
- Fee due report.
- Receipt generation.
- Marks entry.
- Report card generation.
- Communication batch send.

Watch for:

- N+1 queries.
- Missing indexes.
- Unbounded list loads.
- Long synchronous report requests.
- Repeated frontend API calls.

## Release Readiness

A release should not ship until:

- Critical tests pass.
- Migrations have been tested.
- Backups are configured.
- Restore has been rehearsed for production launch.
- Security-sensitive endpoints have permission tests.
- Payment flows are verified in sandbox and production readiness checklist.
- Admin can export critical school data.

