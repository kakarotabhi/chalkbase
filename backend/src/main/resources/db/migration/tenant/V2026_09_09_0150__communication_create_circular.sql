-- Circulars and notices, targeted by class and section (FR-097-FR-104, Phase 2).
--
-- Three tables. circular is the content and its lifecycle (DRAFT then PUBLISHED, one-way).
-- circular_target is what it was addressed to — a class, or one section of it — and survives
-- publishing unchanged, because "what we meant to send this to" must stay correct even after the
-- roster moves on. circular_recipient is who it actually reached: one row per actively enrolled
-- student, resolved once at publish time from the targets, the same "resolved once, kept forever"
-- choice attendance_mark makes for the section a student sat in on a given day.
--
-- circular_recipient carries a plain student_id, not a guardian account: a guardian may have no
-- account at all (ADR-0017), so "delivered to a parent's inbox" is not a thing this build can mean
-- yet. student_id is the identifier that survives a guardian's account being created later — see
-- the module's own package-info.java for the full reasoning, and idx_circular_recipient_student
-- below for the query shape that reasoning points at.
--
-- No channel port, no queue and no delivery_status column: an in-app circular is not delivered
-- anywhere outside this database, so delivered_at is set the instant the row is created. See the
-- module's package-info.java for why this module does not register with ADR-0013's
-- NotificationChannel port.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table circular (
    id                        uuid        not null,
    title                     varchar(200) not null,
    body                      text        not null,
    requires_acknowledgement  boolean     not null default false,
    status                    varchar(20) not null default 'DRAFT',
    created_by                uuid        not null,
    published_by              uuid,
    published_at              timestamptz,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),

    constraint pk_circular primary key (id),
    constraint fk_circular_created_by foreign key (created_by) references user_account (id),
    constraint fk_circular_published_by foreign key (published_by) references user_account (id),

    constraint ck_circular_status check (status in ('DRAFT', 'PUBLISHED')),
    -- A published circular always names who and when; a draft names neither yet.
    constraint ck_circular_published_together check (
        (status = 'DRAFT' and published_by is null and published_at is null) or
        (status = 'PUBLISHED' and published_by is not null and published_at is not null)
    )
);

comment on table circular is
    'A circular composed for the school''s families. Internal under ADR-0014 — the content itself '
    'names no student. Mutable only while DRAFT; publish() is a one-way transition.';

-- A class, or one section of it, this circular was addressed to. Plain uuid columns with a
-- database foreign key, not Java associations — this module has no import of `academics` at all,
-- the same shape attendance_mark uses for its own section id.
create table circular_target (
    id          uuid        not null,
    circular_id uuid        not null,
    class_id    uuid        not null,
    -- null = every active section of class_id. Set = one specific section, which must belong to
    -- class_id (enforced in the application layer, where the roster to check it against lives).
    section_id  uuid,
    created_at  timestamptz not null default now(),

    constraint pk_circular_target primary key (id),
    constraint fk_circular_target_circular foreign key (circular_id) references circular (id),
    constraint fk_circular_target_class foreign key (class_id) references school_class (id),
    constraint fk_circular_target_section foreign key (section_id) references section (id)
);

-- At most one "every section of this class" target per circular, per class.
create unique index uq_circular_target_whole_class on circular_target (circular_id, class_id)
    where section_id is null;

-- At most one target naming this exact section, per circular.
create unique index uq_circular_target_section on circular_target (circular_id, section_id)
    where section_id is not null;

create index idx_circular_target_circular on circular_target (circular_id);

comment on table circular_target is
    'What a circular was addressed to. Survives publishing unchanged — see circular_recipient for '
    'who it actually reached.';

-- One actively enrolled student a published circular reached. Generated once, at publish time,
-- from the circular's targets; never regenerated if the roster changes afterwards.
create table circular_recipient (
    id                     uuid        not null,
    circular_id            uuid        not null,
    student_id             uuid        not null,
    -- The section the student sat in at publish time, not resolved from their enrolment at read
    -- time — the same "fixed at creation" choice attendance_mark.section_id makes, for the same
    -- reason: a later section move must not rewrite who a past circular reached.
    section_id             uuid        not null,
    -- Set the instant this row is created: there is no queue an in-app circular waits in.
    delivered_at           timestamptz not null default now(),
    -- No write path in this build. Table shape only, for the read-receipt a parent-facing screen
    -- will set once it exists — see the module's package-info.java.
    viewed_at              timestamptz,
    acknowledged_at        timestamptz,
    -- The user_account that recorded the acknowledgement — today always a staff member acting on
    -- a family's behalf (by phone, in writing, or in person). Unchanged in shape the day a
    -- guardian's own account can call the same endpoint for themselves.
    acknowledged_by        uuid,
    acknowledgement_note   varchar(500),
    created_at             timestamptz not null default now(),

    constraint pk_circular_recipient primary key (id),
    constraint fk_circular_recipient_circular foreign key (circular_id) references circular (id),
    constraint fk_circular_recipient_student foreign key (student_id) references student (id),
    constraint fk_circular_recipient_section foreign key (section_id) references section (id),
    constraint fk_circular_recipient_acknowledged_by foreign key (acknowledged_by) references user_account (id),

    -- An acknowledgement always names who recorded it; the absence of one means neither is set.
    constraint ck_circular_recipient_ack_together check (
        (acknowledged_at is null and acknowledged_by is null) or
        (acknowledged_at is not null and acknowledged_by is not null)
    )
);

-- One recipient row per student, per circular — publishing twice is refused before this could
-- ever be tested, but the constraint is the actual guarantee, not the application check.
create unique index uq_circular_recipient_student on circular_recipient (circular_id, student_id);

-- "This circular's own recipient list": the detail screen's own read.
create index idx_circular_recipient_circular on circular_recipient (circular_id);

-- The future parent-inbox query shape: "recipients where student_id in (my wards)". Unused by any
-- screen in this build; the index costs nothing to add now and everything to add under load later.
create index idx_circular_recipient_student on circular_recipient (student_id);

comment on table circular_recipient is
    'One student a published circular reached. Confidential under ADR-0014, the same tier as any '
    'other row naming a specific child. See the module''s package-info.java for what this row is '
    'today and how it becomes a parent-visible inbox later without a migration.';
