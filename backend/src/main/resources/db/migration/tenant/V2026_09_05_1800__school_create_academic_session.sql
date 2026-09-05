-- First per-tenant table. Created inside every school's own schema, never in `public`.
--
-- Deliberately unqualified: Flyway applies this file once per tenant schema, and the schema it
-- lands in is whichever one the orchestrator is currently pointed at (ADR-0011).
--
-- Note there is no `school_id` column. The schema is the tenant boundary.

create table academic_session (
    id         uuid        not null,
    name       varchar(40) not null,
    starts_on  date        not null,
    ends_on    date        not null,
    is_current boolean     not null default false,
    created_at timestamptz not null default now(),
    constraint pk_academic_session primary key (id),
    constraint uq_academic_session_name unique (name),
    constraint ck_academic_session_dates check (ends_on > starts_on)
);

-- A school has at most one current session. A partial unique index says so in the database rather
-- than hoping the application remembers — the kind of PostgreSQL-only constraint the H2 phase
-- could not express.
create unique index uq_academic_session_one_current on academic_session (is_current) where is_current;

comment on table academic_session is
    'An academic year for this school, e.g. 2026-27. April to March in most Indian schools.';
