# Database Guidelines: PostgreSQL

## Database Direction

Use PostgreSQL as the authoritative system of record.

Database work must prioritize:

- Data correctness.
- Referential integrity.
- Auditability.
- Queryability.
- Safe migration.
- Tenant/school/session scoping.

## Schema Principles

- Use relational tables for core business data.
- Use JSONB only for custom fields, integration payloads, provider metadata, and low-risk flexible attributes.
- Use foreign keys for important relationships.
- Use unique constraints for business uniqueness.
- Use check constraints for simple valid ranges and states.
- Use indexes for common filters.
- Keep historical records instead of overwriting important facts.
- Do not encode business-critical rules only in frontend code.

## IDs and Keys

Use:

- UUID primary keys for internal records.
- Human-readable generated numbers for school-facing records.
- External identifiers in separate nullable columns or identifier tables.

Examples:

- Student internal ID: UUID.
- Admission number: configured series.
- Receipt number: configured series.
- Certificate number: configured series.
- Employee code: configured series.
- Accession number: library-specific series.
- APAAR/PEN/board registration: external identifiers with restricted access.

Rules:

- Do not expose sequential primary keys publicly.
- Do not depend on names or phone numbers as identifiers.
- Keep generated number rules tenant/school scoped.

## Required Common Columns

Most business tables should include:

- `id`
- `school_id`
- `academic_session_id` where session-specific
- `created_at`
- `created_by`
- `updated_at`
- `updated_by`
- `version` where optimistic locking is needed
- `status` where lifecycle matters

Use `deleted_at`/`deleted_by` only where soft delete is appropriate. Do not soft-delete records that need explicit cancellation/reversal workflows.

## Tenant and Session Scoping

Every business query must be scoped correctly:

- Tenant or school.
- Campus where applicable.
- Academic session where applicable.
- Class/section/subject where applicable.
- User assignment or permission scope.

Add indexes matching these filters.

Example index shape:

```sql
CREATE INDEX idx_student_enrollment_school_session_class
ON student_enrollment (school_id, academic_session_id, class_id, section_id);
```

## Migration Rules

Use Flyway or Liquibase.

Rules:

- Every schema change must be a migration.
- Migrations must be deterministic.
- Do not edit applied migrations.
- Add new migrations for fixes.
- Include constraints and indexes in migrations.
- Backfill data safely.
- Split risky migrations into expand, migrate, contract steps.
- Avoid long locks on large tables.
- Test migrations on realistic data before production.

## Naming Conventions

Suggested:

- Tables: `snake_case`, plural or singular consistently. Pick one and keep it.
- Columns: `snake_case`.
- Foreign keys: `<entity>_id`.
- Indexes: `idx_<table>_<columns_or_purpose>`.
- Unique constraints: `uk_<table>_<columns_or_purpose>`.
- Foreign keys: `fk_<table>_<referenced_table>`.
- Check constraints: `ck_<table>_<rule>`.

Do not use ambiguous names like `data`, `details`, `type`, or `status` without clear context.

## Financial Data Rules

Fees must be ledger-like.

Rules:

- Do not hard-delete fee transactions.
- Do not update posted receipt amounts.
- Use reversal/void records.
- Store payment gateway webhook payload metadata.
- Make webhook processing idempotent.
- Keep receipt numbers unique.
- Keep cancellation reasons and approvers.
- Reconcile fee demand, paid amount, concessions, refunds, and outstanding amount.
- Use `numeric` for money. Do not use floating point.
- Store currency if future multi-currency support is possible, default `INR`.

Critical tables should include audit-friendly status history.

## Attendance Data Rules

- Store attendance at the granularity required by the school: daily or period-wise.
- Preserve lock status.
- Preserve corrections with reason, old value, new value, actor, and approval.
- Do not overwrite locked attendance without a correction record.
- Store attendance calendar and working-day configuration.
- Compute percentages using configured rules.

## Exam Data Rules

- Store exam configuration separately from marks.
- Version result calculation rules.
- Lock marks after verification.
- Snapshot published report cards.
- Do not let template changes modify old published report cards.
- Store absent, exempted, medical, withheld, and not applicable states distinctly.

## Document Data Rules

- Store files outside PostgreSQL.
- Store metadata in PostgreSQL.
- Store checksum where practical.
- Store classification and access policy.
- Keep generated official document snapshots.
- Use verification tokens for public checks.
- Do not expose internal object storage paths to clients.

## JSONB Rules

JSONB is acceptable for:

- Custom fields.
- Provider payloads.
- Import row error details.
- Integration metadata.
- Flexible compliance mappings.

JSONB is not acceptable as the only storage for:

- Fee transactions.
- Student enrollment.
- Attendance records.
- Marks.
- Receipts.
- Permissions.
- Audit logs.
- Core relationships.

If JSONB fields are queried frequently, add appropriate GIN or expression indexes.

## Indexing Rules

Add indexes for:

- Foreign keys used in joins.
- Common search filters.
- Tenant/school/session filters.
- Status filters.
- Date range filters.
- Receipt number, admission number, application number, certificate number.
- Payment gateway IDs and idempotency keys.

Use partial indexes for common active-record queries where useful.

Avoid:

- Duplicating indexes created by unique constraints.
- Indexing every column.
- Ignoring write cost on high-volume tables.

## Reporting

For MVP:

- Use indexed transactional tables.
- Add materialized views only when needed.
- Snapshot compliance reports and published report cards.

For scale:

- Add reporting schema.
- Add scheduled aggregations.
- Add export history.

## Backup and Restore

Requirements:

- Automated PostgreSQL backups.
- Offsite copy.
- Restore drill.
- Documented recovery steps.
- Backup encryption where practical.
- Backup monitoring.

Do not consider backup complete until restore is tested.

## Official References

- PostgreSQL constraints: <https://www.postgresql.org/docs/current/ddl-constraints.html>
- PostgreSQL JSON types: <https://www.postgresql.org/docs/current/datatype-json.html>
- PostgreSQL row security: <https://www.postgresql.org/docs/current/ddl-rowsecurity.html>
- PostgreSQL backup and restore: <https://www.postgresql.org/docs/current/backup.html>

