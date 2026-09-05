-- The tenant registry. Lives in `public`, and is the only thing that knows which schools exist.
--
-- It cannot live in a tenant schema for the obvious reason: it is what tells the application which
-- tenant schemas to connect to and migrate (ADR-0011).
--
-- This replaces the single-schema `chalkbase.school` table created by the pre-ADR-0011 migration.
-- That migration is deleted rather than followed with an ALTER: it had only ever been applied to a
-- development database holding zero rows, and the layout it created no longer exists. This is the
-- last such exception — from here migrations are immutable and expand/contract only.

create table school_group (
    id         uuid         not null,
    code       varchar(32)  not null,
    name       varchar(200) not null,
    created_at timestamptz  not null default now(),
    constraint pk_school_group primary key (id),
    constraint uq_school_group_code unique (code)
);

comment on table school_group is
    'A trust or society owning several campuses. A grouping, never a container: each campus is its own tenant.';

create table school (
    id          uuid         not null,
    code        varchar(32)  not null,
    name        varchar(200) not null,
    schema_name varchar(63)  not null,
    group_id    uuid,
    board       varchar(16)  not null,
    city        varchar(100),
    state       varchar(100),
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    constraint pk_school primary key (id),
    constraint uq_school_code unique (code),
    constraint uq_school_schema_name unique (schema_name),
    constraint fk_school_group foreign key (group_id) references school_group (id),
    -- Mirrors SchemaName.PATTERN in the application. The database is the second guard, not the
    -- first: a name that reaches SET search_path has already been validated in code.
    constraint ck_school_schema_name check (schema_name ~ '^[a-z][a-z0-9_]{2,62}$')
);

comment on table school is
    'One row per campus. `schema_name` is the PostgreSQL schema holding that campus''s data.';
comment on column school.schema_name is
    'PostgreSQL identifiers are limited to 63 bytes, hence varchar(63).';

-- Only active schools are migrated and routable, and that lookup happens on every boot.
create index idx_school_active on school (active) where active;
