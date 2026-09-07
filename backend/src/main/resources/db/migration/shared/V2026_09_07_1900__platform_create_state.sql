-- Indian states and union territories: global reference data (ADR-0006, ADR-0029).
--
-- Lives in `public`, not a school's schema — it is the same list for every school and does not
-- belong to any one of them (module-map.md: "Global reference data ... lives in `platform` and is
-- created in the `public` schema by `db/migration/shared`").
--
-- This migration creates the table. It inserts no rows. The shipped list of states lives in
-- `ReferenceDataSeeder`/`IndianStates` (Java), copied in here at every startup the same way
-- `PermissionSeeder` copies the permission catalogue into `permission` — because a state that gets
-- renamed (Orissa -> Odisha, 2011) or a union territory that gets created or merged (Ladakh split
-- from Jammu and Kashmir in 2019; Dadra and Nagar Haveli merged with Daman and Diu in 2020) is a
-- one-line change to a reviewable Java list and a redeploy, not a migration that edits a row a
-- previous migration inserted — which this project's migrations may never do once merged.
--
-- `code` is the primary key, not a generated id, for the same reason `permission.code` is: this is
-- a small, code-seeded catalogue, not a school-authored row a user creates through a screen and
-- might rename in place (that is `subject`, which does need a surrogate id). `code` is a short,
-- permanent identifier this project assigns once in `IndianStates` and never derives from `name` at
-- seed time, so correcting a name's spelling later is an update keyed on something that did not
-- change.
--
-- No `active` column: nothing references `state.code` yet (school_profile.state is still a plain
-- string, matching what it already stored before this table existed), so there is nothing for a
-- retired row to protect. A code the shipped list stops mentioning is simply left in the table
-- rather than deleted — deleting on a seeder's say-so is exactly the kind of automatic destructive
-- step a reference table should not perform on its own — but it is not expected to matter in
-- practice: India has not removed a state or union territory outright since independence, only
-- renamed or reorganised one.
create table state (
    code varchar(40)  not null,
    name varchar(100) not null,
    constraint pk_state primary key (code),
    constraint uq_state_name unique (name)
);

comment on table state is
    'Indian states and union territories: shared Tier-1 reference data (ADR-0006), seeded from '
    'IndianStates.java at every startup (ReferenceDataSeeder), the same pattern PermissionSeeder '
    'uses for the permission catalogue. Read through GET /api/reference/states; no write endpoint.';
comment on column state.code is
    'A short, permanent identifier this project assigns once. Not an external standard (ISO 3166-2 '
    'or otherwise) and not asserted to match one — it exists only so a rename can update `name` '
    'without changing what a future foreign key would point at.';
