-- Admission enquiries: FR-016 (capture), FR-017 (status, counsellor assignment, follow-up
-- history). This is the front box of Phase 0 decision §5's pipeline
-- (Enquiry -> Application -> Document verification -> Screening outcome -> Approval ->
-- Admission fee -> Student record created) and nothing past it: there is no application table
-- here, and there will not be one until a later lane builds it.
--
-- interested_class_id and assigned_counsellor_id are plain uuid columns with a database foreign
-- key, not Java associations, the same shape document.student_id and attendance_mark.section_id
-- use for the tables they point at (ADR-0011, module map): this module has no import of
-- `academics` or `identity` at all, only their named interfaces.
--
-- assigned_counsellor_id is NOT NULL, deliberately: an enquiry with nobody assigned to it is the
-- mailbox this module exists to prevent (see enquiry.package-info and Enquiry's own Javadoc), so
-- the table refuses to hold one rather than merely discouraging it in the API.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table enquiry (
    id                     uuid         not null,
    child_full_name        varchar(200) not null,
    child_date_of_birth    date,
    -- Optional: a walk-in may not yet know, or care, which class. References school_class(id),
    -- which this same tenant schema already holds (academics is created before this migration).
    interested_class_id    uuid,
    parent_name            varchar(200) not null,
    parent_phone           varchar(20)  not null,
    parent_email           varchar(200),
    source                 varchar(20)  not null,
    status                 varchar(20)  not null default 'NEW',
    -- Required. See the class comment above and Enquiry's own Javadoc for why this is NOT NULL
    -- rather than merely recommended.
    assigned_counsellor_id uuid         not null,
    -- Defaults to the day the row is captured (see Enquiry's constructor), so a fresh enquiry is
    -- due for its first follow-up immediately rather than after somebody remembers to schedule
    -- one. Cleared to null the moment the enquiry closes (CONVERTED or LOST) so a closed enquiry
    -- stops asking anyone to follow up on it — see Enquiry.applyFollowUp.
    next_follow_up_date    date,
    remarks                varchar(1000),
    captured_by            uuid         not null,
    created_at             timestamptz  not null default now(),
    updated_at             timestamptz  not null default now(),

    constraint pk_enquiry primary key (id),
    constraint fk_enquiry_class foreign key (interested_class_id) references school_class (id),
    constraint fk_enquiry_counsellor foreign key (assigned_counsellor_id) references user_account (id),
    constraint fk_enquiry_captured_by foreign key (captured_by) references user_account (id),

    constraint ck_enquiry_source check (source in (
        'WALK_IN', 'PHONE', 'WEBSITE', 'REFERRAL', 'CAMPAIGN', 'IMPORTED'
    )),
    constraint ck_enquiry_status check (status in ('NEW', 'IN_PROGRESS', 'CONVERTED', 'LOST')),
    -- A closed enquiry (CONVERTED or LOST) never carries a pending follow-up date; an open one
    -- (NEW or IN_PROGRESS) always does, once it has been captured at all. Enforced in the domain
    -- (Enquiry.applyFollowUp) and restated here so the invariant the due-date queue relies on —
    -- "every row it returns has a date" — cannot be violated by a write outside that path either.
    constraint ck_enquiry_follow_up_date_matches_status check (
        (status in ('CONVERTED', 'LOST') and next_follow_up_date is null) or
        (status in ('NEW', 'IN_PROGRESS') and next_follow_up_date is not null)
    )
);

-- The list screen's own filters.
create index idx_enquiry_status on enquiry (status);
create index idx_enquiry_assigned_counsellor on enquiry (assigned_counsellor_id);

-- The due-date follow-up queue's own read: open enquiries whose next follow-up date has arrived,
-- one counsellor's or the whole school's, oldest due date first. Leading on the date rather than
-- the counsellor because "mine" and "everyone's" share the same predicate on this column and only
-- differ by an optional equality filter on assigned_counsellor_id, which this index also carries.
create index idx_enquiry_next_follow_up on enquiry (next_follow_up_date, assigned_counsellor_id);

comment on table enquiry is
    'A prospective student''s enquiry (FR-016, FR-017) -- the first box of the admission pipeline '
    '(Phase 0 decision 5) and the only one this lane builds. Confidential under ADR-0014: a '
    'child''s name and a parent''s phone number.';
comment on column enquiry.assigned_counsellor_id is
    'Required. An unassigned enquiry is the mailbox this module exists to prevent.';
comment on column enquiry.next_follow_up_date is
    'Null only while the enquiry is closed (CONVERTED or LOST). Defaults to the capture date, so a '
    'fresh enquiry is due immediately -- see the migration comment above.';

-- One dated note against an enquiry (FR-17's "follow-up history"). Append-only: no update or
-- delete path exists on this table, the same discipline attendance_correction_request applies to
-- a decided correction -- a counsellor's record of a call should never quietly change afterwards.
create table enquiry_follow_up (
    id                   uuid         not null,
    enquiry_id           uuid         not null,
    note                 varchar(1000) not null,
    next_follow_up_date  date,
    resulting_status     varchar(20),
    recorded_by          uuid         not null,
    recorded_at          timestamptz  not null default now(),

    constraint pk_enquiry_follow_up primary key (id),
    constraint fk_enquiry_follow_up_enquiry foreign key (enquiry_id) references enquiry (id),
    constraint fk_enquiry_follow_up_recorded_by foreign key (recorded_by) references user_account (id),
    -- No 'NEW' here, deliberately -- a follow-up can only move an enquiry forward or close it
    -- (Enquiry.applyFollowUp, AdmissionErrorCode.STATUS_CANNOT_REOPEN_TO_NEW), restated at the
    -- database the same way the check above restates the domain's own next-follow-up-date rule.
    constraint ck_enquiry_follow_up_resulting_status check (
        resulting_status is null or resulting_status in ('IN_PROGRESS', 'CONVERTED', 'LOST')
    )
);

-- The detail screen's own read: one enquiry's whole history, newest first.
create index idx_enquiry_follow_up_enquiry on enquiry_follow_up (enquiry_id, recorded_at desc);

comment on table enquiry_follow_up is
    'One dated, append-only note against an enquiry: who followed up, when, what was said, and '
    'what happens next. Confidential under ADR-0014, same tier as the enquiry it belongs to.';
