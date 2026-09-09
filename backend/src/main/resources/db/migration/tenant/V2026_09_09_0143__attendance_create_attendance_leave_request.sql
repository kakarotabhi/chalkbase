-- Leave requests: a guardian-or-teacher's advance notice that a student will be away, and an
-- authorised person's decision on it (FR-047).
--
-- Deliberately its own table, with NO foreign key to attendance_mark in either direction, and
-- approving a request never writes one. A leave request is filed *before* a day happens and
-- reasons about a date range; a mark is filed *for* a day, one date at a time, and
-- MarkAttendanceRequest already refuses a future one (ATT_002) — so an approval cannot pre-write
-- attendance_mark for the dates it covers even if this lane wanted it to, and the roster a mark
-- validates against is only known as of the day itself, not in advance. Instead,
-- AttendanceMarkingService.view reads this table at the moment a section's register is opened, for
-- the date opened, and offers EXCUSED_LEAVE as an unmarked student's default; the teacher's own
-- Save is still what writes attendance_mark. See the ADR-0030 amendment for the full reasoning.
--
-- A date already in the past is refused at the service layer (ATT_010) rather than accepted here
-- and routed anywhere: this module already has a correction-request workflow
-- (attendance_correction_request) for a day that has already been marked, and a second, parallel
-- approval mechanism for "something already happened" is exactly the maintainability trap two
-- almost-identical flows create.
--
-- student_id, section_id and academic_session_id are plain uuid columns with a database foreign
-- key, not Java associations — the same shape attendance_mark itself uses, and for the same reason
-- (ADR-0011, module map): this module has no import of `student` or `academics` at all.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table attendance_leave_request (
    id                   uuid         not null,
    student_id           uuid         not null,
    -- The section the student was on as of filing, the same reasoning attendance_mark.section_id
    -- gives: fixed at creation so a mid-year section move does not rewrite which class a past
    -- request named.
    section_id           uuid         not null,
    academic_session_id  uuid         not null,
    start_date           date         not null,
    end_date             date         not null,
    reason               varchar(500) not null,
    requested_by         uuid         not null,
    requested_at         timestamptz  not null default now(),
    decision             varchar(20)  not null default 'PENDING',
    decided_by           uuid,
    decided_at           timestamptz,
    decision_note        varchar(500),
    created_at           timestamptz  not null default now(),

    constraint pk_attendance_leave_request primary key (id),
    constraint fk_attendance_leave_request_student foreign key (student_id) references student (id),
    constraint fk_attendance_leave_request_section foreign key (section_id) references section (id),
    constraint fk_attendance_leave_request_session foreign key (academic_session_id) references academic_session (id),
    constraint fk_attendance_leave_request_requested_by foreign key (requested_by) references user_account (id),
    constraint fk_attendance_leave_request_decided_by foreign key (decided_by) references user_account (id)
        on delete set null,

    constraint ck_attendance_leave_request_dates check (end_date >= start_date),
    constraint ck_attendance_leave_request_decision check (decision in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint ck_attendance_leave_request_decided_together check (
        (decision = 'PENDING' and decided_by is null and decided_at is null) or
        (decision <> 'PENDING' and decided_by is not null and decided_at is not null)
    )
);

-- The queue: requests in one decision state, oldest first — pending is the common read.
create index idx_attendance_leave_request_pending on attendance_leave_request (decision, requested_at);

-- "does an approved request cover this student on this date": AttendanceMarkingService.buildView's
-- own read, once per section-day opened, against however many students are on the roster.
create index idx_attendance_leave_request_student_dates on attendance_leave_request (student_id, start_date, end_date);

comment on table attendance_leave_request is
    'A guardian-or-teacher''s advance notice that a student will be away, and an authorised '
    'person''s decision on it (FR-047). Confidential, same tier as attendance_mark. Approving does '
    'not write attendance_mark directly -- see the ADR-0030 amendment.';
comment on column attendance_leave_request.reason is
    'Confidential, not Restricted, even though it may name a medical condition -- see '
    'LeaveRequestResponse''s Javadoc for why.';
