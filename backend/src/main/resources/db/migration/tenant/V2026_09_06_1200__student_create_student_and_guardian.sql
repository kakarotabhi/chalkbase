-- Students, guardians and enrolment (ADR-0020).
--
-- Every column here is Confidential or lower under ADR-0014: names, dates of birth, addresses and
-- phone numbers. None of it may be logged, at any level, or appear in an error message.
--
-- What is NOT here is deliberate and is a recorded blocker rather than a scope choice: caste and
-- community, religion, disability/CWSN, EWS/BPL/RTE category, guardian income, APAAR and Aadhaar
-- are Restricted, which ADR-0014 requires to be encrypted at rest, masked in the UI and audited on
-- every read. None of that machinery exists yet, and adding the columns first would mean storing a
-- child's caste in plaintext in a table nobody has decided how to protect.

create table student (
    id               uuid         not null,
    -- Unique within this school, which is the only scope a schema can enforce (ADR-0020 §3).
    admission_number varchar(40)  not null,

    -- ONE name field, not three. A great many Indian students have no surname; many have a single
    -- name; a required "last name" box makes the office invent one, and what they invent goes on
    -- the certificate. This holds the name exactly as the boards will hold the school to it.
    full_name        varchar(200) not null,

    date_of_birth    date         not null,
    -- MALE / FEMALE / OTHER. `OTHER` rather than the government forms' "Transgender" because the
    -- mapping to a reporting format belongs in the export, not in the record — UDISE+ can change
    -- its vocabulary without a migration here.
    gender           varchar(16)  not null,

    -- Never deleted (ADR-0020 §6). Fees, attendance and marks all point here, and a school is
    -- legally required to produce these records years later.
    status           varchar(16)  not null default 'ACTIVE',

    admitted_on      date,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),

    constraint pk_student primary key (id),
    constraint uq_student_admission_number unique (admission_number),
    constraint ck_student_gender check (gender in ('MALE', 'FEMALE', 'OTHER')),
    constraint ck_student_status check (status in ('ACTIVE', 'INACTIVE', 'TRANSFERRED', 'GRADUATED', 'WITHDRAWN'))
);

create index idx_student_full_name on student (full_name);
create index idx_student_status on student (status);

-- A person, shared between siblings rather than copied per child (ADR-0020 §5). A guardian is a
-- record here and not an identity account (ADR-0017); an account is created only if one is needed.
create table guardian (
    id         uuid         not null,
    full_name  varchar(200) not null,
    phone      varchar(20),
    email      varchar(320),
    occupation varchar(120),
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint pk_guardian primary key (id)
);

create index idx_guardian_full_name on guardian (full_name);
create index idx_guardian_phone on guardian (phone);

create table student_guardian (
    id           uuid        not null,
    student_id   uuid        not null,
    guardian_id  uuid        not null,
    relation     varchar(20) not null,
    -- Who the school rings first. At most one per student, said in the database rather than hoped
    -- for in the application.
    is_primary   boolean     not null default false,
    created_at   timestamptz not null default now(),
    constraint pk_student_guardian primary key (id),
    constraint fk_student_guardian_student foreign key (student_id) references student (id),
    constraint fk_student_guardian_guardian foreign key (guardian_id) references guardian (id),
    constraint uq_student_guardian_pair unique (student_id, guardian_id),
    constraint ck_student_guardian_relation
        check (relation in ('FATHER', 'MOTHER', 'GUARDIAN', 'LOCAL_GUARDIAN', 'OTHER'))
);

create unique index uq_student_guardian_one_primary
    on student_guardian (student_id) where is_primary;
create index idx_student_guardian_guardian on student_guardian (guardian_id);

-- The year lives here, not on the class (ADR-0019, ADR-0020 §4). Promotion is a new row, so a
-- student's history is readable without consulting the audit log.
create table student_enrolment (
    id                  uuid        not null,
    student_id          uuid        not null,
    academic_session_id uuid        not null,
    section_id          uuid        not null,
    -- Assigned after admission, often after the class list settles, so nullable on purpose.
    roll_number         varchar(20),
    active              boolean     not null default true,
    enrolled_on         date        not null default current_date,
    created_at          timestamptz not null default now(),
    constraint pk_student_enrolment primary key (id),
    constraint fk_student_enrolment_student foreign key (student_id) references student (id),
    constraint fk_student_enrolment_session foreign key (academic_session_id) references academic_session (id),
    constraint fk_student_enrolment_section foreign key (section_id) references section (id),
    -- "Per class-section-session" from the requirements. A section belongs to exactly one class, so
    -- naming the section here says the same thing with one fewer column to keep consistent.
    constraint uq_student_enrolment_roll unique (academic_session_id, section_id, roll_number)
);

-- At most one ACTIVE enrolment per student per session. A partial index, because a student may
-- legitimately have an ended enrolment in the same year — moved section mid-term — and only the
-- live one is exclusive.
create unique index uq_student_enrolment_one_active
    on student_enrolment (student_id, academic_session_id) where active;

create index idx_student_enrolment_section on student_enrolment (academic_session_id, section_id);
create index idx_student_enrolment_student on student_enrolment (student_id);

comment on table student is
    'Confidential under ADR-0014. Never logged, never in an error message. Restricted category fields are deliberately absent until encryption at rest exists.';
comment on column student.full_name is
    'One field, exactly as the boards hold the school to it. Splitting it would make the office invent a surname (ADR-0020).';
comment on table guardian is
    'A person, shared between siblings. Not an identity account (ADR-0017).';
comment on table student_enrolment is
    'A student in a section for a session. Promotion is a new row, never an edit.';
