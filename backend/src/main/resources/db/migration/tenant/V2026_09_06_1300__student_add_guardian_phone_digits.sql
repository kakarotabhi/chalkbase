-- The guardian search, made to actually match a phone number (ADR-0020 §5).
--
-- The problem this fixes is a real one in shipped code, not a refinement. The directory search ran
-- `phone like '%q%'` against the raw stored value, so a guardian entered as `+919876543210` did not
-- match a clerk typing `98765 43210`, and one entered as `+91 98765 43210` did not match
-- `9876543210`. A phone number is the one thing that tells two Sureshes apart when a family shares
-- a surname, so the search failed in exactly the case the shared-guardian model needs it to work:
-- the office concludes the father is not here and types him in a second time, and from then on his
-- four children hold four numbers that can disagree.
--
-- Digits compared to digits. The column holds only the digits of `phone`, and the search strips its
-- own term the same way before comparing. A `like '%digits%'` then gets the country-code case for
-- free — the local ten digits are a suffix of the same number stored with a `+91` in front.
--
-- GENERATED ALWAYS ... STORED, not a trigger and not an application field, for three reasons: it
-- cannot drift from `phone`, no code can write it or forget to, and a row corrected by hand in psql
-- is corrected here too. `regexp_replace(text, text, text, text)` is IMMUTABLE, which is what makes
-- it legal in a generated expression at all.
--
-- What this deliberately does NOT do is normalise what is stored. `phone` keeps the number exactly
-- as the school typed it, spaces, country code and all, because that is what the office reads back
-- off the screen and dials. Rewriting it to E.164 on write would mean guessing a country code for
-- every number that has none, and the guess would be wrong for the boarding school's overseas
-- parents. Only the search is normalised.
alter table guardian
    add column phone_digits text
        generated always as (regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g')) stored;

-- There is deliberately NO index on this column, and the reason is worth writing down because
-- adding one looks obviously right. `like '%digits%'` is an unanchored match and a btree index
-- cannot serve it — the index would be created, would never be used, and would read to the next
-- person as though the search were indexed. A school has a few thousand guardians and the
-- sequential scan is not what anyone will notice; under ADR-0011 the same object would also be
-- created once per school, forever, for nothing.
--
-- The index that WOULD serve this is a `pg_trgm` GIN index. `pg_trgm` is available on the dev
-- database and not installed, and installing it is a database-wide change rather than a tenant
-- migration — a decision to take when the scan actually hurts, with a measurement behind it.

comment on column guardian.phone_digits is
    'Database-maintained digits of phone, for searching only. Never written by the application; `phone` remains the number as the school typed it (ADR-0020 §5).';
