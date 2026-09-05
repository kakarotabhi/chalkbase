# ADR-0005: Authorization — permissions, roles and scopes

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0002](0002-multi-tenancy-strategy.md) (tenancy), [ADR-0003](0003-authentication-and-authorization.md) (authentication)

## Context

Two requirements pull in different directions:

- **FR-004** — role-based permissions for every module and action.
- **FR-005** — attribute restrictions by campus, class, section, subject, department and *assigned
  students*.

And a third constraint the requirement pack states plainly: the permission model is shared by every
other module, so it is the most expensive thing in the system to change later. There are 16 modules
and roughly a dozen role archetypes, and **no two schools split those roles the same way**. One
school lets the accountant approve concessions; the next insists only the principal may. If that
difference needs a code change, the product does not scale past a handful of schools.

## Options considered

1. **Role checks in code** — `hasRole('ACCOUNTANT')` at each endpoint.
   Simple, and wrong for this product. Every school-specific variation becomes a code change or a
   new role. Roles multiply combinatorially ("teacher who is also transport in-charge"), and
   renaming a role needs a deploy.

2. **A policy engine** (OPA, Cedar, a rules DSL).
   Maximum expressiveness, and the wrong shape here. It adds a policy language and a second source
   of truth, makes "why can't I see this?" genuinely hard to answer, and defeats the acceptance note
   that says *admins must be able to preview permission impact before assigning a role*. A principal
   configuring access in a UI cannot reason about a policy language.

3. **Permission-based RBAC with scoped grants** — permissions are the unit of enforcement, roles are
   configurable bundles of them, and each grant carries a scope.

## Decision

**Option 3.** Concretely, four concepts and one rule.

### The rule

> **Permissions are code. Roles are data.**

A permission is a promise the code makes about a specific action, so it is defined in the codebase
and cannot be invented by a user. A role is an opinion about who should do what, which differs per
school, so it is data that schools edit. Code checks **permissions only** — a role name must never
appear in an authorization check.

### 1. Permission

Identifier shaped `<module>:<resource>:<action>` — `fee:invoice:create`, `attendance:mark:write`,
`student:profile:read`, `exam:result:publish`.

Each module declares its own permissions in code (a registry per module, contributed through the
module's `api` package) and they are seeded into the database on startup. The catalogue is small —
a few hundred entries — and every one has a human-readable label and description, because a
principal reads this list in a UI.

### 2. Role — a per-school bundle of permissions

Two kinds, and the distinction matters:

- **System role templates**, shipped and versioned with the product: Principal, Vice Principal,
  Class Teacher, Subject Teacher, Accountant, Admission Counsellor, Librarian, Transport Manager,
  Hostel Warden, Parent, Student, Auditor.
- **School roles** — what schools actually use. Created by copying a template, then edited freely.

**A school's role is a copy, not a reference.** If school roles pointed at shared templates, adding
a permission to a template in a release would silently widen access at every school — a security
incident delivered by an upgrade. Instead, a release adds new permissions to templates, and each
school's admin sees a "new permissions available" review prompt. Slightly more work, and the only
version that is safe at scale.

### 3. Grant — the scope, and the answer to FR-005

An assignment is `(user, role, scope, valid_from, valid_to)` where scope is one of
`SCHOOL | CAMPUS | DEPARTMENT | CLASS | SECTION | SUBJECT | SELF | WARD`.

A user holds **several** grants:

```
Priya  →  Class Teacher   @ SECTION 9-A
       →  Subject Teacher @ SUBJECT science, SECTION 9-A, 9-B, 10-A
       →  Transport In-charge @ SCHOOL
```

This is what stops role names multiplying. There is no "teacher who is also transport in-charge"
role — there are two grants. Adding a responsibility is a row, not a new role.

The validity window is deliberately in from the start: schools genuinely need "acting principal for
March" and "exam controller during the exam window", and adding time-bounded grants to a live
permission table later is unpleasant.

### 4. Two enforcement layers

- **Can they do this at all?** A permission check on the service or controller method:
  `@PreAuthorize("hasAuthority('fee:invoice:create')")`. Cheap, coarse, declarative.
- **Which rows?** The user's scopes narrow the query itself, through
  `platform.security.AccessScope`, exactly as `TenantContext` narrows by school.

**Scope must be applied inside the query, never as a filter over results.** A post-filter turns
every list endpoint into "load the whole school, discard most of it" — that is the one place this
model can genuinely fail to scale, and it fails quietly, only on the biggest school.

### Deliberate exclusions

- **No deny rules.** Access is the union of grants. Deny-overrides makes "why can't I?"
  unanswerable, interacts badly with multiple grants, and breaks the preview requirement. To remove
  access, remove the permission from that school's role.
- **Parent and student access is derived, not assigned.** A parent's reach comes from the
  guardian-of relationship (`WARD` scope), computed from student data — never from an admin
  assigning "Parent scoped to class 9-A". Letting that be assignable is a data leak waiting for a
  mis-click.
- **Platform support access is its own thing.** A support user is not "a user with every
  permission"; it is a platform-level role outside any school, and every use of it is audited and
  time-boxed. Under the DPDP Act, support staff reading a child's record must leave a trace.

## How this performs

The catalogue is a few hundred permissions; a role holds tens; a user holds a handful of grants. The
effective permission set is computed **once per session** at login, cached against the session, and
invalidated when a role or grant changes, or on forced logout. No per-request permission queries.

The only real cost is the scope-narrowing join on list queries, which is handled with indexes that
lead with `school_id` and then the scope column — the same shape ADR-0002 already requires.

## Consequences

- Every controller method needs an explicit authorization annotation. **A test enumerates controller
  methods and fails the build when one has none** — with 16 modules, that check is what keeps this
  honest; reviews will not.
- The identity module must ship a permission-management UI (roles, grants, and the impact preview
  the acceptance notes require). That is real work and belongs in the first identity milestone, not
  after it.
- Seeding role templates is part of school onboarding, so onboarding is a defined process from day
  one rather than a pile of manual inserts.
- Permission identifiers are effectively public API: renaming one breaks every school's saved roles.
  Renames need a migration that rewrites grants.
