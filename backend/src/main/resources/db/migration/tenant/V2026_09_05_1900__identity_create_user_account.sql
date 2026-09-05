-- Identity, per school.
--
-- The table is `user_account`, not `user`: PostgreSQL accepts `create table user` and then
-- `select * from user` silently returns the current_user FUNCTION instead of the table (ADR-0017).
--
-- Identity is separated from proof (ADR-0003): who someone is lives in user_identifier, how they
-- prove it lives in user_credential. Adding phone + OTP later is a row in each, not a migration.

create table user_account (
    id                   uuid        not null,
    display_name         varchar(200) not null,
    status               varchar(16) not null default 'ACTIVE',
    must_change_password boolean     not null default true,
    failed_attempts      smallint    not null default 0,
    locked_until         timestamptz,
    last_login_at        timestamptz,
    created_at           timestamptz not null default now(),
    constraint pk_user_account primary key (id),
    constraint ck_user_account_status check (status in ('ACTIVE', 'DISABLED'))
);

create table user_identifier (
    id              uuid         not null,
    user_account_id uuid         not null,
    type            varchar(16)  not null,
    value           varchar(320) not null,
    verified_at     timestamptz,
    created_at      timestamptz  not null default now(),
    constraint pk_user_identifier primary key (id),
    constraint fk_user_identifier_account foreign key (user_account_id)
        references user_account (id) on delete cascade,
    constraint ck_user_identifier_type check (type in ('USERNAME', 'EMAIL', 'PHONE')),
    -- Unique within this school only. Two schools may both have a parent "2026-0412", which is the
    -- point of accounts living per tenant.
    constraint uq_user_identifier_value unique (type, value)
);

create index idx_user_identifier_account on user_identifier (user_account_id);

create table user_credential (
    id              uuid        not null,
    user_account_id uuid        not null,
    type            varchar(16) not null,
    secret          varchar(512),
    status          varchar(16) not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    last_used_at    timestamptz,
    constraint pk_user_credential primary key (id),
    constraint fk_user_credential_account foreign key (user_account_id)
        references user_account (id) on delete cascade,
    constraint ck_user_credential_type check (type in ('PASSWORD', 'OTP', 'OIDC')),
    constraint ck_user_credential_status check (status in ('ACTIVE', 'REVOKED'))
);

-- One active credential of a given type per account; revoked ones are kept for audit.
create unique index uq_user_credential_active
    on user_credential (user_account_id, type) where status = 'ACTIVE';

comment on column user_credential.secret is
    'Password hash with its algorithm prefix, so the algorithm can be upgraded by re-hashing on next login (ADR-0003).';
