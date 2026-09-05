# ADR-0017: Identity — per-school accounts, platform sessions

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0003](0003-authentication-and-authorization.md) (authentication), [ADR-0011](0011-schema-per-tenant.md) (tenancy), [ADR-0005](0005-authorization-model.md) (authorization)

## Context

[ADR-0003](0003-authentication-and-authorization.md) settled server-side sessions, in-app identity
and pluggable credentials. [ADR-0011](0011-schema-per-tenant.md) then made the tenant a PostgreSQL
schema, which creates a problem ADR-0003 did not have to answer:

**A session cookie has to resolve to a school before a tenant can be bound, but the user record that
the session points at lives inside a tenant.** Something has to break the circle.

## Decisions

### 1. Accounts live inside each school's schema

`user_account` and its identifiers and credentials are per-tenant tables. Nothing about a person —
not even a username — sits in `public`.

The alternative was a global `public.user_account` carrying a school pointer, which gives a simpler
login form. It was rejected: it puts a row-level tenant boundary back into the one table that holds
credentials, and it forces usernames to be globally unique across unrelated schools.

The cost is real and is accepted: **the school must be known before authenticating.** Login takes a
school code alongside the username, remembered on the device after the first sign-in so a parent
types it once.

### 2. Sessions live in `public`, and are the only exception

The session store cannot be per-tenant, because reading the cookie is what tells us which tenant to
bind. `public` therefore holds the session table and nothing else about a person: a session id, the
school, the user id, timestamps. No name, no contact detail, no student link.

That is the whole exception. If anything else is ever proposed for `public` on the grounds that
"sessions are there too", the answer is no — sessions are there because of a genuine circular
dependency, not as a precedent.

### 3. Parents sign in with a school-issued username

The school issues a username, usually the admission number, and a temporary password that must be
changed on first sign-in. A meaningful number of parents in an Indian K-12 school have no email
address they read, so email as the primary identifier turns the school office into the fallback for
everyone.

Because ADR-0003 separated identity from proof, phone plus OTP later is an additional
`user_identifier` row and an additional `user_credential` verifier — not a migration.

### 4. A guardian is a person record, not an account

`guardian` is a record in the student module — name, relation, contact, occupation — linked to
students through `student_guardian`. An account is created only when a guardian actually needs to
sign in.

Most parents never will. Creating a dormant account for every guardian would put thousands of rows
that can never authenticate into the identity tables, and it would put guardian data somewhere other
than next to the student data it describes.

The parent-who-is-also-a-teacher case is then two records for one human, deliberately: a staff
account and a guardian record. They are different relationships with the school and have different
lifecycles.

## The table is `user_account`, not `user`

`user` is a reserved word, and PostgreSQL's behaviour here is a trap rather than an error:

```
create table kwtest.user (id int)   -- succeeds
select * from user                  -- returns "postgres" — the current_user FUNCTION
select id from user u               -- ERROR: column "id" does not exist
```

The table is created happily, and only later does a query silently return the wrong thing. Verified
on the development database before choosing the name.

## Shape

```
public
├── spring_session              Spring Session JDBC, per ADR-0003
└── spring_session_attributes   carries the bound schema and the authenticated principal

<tenant>
├── user_account          id, status, must_change_password, failed_attempts, locked_until
├── user_identifier       user_account_id, type (USERNAME|EMAIL|PHONE), value, verified_at
└── user_credential       user_account_id, type (PASSWORD|OTP|OIDC), secret, status
```

The session store is Spring Session's own JDBC schema rather than a hand-rolled table — ADR-0003
chose Spring Session, and session management is not a thing to write by hand. The tenant's schema
name rides as a session attribute, which is what the request filter reads before binding.

Login: school code → `public.school` → bind tenant → `user_identifier` → `user_credential` → create
session in `public`.

Every later request: session cookie → `public.user_session` → bind tenant → proceed.

## Consequences

- **The login form has three fields, not two.** The school code is remembered per device, so it is a
  one-time cost for a parent and a permanent one for anyone using a shared machine at the school
  office.
- **A user cannot span schools.** A teacher working at two campuses of one trust needs an account at
  each. Acceptable while Phase 0 scopes the product to single-campus schools; revisit with groups.
- **Password reset needs the school office** until a verified email or phone exists. That is how
  Indian schools already work, and it is why FR-006 lists administrator-triggered reset.
- **Session cleanup is a platform job.** Expired rows in `public.user_session` need a scheduled
  purge; without one the table grows for the lifetime of the installation.
