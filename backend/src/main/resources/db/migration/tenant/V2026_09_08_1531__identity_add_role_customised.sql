-- Marks a role that role management has changed, so template reconciliation knows which rows are
-- still safe to touch (ADR-0031).
--
-- Every existing row starts as `false`. That is a deliberate, bounded trade-off, not an oversight:
-- there is no record of which roles were edited before this column existed, and role management
-- shipped only days before this migration, so the exposure is small. The alternative — starting
-- every row `true` — would protect a school's past edits at the cost of the very fix this migration
-- exists for: RoleTemplateInstaller reconciles only a role where this is `false`, so `true` on every
-- pre-existing row would leave every school provisioned before today exactly as stuck as it is now.
alter table role
    add column customised boolean not null default false;

comment on column role.customised is
    'Set the moment role management replaces this role''s permission set. RoleTemplateInstaller '
    'reconciliation (ADR-0031) skips any role where this is true, and never sets it back to false.';
