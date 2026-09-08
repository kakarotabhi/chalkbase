-- The registry's own copy of the school's time zone, for the same reason it already carries a copy
-- of the name, board and town (V2026_09_05_1800__platform_create_tenant_registry.sql): identity's
-- login (`SchoolLookup.byCode`) resolves the school with no tenant bound, and the value it reads
-- here is what rides onto the session as `SchoolSummary` — the shape every later page load reads
-- without opening the tenant schema again (ADR-0008). The profile, inside the school's own schema,
-- is where a school actually edits this; see the tenant migration alongside this one and
-- `docs/architecture/adr/0032-school-timezone.md`.
--
-- Same default as the profile column, for the same reason: it keeps every school already registered
-- valid the moment this migration runs, rather than making a `not null` column with no default an
-- outage for every existing row.
alter table school
    add column timezone varchar(50) not null default 'Asia/Kolkata';

comment on column school.timezone is
    'A copy of the profile''s IANA zone id, kept in step on every profile save. Authoritative one is school_profile.timezone.';

alter table school
    add constraint ck_school_timezone check (timezone ~ '^[A-Za-z0-9_+/-]{1,50}$');
