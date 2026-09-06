# ADR-0014: Four data classification tiers, enforced in code

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md), [ADR-0011](0011-schema-per-tenant.md),
  [ADR-0007](0007-api-response-envelope.md)

## Context

`AGENTS.md` rule 9 says personal data of children is the default assumption: no student, parent or
staff data in logs, error messages, committed test fixtures, or third-party calls.

That rule is correct and it is also unenforceable as written, because it asks every future change —
including every change made by an agent — to remember it. Rules that depend on memory fail silently
and are discovered by someone else, in production, in a log file.

The data itself is not uniform, either. A school ERP holds a student's timetable and a student's
disability status in the same database. Treating them identically means either logging a
disability status or being unable to log a timetable lookup. So the rule needs tiers, and the tiers
need to do something mechanical.

The legal frame is the DPDP Act 2023. Everything here is children's data: processing requires
verifiable parental consent, and behavioural tracking and targeted advertising directed at children
are barred outright. Consent and retention are therefore data model concerns, not a policy page.

## Options considered

1. **Two tiers — personal and non-personal.** Matches DPDP's own vocabulary and is the least work.
   But nearly everything in a school ERP is personal data, so the classification partitions almost
   nothing and gives no guidance about what may appear in a log. Rejected.

2. **Three tiers — restricted, internal, public.** Simple enough to hold in your head. But student
   names and marks land in the same tier as health records and caste category, forcing a choice
   between over-protecting routine data (making the product unusable) and under-protecting the
   sensitive kind (making it dangerous). Rejected.

3. **Four tiers, with the classification declared on the data and enforced at the boundary.**

## Decision

**Option 3, and the enforcement half is the decision — the tiers alone are just a table.**

### The tiers

| Tier | Contents | Handling |
|---|---|---|
| **Restricted** | Biometrics, health and counselling records, caste/community, religion, disability/CWSN, guardian income, EWS/BPL/RTE category, APAAR and Aadhaar references | Encrypted at rest. Never logged, at any level. Never in an error message. Every read is audited. Masked by default in the UI, revealed by an explicit permission and a recorded action. |
| **Confidential** | Student and guardian names, date of birth, address, phone, photographs, marks, fee ledger, attendance, and **credentials** — passwords, password hashes, session ids and tokens, which additionally are never stored in a recoverable form and never returned by any endpoint | Never logged. Permission-gated per [ADR-0005](0005-authorization-model.md). Export is audited. |
| **Internal** | Class and section structure, timetables, subjects, fee heads, staff roles, academic calendar | Permission-gated. May be logged freely. |
| **Public** | School profile, mandatory public disclosure pages, the certificate verification response | No authentication required. Deliberately published. |

### Enforcement

A `@Classification` annotation on DTO record components is the single source of truth, and it drives:

- **Log redaction.** A serialiser that refuses to render `RESTRICTED` and `CONFIDENTIAL` fields into
  any log sink, replacing them with the field name and its tier.
- **Error responses.** The [ADR-0007](0007-api-response-envelope.md) error envelope carries codes and
  field names, never rejected values — a validation failure on a phone number must not echo it.
- **Export masking.** CSV and Excel exports mask by tier unless the caller holds the permission that
  unmasks, and the unmasked export is an audited action.
- **A test that fails the build** when a DTO field is unclassified. An unannotated field is not
  assumed safe; it is a build error. This is what makes the policy hold across changes nobody
  reviews closely.

### What is actually built, as of 2026-09-06

One of the four bullets above, and it is worth being precise about which — the others are still
aspirations and should not be relied on.

**Built:** `@Classification` on every record under a `*/api/` package, a `toString()` on each that
renders through `platform.classification.Classified`, and three build-failing tests: every component
is annotated, every record delegates its `toString`, and a populated instance's `toString` contains
no `CONFIDENTIAL` or `RESTRICTED` value. It fails closed — an unannotated component renders
`<UNCLASSIFIED>`, never the value.

**Not built, and the gap is narrower than it sounds.** This stops `log.info("saving {}", dto)`. It
does nothing about `log.info("saving {}", dto.fullName())`, which is exactly as unsafe as it was
before. Closing that needs either the log-redacting serialiser above, or — cheaper and probably
first — a static rule that flags a call to a `CONFIDENTIAL` accessor appearing inside a logger
argument. Export masking has no exports to mask yet.

**Also unbuilt: encryption at rest**, which is why no `RESTRICTED` field exists anywhere
([ADR-0020](0020-student-and-guardian-model.md) §2). A test asserts that no `RESTRICTED` component
exists, so introducing one before the encryption, the read-auditing and the UI masking exist will
fail the build rather than quietly ship.

Classification is declared **once, at the DTO**, because the DTO is already the mandatory boundary
under `AGENTS.md` rule 4 — every crossing of a module or HTTP boundary passes through one.

### Consent and retention

- Parental consent is recorded per purpose, with who consented, when, and through what — not a single
  boolean. APAAR specifically requires its own consent record, per the compliance requirements.
- Consent is withdrawable, and withdrawal is honoured for optional processing.
- Every `RESTRICTED` and `CONFIDENTIAL` category carries a **retention period**, and retention runs
  from an event (student exit, alumnus for N years) rather than a fixed date.
- Erasure is a first-class operation. [Schema-per-tenant](0011-schema-per-tenant.md) makes
  school-level erasure tractable — a school's data is a schema and a MinIO bucket, not rows scattered
  across shared tables.
- No third-party analytics, advertising or behavioural tracking in any parent- or student-facing
  surface. Not a preference; DPDP bars it for children.

### Sample data

Committed fixtures are synthetic only. This is now an enforced consequence of the tiers rather than a
convention: a fixture containing `RESTRICTED` or `CONFIDENTIAL` real data has no lawful basis and no
consent record behind it.

## Consequences

**Easier.** `AGENTS.md` rule 9 becomes a build failure instead of a code review discussion, which is
the only form of it that survives contact with a large codebase and with agents. DPDP readiness is a
property of the model rather than a project. Data subject requests are answerable, because the tiers
say where the sensitive data is.

**Harder.** Every DTO field needs a classification, including the boring ones, and that is friction
on every new endpoint — accepted deliberately, because the friction is the enforcement. Debugging is
harder when logs cannot contain the values involved; trace ids and the constraint registry from
[ADR-0007](0007-api-response-envelope.md) have to carry that weight instead. Encryption at rest for
`RESTRICTED` columns costs query flexibility — those columns cannot be indexed or searched normally,
which constrains reporting on category and disability data that compliance exports will eventually
want.

**To revisit.** The UDISE and CBSE disclosure exports in Phase 4 need aggregate counts by category
and disability. Aggregates over encrypted columns need a deliberate approach — most likely a
maintained aggregate table rather than querying the encrypted values — and that should be designed
when the export is built, not improvised.
