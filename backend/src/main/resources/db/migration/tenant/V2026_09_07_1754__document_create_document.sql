-- Documents: certificate, compliance and identity documents attached to a student (FR-013, FR-032,
-- ADR-0025).
--
-- Every column here is Confidential under ADR-0014, including document_type: see DocumentType's own
-- Javadoc for why a Restricted-category document type (a caste certificate, an Aadhaar copy) is
-- deliberately not one of the values this column may hold yet.
--
-- storage_key is TENANT-RELATIVE. It never contains this school's schema name — this row already
-- lives inside that schema, so recording it again would be redundant and could drift from it. The
-- storage adapter is what turns this into wherever the bytes really are (ADR-0025).
--
-- student_id is a plain uuid with a database foreign key, not a Java association: this module has
-- no import of `student` at all, the same shape student_enrolment uses for academic_session_id and
-- section_id. The foreign key is the whole of what stops a document naming a student this school
-- does not have; there is no application-level check to duplicate it.
--
-- Unqualified, like every tenant table: the schema is the boundary, and there is no school_id.

create table document (
    id                   uuid         not null,
    student_id           uuid         not null,
    document_type        varchar(30)  not null,
    issue_date           date,
    expiry_date          date,
    verification_status  varchar(20)  not null default 'UNVERIFIED',

    -- Tenant-relative — see the comment above. Never updated after creation: replacing the file is a
    -- new document, not an edit to this one (Document's class Javadoc).
    storage_key          varchar(300) not null,
    original_filename    varchar(255) not null,
    content_type         varchar(100) not null,
    size_bytes           bigint       not null,
    checksum_sha256      varchar(64)  not null,

    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),

    constraint pk_document primary key (id),
    constraint fk_document_student foreign key (student_id) references student (id),
    constraint ck_document_type check (document_type in (
        'BIRTH_CERTIFICATE', 'TRANSFER_CERTIFICATE', 'REPORT_CARD', 'PHOTO', 'SIGNATURE', 'OTHER'
    )),
    constraint ck_document_verification_status check (verification_status in (
        'UNVERIFIED', 'VERIFIED', 'REJECTED'
    )),
    constraint ck_document_dates check (expiry_date is null or issue_date is null or expiry_date > issue_date)
);

create index idx_document_student on document (student_id);

comment on table document is
    'A certificate, photo, signature or other document attached to a student. Confidential under '
    'ADR-0014; the bytes live behind platform.storage.StorageService, not in this table (ADR-0025).';
comment on column document.storage_key is
    'Tenant-relative. Never includes this school''s schema name — the row already lives inside it.';
