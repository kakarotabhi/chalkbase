-- The school's audit log (ADR-0018, FR-008).
--
-- One generic table, not Hibernate Envers. Envers creates an _AUD table per audited entity, and
-- ADR-0011 names table count as the ceiling of schema-per-tenant — sixty tables across five hundred
-- schools is thirty thousand, and Envers would roughly double it. This costs one table per school.
--
-- Field NAMES are recorded, values are not. ADR-0014 says Restricted and Confidential data is never
-- logged, and a table holding before-and-after values of every edit would be a complete,
-- permanently retained second copy of every student record — the largest concentration of
-- children's data in the system, and the one nobody thinks of as a database.

create table audit_event (
    id             uuid         not null,
    occurred_at    timestamptz  not null default now(),

    -- Snapshots, deliberately denormalised and deliberately not foreign keys. An audit row must
    -- still read correctly after the account is renamed, its roles change, or it is deleted. A
    -- reference to a mutable row would let the past change.
    actor_id       uuid,
    actor_name     varchar(200),
    actor_roles    varchar(400),

    action         varchar(60)  not null,
    entity_type    varchar(60),
    entity_id      varchar(100),

    -- Which fields changed. Never what they changed to (ADR-0014).
    changed_fields text,

    outcome        varchar(16)  not null default 'SUCCESS',
    ip_address     varchar(45),
    user_agent     varchar(400),

    -- The same id the ADR-0007 envelope returns, so a trace id quoted off an error screen leads
    -- straight to the audited action.
    trace_id       varchar(64),

    constraint pk_audit_event primary key (id),
    constraint ck_audit_event_outcome check (outcome in ('SUCCESS', 'FAILURE', 'DENIED'))
);

-- The three questions actually asked of an audit log: what happened recently, what did this person
-- do, and what happened to this record.
create index idx_audit_event_occurred on audit_event (occurred_at desc);
create index idx_audit_event_actor on audit_event (actor_id, occurred_at desc);
create index idx_audit_event_entity on audit_event (entity_type, entity_id, occurred_at desc);

comment on table audit_event is
    'Append-only. No update or delete endpoint exists or should. Retention is a scheduled platform job (ADR-0018).';
comment on column audit_event.ip_address is
    'Personal data under the DPDP Act. Inherits the audit log retention period; never kept indefinitely.';
comment on column audit_event.changed_fields is
    'Field names only. Recording values would make this an unencrypted copy of every student record.';
