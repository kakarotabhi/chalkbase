-- Session store, in `public` and deliberately alone there.
--
-- Reading the session cookie is what tells us which tenant to bind, so the session table cannot
-- itself live in a tenant schema (ADR-0017). It is the only exception, and it holds no personal
-- data: an opaque id, timestamps, and serialised attributes carrying the bound schema and principal.
--
-- Column names and types follow Spring Session JDBC's expected schema. Do not rename them.

create table spring_session (
    primary_id            char(36)    not null,
    session_id            char(36)    not null,
    creation_time         bigint      not null,
    last_access_time      bigint      not null,
    max_inactive_interval int         not null,
    expiry_time           bigint      not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id)
        references spring_session (primary_id) on delete cascade
);

comment on table spring_session is
    'Server-side sessions (ADR-0003). Expired rows need a scheduled purge, or this grows forever.';
