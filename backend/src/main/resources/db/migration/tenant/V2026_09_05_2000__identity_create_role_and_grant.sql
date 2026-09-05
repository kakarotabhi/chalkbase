-- Roles and grants, per school (ADR-0005).
--
-- Three ideas, and the distinctions between them are the whole design:
--   permission  is CODE. Declared by each module, seeded on startup, never invented by a user.
--   role        is DATA. A school's own bundle of permissions, editable without a release.
--   grant       ties a user to a role WITHIN A SCOPE, optionally for a period.

-- Seeded from each module's registry on startup. Present in the database so a role can reference
-- it and a principal can read a real list in a UI; code remains the source of truth.
create table permission (
    code        varchar(80)  not null,
    module      varchar(40)  not null,
    label       varchar(120) not null,
    description varchar(400),
    constraint pk_permission primary key (code),
    -- <module>:<resource>:<action>
    constraint ck_permission_code check (code ~ '^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$')
);

-- A school's own role. `template_code` records which shipped template it was copied FROM, and is
-- deliberately not a foreign key: it is provenance, not a live link. If a school's role pointed at
-- a shared template, adding a permission to that template in a release would silently widen access
-- at every school — a security incident delivered by an upgrade (ADR-0005).
create table role (
    id            uuid         not null,
    code          varchar(40)  not null,
    name          varchar(120) not null,
    description   varchar(400),
    template_code varchar(40),
    created_at    timestamptz  not null default now(),
    constraint pk_role primary key (id),
    constraint uq_role_code unique (code)
);

create table role_permission (
    role_id         uuid        not null,
    permission_code varchar(80) not null,
    constraint pk_role_permission primary key (role_id, permission_code),
    constraint fk_role_permission_role foreign key (role_id) references role (id) on delete cascade,
    constraint fk_role_permission_permission foreign key (permission_code) references permission (code)
);

-- A user holds SEVERAL of these. That is what stops role names multiplying combinatorially: a
-- teacher who also runs transport has two grants, not a "teacher and transport in-charge" role.
create table user_role_grant (
    id              uuid        not null,
    user_account_id uuid        not null,
    role_id         uuid        not null,
    scope_type      varchar(16) not null,
    -- Null for SCHOOL and SELF, which need no target. WARD is resolved from the guardian
    -- relationship rather than stored, so it is never assigned here (ADR-0005).
    scope_id        uuid,
    valid_from      date,
    valid_to        date,
    created_at      timestamptz not null default now(),
    constraint pk_user_role_grant primary key (id),
    constraint fk_user_role_grant_account foreign key (user_account_id)
        references user_account (id) on delete cascade,
    constraint fk_user_role_grant_role foreign key (role_id) references role (id),
    constraint ck_user_role_grant_scope check (scope_type in
        ('SCHOOL', 'CAMPUS', 'DEPARTMENT', 'CLASS', 'SECTION', 'SUBJECT', 'SELF')),
    -- "Acting principal for March" is a real requirement; adding time bounds to a live permission
    -- table later is not something to look forward to.
    constraint ck_user_role_grant_period check (valid_to is null or valid_from is null or valid_to >= valid_from),
    -- A scoped role is granted once per target. Two identical grants are a bug, not a nuance.
    constraint uq_user_role_grant unique (user_account_id, role_id, scope_type, scope_id)
);

create index idx_user_role_grant_account on user_role_grant (user_account_id);
create index idx_role_permission_role on role_permission (role_id);

comment on table permission is
    'Seeded from code on startup. A row here without a module registry entry is stale and should be removed.';
comment on column role.template_code is
    'Which shipped template this was copied from. Provenance only — never a live reference.';
