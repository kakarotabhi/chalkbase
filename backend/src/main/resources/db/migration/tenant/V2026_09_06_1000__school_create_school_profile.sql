-- The school's own profile: the details a school edits about itself.
--
-- Why this is not in `public.school`. That row is the REGISTRY (ADR-0011): who exists, what code
-- they are addressed by, and which schema their data lives in. It is read before any tenant is
-- bound — by the migration orchestrator at startup and by the login form resolving a school code —
-- so everything in it is paid for on paths that have nothing to do with a school's address. The
-- rich, editable detail is the school's own data and belongs in the school's own schema, where the
-- schema boundary already protects it and where a school editing its address touches nothing that
-- routing depends on.
--
-- These fields are Tier 1 master data (ADR-0006): rows a school edits, with no branching in code.
--
-- Deliberately unqualified, like every other file in this folder: Flyway applies it once per tenant
-- schema. There is no `school_id` column — the schema is the tenant boundary.

create table school_profile (
    id                 uuid         not null,

    -- Exactly one profile per school, enforced here rather than in application code.
    --
    -- A school is the tenant, so "the profile of this schema" is a singleton by definition, and the
    -- only honest place to say so is the schema itself: a service that checks first and inserts
    -- second is two statements with a race between them, and a second row would make every read
    -- ambiguous with no way to tell which one is the school. `unique` on a column that `check`
    -- pins to a single value is the standard way to spell "at most one row" in PostgreSQL.
    is_singleton       boolean      not null default true,

    address_line1      varchar(200) not null,
    address_line2      varchar(200),
    city               varchar(100) not null,
    state              varchar(100) not null,
    pincode            varchar(6)   not null,

    principal_name     varchar(200) not null,
    phone              varchar(20)  not null,
    email              varchar(320) not null,
    website            varchar(200),

    affiliation_number varchar(40),
    board              varchar(16)  not null,

    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),

    constraint pk_school_profile primary key (id),
    constraint uq_school_profile_singleton unique (is_singleton),
    constraint ck_school_profile_singleton check (is_singleton),

    -- Indian PIN codes are six digits and never begin with zero. Mirrored by the request DTO and
    -- by the form, so a user is told before the round trip; this is the guard that holds when
    -- something writes here without going through the API.
    constraint ck_school_profile_pincode check (pincode ~ '^[1-9][0-9]{5}$'),

    -- Deliberately weaker than the application's own e-mail and phone validation. A database check
    -- that is stricter than the validator in front of it turns a typo into a constraint violation
    -- with no field to attach it to; this only rules out values that cannot be an address or a
    -- number at all.
    constraint ck_school_profile_email check (email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    constraint ck_school_profile_phone check (phone ~ '^[+0-9][0-9 ()-]{6,19}$'),
    constraint ck_school_profile_website check (website is null or website ~ '^https?://'),

    constraint ck_school_profile_board check (board in ('CBSE', 'CISCE', 'STATE', 'IB', 'CAIE', 'OTHER'))
);

comment on table school_profile is
    'This school''s own details: address, contact and affiliation. One row, enforced by uq_school_profile_singleton.';
comment on column school_profile.is_singleton is
    'Always true. Exists only so a unique constraint can say "at most one row"; never read by application code.';
comment on column school_profile.board is
    'A copy of the registry''s board for this school, and the authoritative one: the registry keeps its own so the platform can list schools without binding a tenant.';
comment on column school_profile.pincode is
    'varchar rather than char(6): a fixed-width type pads on the way out, and a trailing space in a PIN code on a certificate is nobody''s idea of correct.';
