-- Fee heads and concession types (ADR-0012, Phase 0 §4, ADR-0033).
--
-- Both are catalogues a school defines once and reuses across sessions — closer to `subject` than
-- to anything session-scoped. Neither table carries an academic_session_id: a fee head named
-- "Tuition Fee" is the same head every year, and it is the fee_structure that says what it costs in
-- a given session (the next migration).
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

-- The seven fee heads Phase 0 §4 confirmed: tuition, admission, annual/development, transport,
-- exam, activity, late fee. `category` is this closed set, enforced below; `name` is what the
-- school actually calls the line item on a receipt, so two heads may share a category (a school
-- charging both a "Sports Fee" and an "Annual Day Fee" under ACTIVITY) without sharing a name.
create table fee_head (
    id                       uuid          not null,
    name                     varchar(80)   not null,
    category                 varchar(30)   not null,
    -- Delhi's DoE caps Development Fee as a proportion of tuition (07-phase-0-decisions.md §2,
    -- ADR-0012). Only meaningful for category = ANNUAL_DEVELOPMENT; ck_fee_head_cap_only_development
    -- below is what keeps it from being set on any other head. Nullable because most schools run no
    -- cap at all until their state requires one, and the cap fraction itself is not hardcoded to 15%
    -- — Delhi is the first target state, not the only one this table will ever serve (ADR-0006).
    cap_percent_of_tuition   numeric(5,2),
    active                   boolean       not null default true,
    created_at               timestamptz   not null default now(),
    constraint pk_fee_head primary key (id),
    constraint uq_fee_head_name unique (name),
    constraint ck_fee_head_category check (category in (
        'TUITION', 'ADMISSION', 'ANNUAL_DEVELOPMENT', 'TRANSPORT', 'EXAM', 'ACTIVITY', 'LATE_FEE'
    )),
    constraint ck_fee_head_cap_only_development check (
        cap_percent_of_tuition is null or category = 'ANNUAL_DEVELOPMENT'
    ),
    constraint ck_fee_head_cap_range check (
        cap_percent_of_tuition is null or (cap_percent_of_tuition > 0 and cap_percent_of_tuition <= 100)
    )
);

comment on table fee_head is
    'A named thing this school charges for. Internal under ADR-0014 — a price list, not a family''s '
    'own record. Deactivated, never deleted, once a fee_structure_item has used it.';
comment on column fee_head.category is
    'One of the seven heads Phase 0 confirmed. Closed set: a school does not invent an eighth.';
comment on column fee_head.cap_percent_of_tuition is
    'Delhi DoE caps Development Fee as a percentage of tuition. Set by the school, not hardcoded, '
    'and only legal on an ANNUAL_DEVELOPMENT head — see ck_fee_head_cap_only_development.';

-- Waivers, defined here as a catalogue (FR-075: "the system shall define ... waivers"). Applying
-- one to a student's actual charge is FR-078's other half, deferred to fee demand (ADR-0012's
-- fee_ledger_entry) along with the demand it would attach to — see this module's package-info.java
-- for why defining the catalogue and applying an instance of it are different pieces of work.
create table fee_concession_type (
    id                uuid          not null,
    name              varchar(80)   not null,
    category          varchar(30)   not null,
    description       varchar(300),
    -- ADR-0012 rule 5: a concession is money given away and requires approval. True by default;
    -- a school with, say, an automatic sibling discount it never wants a second sign-off on may
    -- turn this off for that one type, but it does not default to silent.
    requires_approval boolean       not null default true,
    active            boolean       not null default true,
    created_at        timestamptz   not null default now(),
    constraint pk_fee_concession_type primary key (id),
    constraint uq_fee_concession_type_name unique (name),
    constraint ck_fee_concession_type_category check (category in (
        'SIBLING', 'STAFF_CHILD', 'MANAGEMENT_QUOTA', 'RTE_EWS', 'SCHOLARSHIP', 'OTHER'
    ))
);

comment on table fee_concession_type is
    'A kind of waiver this school offers (FR-078): sibling, staff-child, management quota, RTE/EWS, '
    'scholarship, other. Defining one here does not grant it to anyone — see the module Javadoc.';
