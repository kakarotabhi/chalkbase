-- Subjects: the last piece of Phase 1 master data (docs/status.md).
--
-- A flat catalogue, unlike school_class: a subject has no natural order the way a class ladder
-- does (English does not come before Mathematics the way Class 5 comes before Class 6), so
-- there is no `sequence` column here and the list is read alphabetically instead.
--
-- Deactivated, never deleted, for the same reason as school_class and section (ADR-0019): nothing
-- references a subject yet in this build, but the timetable and marks modules that come next will,
-- and by the time one does it is too late to decide that deleting it was a mistake. `active` is on
-- the table from this first migration for that reason.
--
-- No relation to school_class or section here. Which classes teach which subjects is a subject
-- allocation, and that is timetable/marks territory (FR-041, FR-053) — a later phase and a
-- different module. A subject in this table is a fact about the school, not about a class.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table subject (
    id         uuid        not null,
    name       varchar(80) not null,
    -- The short form a mark sheet or a timetable cell actually has room for — "MATH", "SST" — and a
    -- school's own choice like everything else on this table. Not normalised to upper case: a
    -- school that types "Eng" is entitled to see "Eng" back.
    code       varchar(20) not null,
    -- Deactivated, never deleted, once anything references it (ADR-0019). Present from the first
    -- migration rather than retrofitted, for the reason above.
    active     boolean     not null default true,
    created_at timestamptz not null default now(),
    constraint pk_subject primary key (id),
    constraint uq_subject_name unique (name),
    constraint uq_subject_code unique (code)
);

comment on table subject is
    'A subject this school teaches — English, Mathematics. Flat catalogue, no relation to a class '
    'or section (that is a later, timetable/marks decision). Deactivated rather than deleted once '
    'referenced (ADR-0019).';
comment on column subject.code is
    'The school''s own short form, e.g. MATH. Unique, but not case-normalised.';
