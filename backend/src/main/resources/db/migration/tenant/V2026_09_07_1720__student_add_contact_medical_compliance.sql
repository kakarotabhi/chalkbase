-- Four more sections of the student record (ADR-0020, FR-028, FR-029, FR-033, FR-034), and the
-- Restricted columns ADR-0020 §2 deliberately left out, now that ADR-0022's encryption at rest
-- exists.
--
-- Each is its own table, one row per student, rather than more nullable columns on `student`:
--
--   * `student` is read on every list page and every enrolment lookup. These four sections are
--     read only when somebody opens the "Contact", "Medical", "Previous school" or "Compliance"
--     tab, so keeping them off the hot table means the common queries do not carry columns they
--     never select, and `student_medical` and `student_compliance` in particular stay entirely
--     free of ciphertext until a school actually enters something.
--   * A school onboarding today has none of this filled in on day one — it arrives from a UDISE+
--     drive, a health form, or a transfer certificate, each at its own time. A satellite table with
--     no row is "not entered yet"; a wide table with nine null columns says the same thing with
--     more columns to keep NULL-safe in every query that touches `student`.
--   * `student_medical` and `student_compliance` hold Restricted, encrypted columns
--     (ADR-0022) and `student_contact` / `student_transfer` do not. Keeping the encrypted columns
--     in their own tables is what makes "decrypt-and-count in a scheduled report" (ADR-0022's own
--     answer to UDISE+ aggregation) a scan of two small tables rather than of every column on the
--     student roll.
--
-- Each table's primary key IS the student id — a one-to-one, enforced by the key itself rather
-- than by a separate unique constraint, and a row exists only once a school has entered something
-- for that section.
--
-- `on delete cascade`: these tables have no life of their own once the student is gone. Deleting a
-- student is not a thing this module does (ADR-0020 §6) — the cascade exists so a test's cleanup
-- does not have to know these tables exist, not because production code ever deletes a student.

-- ── Contact (FR-028). Confidential, not Restricted: an address, a phone, an email — the same tier
-- as a guardian's. ──────────────────────────────────────────────────────────────────────────────
create table student_contact (
    student_id uuid        not null,
    address    text,
    phone      varchar(20),
    email      varchar(320),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pk_student_contact primary key (student_id),
    constraint fk_student_contact_student foreign key (student_id) references student (id) on delete cascade
);

-- ── Previous school and transfer certificate (FR-033). Confidential: it identifies where a child
-- came from, not what they are. ────────────────────────────────────────────────────────────────
create table student_transfer (
    student_id                     uuid        not null,
    previous_school_name           varchar(200),
    previous_school_board          varchar(60),
    transfer_certificate_number    varchar(60),
    transfer_certificate_issued_on date,
    -- Free text: "migration details" in FR-033 covers everything from a parent's transfer to a
    -- school closing, and a fixed list of reasons would refuse the one a clerk actually needs.
    reason_for_leaving             text,
    created_at                     timestamptz not null default now(),
    updated_at                     timestamptz not null default now(),
    constraint pk_student_transfer primary key (student_id),
    constraint fk_student_transfer_student foreign key (student_id) references student (id) on delete cascade
);

-- ── Medical (FR-034). Six Restricted, encrypted columns and three Confidential ones. ───────────
--
-- Blood group, CWSN/disability status, allergies, chronic conditions and medication are all health
-- data under ADR-0014's Restricted bucket ("biometrics, health and counselling records" —
-- disability/CWSN is named explicitly). Blood group is the arguable one: schools print it on an ID
-- card without a second thought, but it is still a health fact about a child, and ADR-0014 says to
-- pick the more protective tier when it is arguable. Encrypted here like the rest.
--
-- The emergency contact is a name and a phone number, not a health fact — Confidential, like a
-- guardian's, and not masked. It may be a person who is not on the guardian list at all (a
-- neighbour, an aunt), so it is its own three columns rather than a reference to `guardian`.
--
-- The encrypted columns are `text`, not a bounded `varchar`: the stored value is
-- `<key id>:<base64 of nonce || ciphertext>` (ADR-0022), which is longer than the plaintext and has
-- nothing useful for a database-level length limit to bound. A CHECK constraint on any of these six
-- would run against ciphertext and could not validate the plaintext anyway, which is why blood
-- group, for instance, is not constrained to a fixed list here the way `student.gender` is —
-- that validation lives in the DTO and the service, in Java, before the value is ever encrypted.
create table student_medical (
    student_id                 uuid        not null,
    blood_group                text,
    cwsn_status                text,
    disability_details         text,
    allergies                  text,
    chronic_conditions         text,
    medication                 text,
    emergency_contact_name     varchar(200),
    emergency_contact_phone    varchar(20),
    emergency_contact_relation varchar(60),
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    constraint pk_student_medical primary key (student_id),
    constraint fk_student_medical_student foreign key (student_id) references student (id) on delete cascade
);

-- ── Compliance and identifiers (FR-029, and the Restricted columns ADR-0020 §2 left out: caste
-- and community, religion, EWS/BPL/RTE category, APAAR). ──────────────────────────────────────
--
-- PEN/UDISE and the board registration number are identifiers, the same tier as
-- `student.admission_number` — Confidential, not Restricted, and not encrypted or masked.
--
-- APAAR is consent-based (compliance requirements, ADR-0014's consent section: "APAAR specifically
-- requires its own consent record"). `apaar_consent_given` and its two witnesses are what make that
-- a recorded fact rather than a checkbox nobody can prove was ticked: who gave it, and when.
-- `apaar_id` itself is written only once consent is recorded — enforced in the service, because a
-- CHECK constraint cannot see a value it never receives in the clear.
--
-- Aadhaar is deliberately absent. ADR-0020 §2 named it among the blocked columns, but FR-029 does
-- not ask for an Aadhaar reference on the student record — only PEN/UDISE, APAAR and the board
-- registration number — and adding a field nothing in the requirements calls for is exactly the
-- scope creep this module has otherwise been careful to avoid. Guardian income, the other field
-- ADR-0020 §2 named, is left out for the same reason: it lives on `guardian`, not `student`, and no
-- FR in this slice asks for it.
create table student_compliance (
    student_id                 uuid        not null,
    pen_udise_id                varchar(40),
    board_registration_number   varchar(40),
    caste_category               text,
    religion                     text,
    special_category             text,
    apaar_id                     text,
    apaar_consent_given          boolean     not null default false,
    apaar_consent_given_by       varchar(200),
    apaar_consent_given_at       timestamptz,
    created_at                   timestamptz not null default now(),
    updated_at                   timestamptz not null default now(),
    constraint pk_student_compliance primary key (student_id),
    constraint fk_student_compliance_student foreign key (student_id) references student (id) on delete cascade
);

comment on table student_contact is
    'Confidential under ADR-0014. One row per student, created on first save.';
comment on table student_transfer is
    'Confidential under ADR-0014. Previous school and transfer certificate (FR-033).';
comment on table student_medical is
    'Restricted and Confidential under ADR-0014. blood_group, cwsn_status, disability_details, allergies, chronic_conditions and medication are encrypted at rest (ADR-0022) and masked by default in the UI; the emergency contact columns are Confidential, in full, and not masked.';
comment on table student_compliance is
    'Restricted and Confidential under ADR-0014. caste_category, religion, special_category and apaar_id are encrypted at rest (ADR-0022) and masked by default in the UI. apaar_id must not be set without apaar_consent_given (enforced in the service, not here — the value is encrypted before it reaches this table).';
