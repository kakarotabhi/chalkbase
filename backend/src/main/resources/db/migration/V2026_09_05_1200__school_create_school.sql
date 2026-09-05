-- School (tenant) registry.
--
-- This migration was written against H2 and is rewritten here for PostgreSQL. Rewriting a merged
-- migration is normally forbidden (backend/AGENTS.md), and the exception is narrow and will not
-- recur: H2 was in-memory, so no database has ever persisted this schema and no Flyway history
-- exists to conflict with. From the first PostgreSQL deployment onwards, migrations are immutable.
create table school (
    id         uuid         not null,
    code       varchar(32)  not null,
    name       varchar(200) not null,
    board      varchar(16)  not null,
    city       varchar(100),
    state      varchar(100),
    active     boolean      not null default true,
    created_at timestamptz  not null default now(),
    constraint pk_school primary key (id),
    constraint uq_school_code unique (code)
);

comment on table school is 'Schools served by this installation. Each row is a tenant (ADR-0002).';
