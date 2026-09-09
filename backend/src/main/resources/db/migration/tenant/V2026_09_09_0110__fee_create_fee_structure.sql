-- The fee structure: which heads apply, at what amount, to whom, for one academic session
-- (ADR-0012 rule 6, ADR-0033).
--
-- Versioned by (academic_session_id, school_class_id) from this, the first migration that creates
-- the table — never retrofitted. A row here is NEVER updated once written; "editing" a structure
-- means writing a whole new version and marking the previous one superseded, in one transaction.
-- That is stronger than ADR-0012 requires in the letter (the ADR names fee_charge and
-- fee_ledger_entry as the append-only rows) and is this module's own decision, argued in
-- ADR-0033: the alternative — an editable "current" row per class per session — is exactly the
-- option-1 shape ADR-0012 rejected for the ledger, reappearing one layer up where nothing was
-- watching for it.
--
-- academic_session_id, school_class_id and created_by are plain uuid columns with a database
-- foreign key, not Java associations — the same shape attendance_mark uses for its own session and
-- section columns, and document uses for student_id (ADR-0011, module map): this module has no
-- import of `academics` at all, only its named interface, academics.api.AcademicsLookup.
--
-- Targets by class only. FR-076 also lists section, student category, transport route, hostel,
-- optional subject and individual student — see package-info.java for which of those are deferred
-- and why.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table fee_structure (
    id                  uuid        not null,
    academic_session_id uuid        not null,
    school_class_id     uuid        not null,
    -- 1 for the first structure a class ever gets in a session; every further edit within that
    -- session is a new row with the next number. Never decremented, never reused.
    version             integer     not null,
    -- null while this is the live version for its (session, class). Set, once, at the moment a
    -- newer version is written in the same transaction — never cleared afterwards.
    superseded_at       timestamptz,
    created_at          timestamptz not null default now(),
    created_by          uuid        not null,
    constraint pk_fee_structure primary key (id),
    constraint fk_fee_structure_session foreign key (academic_session_id) references academic_session (id),
    constraint fk_fee_structure_class foreign key (school_class_id) references school_class (id),
    constraint fk_fee_structure_created_by foreign key (created_by) references user_account (id),
    constraint uq_fee_structure_version unique (academic_session_id, school_class_id, version),
    constraint ck_fee_structure_version_positive check (version > 0)
);

-- At most one live version per (session, class) at a time — the row a read resolves to.
create unique index uq_fee_structure_one_current on fee_structure (academic_session_id, school_class_id)
    where superseded_at is null;

-- The structure screen's own read: every class's current structure for one session.
create index idx_fee_structure_session on fee_structure (academic_session_id) where superseded_at is null;

comment on table fee_structure is
    'One version of what a class is charged in one academic session. Never updated — a new row is '
    'written and the old one superseded in the same transaction. Internal under ADR-0014.';
comment on column fee_structure.version is
    'Starts at 1 per (session, class), incremented on every edit within that session. What '
    'fee_charge will eventually pin to as its "source structure version" (ADR-0012''s own diagram), '
    'once fee_demand exists to raise one.';

-- One fee head's amount and collection frequency within a structure version. Belongs entirely to
-- its fee_structure: there is no update path of its own, because editing an item means writing a
-- new fee_structure version with the full new set of items (mirroring how academics reorders its
-- whole ladder in one transaction rather than moving one class at a time).
create table fee_structure_item (
    id                uuid          not null,
    fee_structure_id  uuid          not null,
    fee_head_id       uuid          not null,
    amount            numeric(12,2) not null,
    -- FR-077's six schedules, verbatim.
    frequency         varchar(20)   not null,
    created_at        timestamptz   not null default now(),
    constraint pk_fee_structure_item primary key (id),
    constraint fk_fee_structure_item_structure foreign key (fee_structure_id) references fee_structure (id),
    constraint fk_fee_structure_item_head foreign key (fee_head_id) references fee_head (id),
    -- One row per head per structure version — a school wanting two tuition lines defines two heads.
    constraint uq_fee_structure_item_head unique (fee_structure_id, fee_head_id),
    constraint ck_fee_structure_item_amount check (amount >= 0),
    constraint ck_fee_structure_item_frequency check (frequency in (
        'ONE_TIME', 'MONTHLY', 'QUARTERLY', 'TERM_WISE', 'ANNUAL', 'CUSTOM'
    ))
);

create index idx_fee_structure_item_structure on fee_structure_item (fee_structure_id);

comment on table fee_structure_item is
    'One fee head''s amount and frequency within a fee_structure version. Money is numeric(12,2), '
    'never a float (AGENTS.md). Internal under ADR-0014.';

-- The due dates an item's amount is split across (FR-075: "installments, due dates"). One row for
-- ONE_TIME/ANNUAL, several for MONTHLY/QUARTERLY/TERM_WISE/CUSTOM. The application layer validates
-- that a given item's installment amounts sum to that item's own amount; the database does not
-- attempt a cross-row sum check.
create table fee_installment (
    id                       uuid          not null,
    fee_structure_item_id    uuid          not null,
    due_date                 date          not null,
    amount                   numeric(12,2) not null,
    created_at               timestamptz   not null default now(),
    constraint pk_fee_installment primary key (id),
    constraint fk_fee_installment_item foreign key (fee_structure_item_id) references fee_structure_item (id),
    constraint uq_fee_installment_due_date unique (fee_structure_item_id, due_date),
    constraint ck_fee_installment_amount check (amount > 0)
);

create index idx_fee_installment_item on fee_installment (fee_structure_item_id);

comment on table fee_installment is
    'One due date within a fee_structure_item, and the amount due on it. Internal under ADR-0014.';
