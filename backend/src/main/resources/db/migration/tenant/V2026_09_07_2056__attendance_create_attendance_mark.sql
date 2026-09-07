-- Daily and period-wise attendance (Phase 0 decision 8, ADR-0030).
--
-- ONE table for both grains, from day one. `period_number` and `subject_id` are null for the
-- daily grain a class teacher marks once a day (classes 1-8, and every school by default); a
-- period-wise mark (classes 9-12, subject teacher) fills both. Deferring the period-wise columns
-- to a later migration was rejected for the reason the decision itself gives: changing the grain
-- of a high-volume table after real attendance exists is the expensive kind of migration. Only the
-- daily grain has a write path in this build — see ADR-0030 for what "table shape and nothing
-- more" means for period-wise here.
--
-- This is the first genuinely high-volume table in the product: a thousand students times two
-- hundred school days is 200,000 rows per school per year. The two indexes below are chosen for
-- the two queries this module actually makes — one section on one date (the marking screen, and a
-- monthly section register) and one student across a date range (a student's own history, and the
-- eligibility/short-attendance reports FR-049 asks for and this lane does not build). Neither is a
-- text search, so neither risks the sequential scan docs/status.md records for guardian search.
--
-- student_id, academic_session_id, section_id and subject_id are plain uuid columns with a
-- database foreign key, not Java associations — the same shape student_enrolment uses for
-- academic_session_id and section_id, and document uses for student_id (ADR-0011, module map):
-- this module has no import of `student` or `academics` at all. marked_by and the two actor
-- columns on attendance_correction_request follow user_account.password_reset_by's own precedent
-- for the same reason, one module over (identity).
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table attendance_mark (
    id                   uuid        not null,
    student_id           uuid        not null,
    academic_session_id  uuid        not null,
    -- The section as of the day marked, not resolved from the student's enrolment at read time.
    -- A mid-year section move must not rewrite what a past date says about where a child sat.
    section_id           uuid        not null,
    attendance_date      date        not null,
    -- null = daily grain. 1.. = which period of the day, for the period-wise grain this table
    -- shapes but this lane does not build a write path for (ADR-0030).
    period_number        smallint,
    -- null for daily. Required alongside period_number for period-wise, enforced below.
    subject_id           uuid,
    status               varchar(20) not null,
    remarks              varchar(500),
    marked_by            uuid        not null,
    marked_at            timestamptz not null default now(),
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),

    constraint pk_attendance_mark primary key (id),
    constraint fk_attendance_mark_student foreign key (student_id) references student (id),
    constraint fk_attendance_mark_session foreign key (academic_session_id) references academic_session (id),
    constraint fk_attendance_mark_section foreign key (section_id) references section (id),
    constraint fk_attendance_mark_subject foreign key (subject_id) references subject (id),
    constraint fk_attendance_mark_marked_by foreign key (marked_by) references user_account (id),

    constraint ck_attendance_mark_status check (status in (
        'PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED_LEAVE', 'HOLIDAY'
    )),
    constraint ck_attendance_mark_period_number check (period_number is null or period_number between 1 and 10),
    -- A period-wise mark names its subject; a daily one carries neither. Half a pair (a period
    -- with no subject, or a subject with no period) is not a shape either grain produces.
    constraint ck_attendance_mark_period_subject check (
        (period_number is null and subject_id is null) or
        (period_number is not null and subject_id is not null)
    )
);

-- At most one daily mark per student per day.
create unique index uq_attendance_mark_daily on attendance_mark (student_id, attendance_date)
    where period_number is null;

-- At most one mark per student, per day, per period — the period-wise equivalent. Subject is not
-- part of this key: without a timetable module to say which subject a period is, the subject
-- marked for period 3 today is whatever the marking teacher named, and the key that actually
-- identifies a period-wise mark is the period itself.
create unique index uq_attendance_mark_period on attendance_mark (student_id, attendance_date, period_number)
    where period_number is not null;

-- "One section on one date": the marking screen's own read, and a monthly section register.
create index idx_attendance_mark_section_date on attendance_mark (section_id, attendance_date);

-- "One student across a session": a child's own history, and the short-attendance / 75% board
-- eligibility reports FR-049 asks for — both a range scan on this index once the caller has the
-- session's start and end dates from academics.api.AcademicsLookup.
create index idx_attendance_mark_student_date on attendance_mark (student_id, attendance_date);

comment on table attendance_mark is
    'A student''s attendance for one day (daily grain) or one period (period-wise grain). '
    'Confidential under ADR-0014 — an absence pattern is sensitive information about a family. '
    'Mutable until end-of-day-plus-24h, then append-only via attendance_correction_request '
    '(Phase 0 decision 8, ADR-0030).';
comment on column attendance_mark.period_number is
    'null for the daily grain. This lane ships no write path for a non-null value — see ADR-0030.';

-- A correction to a mark once it has locked (end of the attendance day plus 24 hours, computed at
-- request time rather than stored — there is no scheduled job here, only the passage of time).
-- Before that point a mark is edited in place through the same marking endpoint; this table exists
-- for what happens after. previous_status is a snapshot taken when the request is filed, kept here
-- rather than in the audit log: ADR-0018 never lets the audit log hold a value, so "what did it
-- used to say" has to live in ordinary domain data if it is to be recoverable at all, and this row
-- is that data. The audit log still gets its own entry for the original mark and a second one for
-- the correction being applied (field names only), which is what keeps both "in the audit log" as
-- the decision requires.
create table attendance_correction_request (
    id                uuid         not null,
    attendance_mark_id uuid        not null,
    previous_status   varchar(20)  not null,
    requested_status  varchar(20)  not null,
    reason            varchar(500) not null,
    requested_by      uuid         not null,
    requested_at      timestamptz  not null default now(),
    decision          varchar(20)  not null default 'PENDING',
    decided_by        uuid,
    decided_at        timestamptz,
    decision_note     varchar(500),
    created_at        timestamptz  not null default now(),

    constraint pk_attendance_correction_request primary key (id),
    constraint fk_attendance_correction_mark foreign key (attendance_mark_id) references attendance_mark (id),
    constraint fk_attendance_correction_requested_by foreign key (requested_by) references user_account (id),
    constraint fk_attendance_correction_decided_by foreign key (decided_by) references user_account (id)
        on delete set null,
    constraint ck_attendance_correction_status check (previous_status in (
        'PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED_LEAVE', 'HOLIDAY'
    )),
    constraint ck_attendance_correction_requested_status check (requested_status in (
        'PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED_LEAVE', 'HOLIDAY'
    )),
    constraint ck_attendance_correction_decision check (decision in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_attendance_correction_decided_together check (
        (decision = 'PENDING' and decided_by is null and decided_at is null) or
        (decision <> 'PENDING' and decided_by is not null and decided_at is not null)
    )
);

-- At most one open request per mark. A teacher who wants to change a second time waits for the
-- admin's decision on the first, rather than the queue holding two competing answers for one row.
create unique index uq_attendance_correction_one_pending on attendance_correction_request (attendance_mark_id)
    where decision = 'PENDING';

-- The admin's queue: pending requests, oldest first. Low volume compared to attendance_mark itself
-- (a correction is the exception, not the rule), so one index carries both the filter and the sort.
create index idx_attendance_correction_pending on attendance_correction_request (decision, requested_at);

comment on table attendance_correction_request is
    'A request to change a locked attendance_mark, and an admin''s decision on it. Confidential, '
    'same tier as the mark it corrects.';
