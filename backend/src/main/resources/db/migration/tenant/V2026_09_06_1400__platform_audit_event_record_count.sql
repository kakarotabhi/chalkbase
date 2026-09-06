-- How many records a bulk action touched (ADR-0018, amended).
--
-- Added because the student import could not be audited honestly without it. An import row saying
-- "Asha imported into 2026-27" and not whether that was three students or six hundred is a weak
-- entry for the single most consequential write the product has, and reconstructing the number by
-- counting `created_at` timestamps afterwards is exactly the forensic work an audit log exists to
-- spare somebody.
--
-- This does NOT weaken ADR-0018 §2. That rule exists to keep personal data out of the log: field
-- NAMES are recorded, never the values a field took, because the values are a child's name and
-- date of birth. A row count is not a value of any field and is not personal data — it is a
-- property of the event itself, like `occurred_at` and `outcome`.
--
-- The distinction is worth holding onto, because the tempting shortcut was to write the count into
-- `changed_fields` as `imported_600`, which would pass the field-name regex and would be smuggling
-- a value past a check built to stop precisely that.
--
-- Null for a single-record action, which is almost all of them.
alter table audit_event add column record_count integer;

comment on column audit_event.record_count is
    'How many records a bulk action touched. Null for a single-record action. A count is a property of the event, not a value of a field (ADR-0018).';
