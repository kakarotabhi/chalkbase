# ADR-0003: Authentication

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md) covers authorization

## Context

Chalkbase serves staff, parents and students on one deployment. FR-006 requires activation,
deactivation, password reset, **forced logout** and account lockout. FR-007 wants optional MFA for
staff. The requirement pack leaves the login method open, and parents in Indian schools generally
expect phone + OTP rather than a password.

The product must start simple without painting itself into a corner: the first release uses
username and password, and OTP, SSO and MFA must be addable later without touching modules that
merely consume the session.

## Decisions

### 1. Server-side sessions, not JWT

Login creates a server-side session (Spring Session, JDBC-backed) referenced by an `HttpOnly`,
`Secure`, `SameSite=Lax` cookie.

Forced logout and lockout are hard requirements. With JWT they mean building a revocation list
checked on every request — which is a session store with extra steps and worse failure modes. There
is one deployment and one origin, so JWT's stateless advantage buys nothing here.

Sessions live in PostgreSQL, not in memory, so a restart does not log every parent out.

### 2. Identity lives in Chalkbase, not in Keycloak

A dedicated identity server would take 0.5-1 GB on a VPS already running the database, the backend
and the frontend. More importantly, FR-005's attribute restrictions (this teacher, these sections,
those students) cannot live in an external directory without splitting the source of truth. So the
`identity` module owns users, credentials, sessions, roles and grants.

External SSO stays possible: it becomes an additional credential type (below), not a migration.

### 3. Credentials are pluggable — this is what makes upgrading cheap

The schema separates **who you are** from **how you prove it**:

```
user                     one row per human. No password column.
  └── user_identifier    (type: USERNAME | EMAIL | PHONE, value, verified_at)
  └── user_credential    (type: PASSWORD | OTP | OIDC | PASSKEY, secret/reference, status)
```

- A user may hold several identifiers and several credentials.
- Authentication is a `CredentialVerifier` per type behind one interface. Adding OTP later is a new
  verifier plus a new endpoint — no change to sessions, permissions, or any other module.
- Session creation goes through a single `SessionService`, so an MFA step-up slots in as an extra
  state in that one place.

Two details that specifically avoid a painful migration later:

- The login identifier is a `user_identifier` row from the start, **not** an `email` column on
  `user`. Phone login later needs no change to the user table.
- Password hashing is behind Spring Security's `DelegatingPasswordEncoder` with an algorithm prefix
  stored in the hash, so the algorithm can be upgraded by re-hashing on next login.

### 4. First release: username and password

Staff, parents and students all log in with a username (or email) and a password issued by the
school. Lockout after repeated failures, forced password change on first login, and admin-triggered
reset.

Phone + OTP is deferred, not designed out. When it is scheduled, the blocking item is not code:
transactional SMS in India requires TRAI **DLT registration** — entity, sender ID and per-template
approval — which takes weeks of paperwork. Start that before the code is needed.

## Consequences

- Sessions must be in PostgreSQL before the first multi-user deployment; in-memory sessions are a
  restart away from an outage.
- Every authenticated request now has a subject, which is what lets `TenantContext` (ADR-0002) stop
  using its development-only resolver.
- MFA (FR-007) and SSO remain open, but as additive work behind interfaces that exist from day one.
- Cookie-based auth means CSRF protection has to be switched on for state-changing endpoints; it is
  currently disabled in the scaffold's `SecurityConfig`.
