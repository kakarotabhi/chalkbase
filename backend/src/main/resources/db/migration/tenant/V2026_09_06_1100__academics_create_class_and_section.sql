-- Classes and sections (ADR-0019).
--
-- Structural, not session-scoped: a class and a section are facts about the school, and the
-- academic session appears on whatever references them — enrolment first, and later the
-- class-teacher assignment, which genuinely changes every year.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

-- `school_class`, not `class`. `class` is a Java keyword so the entity must be called something
-- else whatever the table is named, and a table whose name does not match its entity is a small
-- confusion repeated forever. The same reasoning made `user` into `user_account`.
create table school_class (
    id         uuid        not null,
    name       varchar(40) not null,
    -- Orders the ladder: Nursery before Class 1, Class 10 before Class 11. A plain integer rather
    -- than a parsed name, because "V", "Class 5" and "Grade 5" are all the same rung and a school
    -- may call it any of them.
    sequence   integer     not null,
    -- Deactivated, never deleted, once anything references it (ADR-0019). Present from the first
    -- migration rather than retrofitted: by the time a student's enrolment names a class, deciding
    -- that deleting it was a mistake is too late.
    active     boolean     not null default true,
    created_at timestamptz not null default now(),
    constraint pk_school_class primary key (id),
    constraint uq_school_class_name unique (name),
    -- Deferrable so that swapping two classes' positions is one transaction rather than a dance
    -- through a temporary value. Reordering happens whenever a school inserts Pre-Nursery below
    -- what it thought was the bottom rung.
    constraint uq_school_class_sequence unique (sequence) deferrable initially deferred
);

create table section (
    id              uuid        not null,
    school_class_id uuid        not null,
    name            varchar(20) not null,
    active          boolean     not null default true,
    created_at      timestamptz not null default now(),
    constraint pk_section primary key (id),
    constraint fk_section_school_class foreign key (school_class_id) references school_class (id),
    -- "A" means something only inside its class: every class has one, and they are different rooms.
    constraint uq_section_name_in_class unique (school_class_id, name)
);

create index idx_section_class on section (school_class_id);

comment on table school_class is
    'A rung of this school''s ladder — Nursery, LKG, Class 5. Structural, not per session (ADR-0019).';
comment on column school_class.sequence is
    'Sort position. Negative values are conventional for pre-primary rungs below Class 1.';
comment on table section is
    'A division of a class — A, B, Blue. Deactivated rather than deleted once referenced.';
