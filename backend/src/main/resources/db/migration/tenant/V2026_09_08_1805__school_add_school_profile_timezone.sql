-- A time zone for the school, so a date or time can be rendered honestly for a reader anywhere.
--
-- `docs/status.md` named the gap: the audit screen rendered every timestamp in the reader's own
-- device zone. India is one zone, so that was right for everyone reading from inside the country
-- and wrong only for someone reading from abroad — and the row detail named the zone so nobody was
-- misled, but naming the mistake is not the same as closing it. This column is what closes it.
--
-- Authoritative here, in the school's own schema, alongside every other field a school edits about
-- itself — not only in `public.school`. See `V2026_09_08_1806__school_add_school_timezone.sql` in
-- `shared` for why the registry keeps a copy too, and `docs/architecture/adr/0032-school-timezone.md`
-- for the full reasoning.
--
-- `not null default 'Asia/Kolkata'` rather than nullable: every school this product targets today is
-- in that one zone, so defaulting it is what lets nobody in India ever have to think about this
-- field, and it is what keeps every school already provisioned in a valid state the moment this
-- migration runs — a required column with no default would make all of them invalid instead.
alter table school_profile
    add column timezone varchar(50) not null default 'Asia/Kolkata';

comment on column school_profile.timezone is
    'An IANA zone id, e.g. Asia/Kolkata. Validated against java.time.ZoneId at the application layer (ADR-0032); this check only rules out values that cannot be one at all.';

-- Deliberately weaker than UpdateSchoolProfileRequest's own ZoneId.of(...) check, the same relationship
-- ck_school_profile_pincode already has with its request DTO: this is what holds when a write reaches
-- the table without going through the API, not the first line of defence.
alter table school_profile
    add constraint ck_school_profile_timezone check (timezone ~ '^[A-Za-z0-9_+/-]{1,50}$');
