# ADR-0020: The student and guardian model

- Status: Accepted
- Date: 2026-09-06
- Deciders: Raja
- Related: [ADR-0014](0014-data-classification.md) (classification), [ADR-0017](0017-identity-model.md) (a guardian is a person record), [ADR-0019](0019-classes-and-sections.md) (classes are structural), [ADR-0011](0011-schema-per-tenant.md) (tenancy)

## Context

The student record is the thing the rest of the product hangs off: fees are charged to it, attendance
is taken against it, marks are recorded for it, and a transfer certificate is generated from it. It
is also the largest concentration of children's personal data in the system.

Two decisions in this area are already made and are not revisited here. A guardian is a **person
record, not an account** ([ADR-0017](0017-identity-model.md)). Classes and sections are
**structural**, so the academic session lives on the enrolment ([ADR-0019](0019-classes-and-sections.md)).

## Decisions

### 1. A student has one name field, not three

`full_name`, as one column, holding the name exactly as it appears on the records the school will be
held to.

The instinct is `first_name` / `middle_name` / `last_name`, and it is wrong here. A great many
Indian students have no surname at all; many have a single name; in much of South India the
convention is an initial that expands to a village or a father's name and is not a family name in
any sense the three-field shape means. A form with a required "Last name" box forces the office
clerk to invent one, and what they invent goes on the certificate.

It also matches what the boards want. CBSE records a single "Candidate Name" and takes the father's
and mother's names as separate fields of their own — which here come from the guardian records, not
from splitting the student's name.

The cost is sorting: "sort by surname" is not available, and a class list sorts by the whole name.
Schools that want a different order can have `sort_name` later, as an additive column that defaults
to `full_name`. That is a smaller mistake to correct than a schema that has been forcing clerks to
guess for two years.

### 2. Restricted fields, amended 2026-09-07 now that encryption at rest exists

This section originally said these columns were not modelled because no field-level encryption, UI
masking or read-auditing path existed, and that adding them first would mean storing a child's caste
in plaintext in a table nobody had decided how to protect. [ADR-0022](0022-encryption-at-rest.md)
built that machinery, and `student_medical` and `student_compliance` (their own tables, additive to
`student`) now carry the fields this section used to defer:

- `student_compliance.caste_category`, `.religion`, `.special_category` (EWS/BPL/RTE) and `.apaar_id`
  — caste and community, religion, category and APAAR, encrypted at rest, masked by default in the
  UI, and read-audited on every reveal (`StudentAudit#RESTRICTED_DATA_REVEALED`). `apaar_id` cannot
  be saved without a recorded consent (`apaar_consent_given`, who, and when) — APAAR is
  consent-based, and a field only lawful with consent needs somewhere to record that it was given.
- `student_medical.blood_group`, `.cwsn_status`, `.disability_details`, `.allergies`,
  `.chronic_conditions` and `.medication` — disability/CWSN and health data, the same three
  guarantees. Blood group is included even though schools print it without a second thought: it is
  still a health fact about a child, and ADR-0014 says to pick the more protective tier when it is
  arguable.

Two fields this section originally named are still deliberately absent, and for a narrower reason
than "the machinery does not exist": **guardian income** belongs on `guardian`, not `student`, and
nothing in this slice's requirements calls for it; an **Aadhaar reference** is not asked for by
FR-029, which names PEN/UDISE, APAAR and the board registration number and nothing else. Adding
either now would be scope creep this change was otherwise careful to avoid — they remain additive,
the same as everything else this ADR left out.

`student_medical`'s emergency contact and `student_compliance`'s PEN/UDISE id and board registration
number are Confidential, not Restricted — a name and a phone number in one case, identifiers like the
admission number in the other — and are not masked.

### 3. Admission number is unique within the school, and that is a decision not a limitation

The requirements say admission-number uniqueness "should be configurable per school or school
group". A school group spans campuses, and under [ADR-0011](0011-schema-per-tenant.md) a campus is a
schema — so group-wide uniqueness cannot be a database constraint at all. It would be an
application-level check across schemas, racing with every other campus's admissions desk.

Unique within the school, enforced by the database. Group-wide uniqueness, if a trust ever asks for
it, is a prefix convention (`NORTH/2026/0148`) rather than a distributed lock, and that costs
nothing to adopt later.

### 4. Enrolment is its own record, and it is what carries the session

`student_enrolment` links a student to a session, a class and a section, and carries the roll
number. A student has **at most one active enrolment per session**, enforced by a partial unique
index rather than by hope.

This is the shape [ADR-0019](0019-classes-and-sections.md) implies: classes and sections are
structural, so the year has to live somewhere, and it lives here. It also makes promotion a new row
rather than an edit — next year's enrolment does not overwrite this year's, so a student's history
is readable without an audit log.

Roll number is **nullable**, because it is assigned after admission and often after the class list
settles, and unique per `(session, section, roll_number)` — which is the requirement's
"per class-section-session", since a section belongs to exactly one class.

### 5. Guardians are shared, and the link carries the relationship

A `guardian` row is a person. `student_guardian` links them, carrying the relation (father, mother,
guardian, local guardian) and which contact is primary. Siblings therefore share one guardian record
rather than having a copy each, which is the whole reason the requirement says "a guardian can be
linked to multiple students": updating a father's phone number once updates it for all four of his
children.

The trap this avoids is worth naming: with a guardian copied per student, a school that corrects one
child's record leaves the other three with a number that no longer answers, and nothing in the
system knows they disagree.

### 6. Nothing is deleted

A student is `WITHDRAWN` or `TRANSFERRED`, never removed; a guardian link is ended, not erased. Fees,
attendance and marks all reference a student, and a school that deletes one leaves those pointing at
nothing. This is the same rule as [ADR-0019](0019-classes-and-sections.md) and for a stronger
reason: the records here are the ones a school is legally required to be able to produce years later.

Erasure under the DPDP Act is a **different operation** from deleting a row, and it will need its own
design — what is erased, what is retained under a statutory obligation, and what the audit log keeps.
Not in this slice, and not something a `DELETE` endpoint would have been an answer to anyway.

## Consequences

- The `student` module owns `student`, `guardian`, `student_guardian`, `student_enrolment`,
  `student_contact`, `student_transfer`, `student_medical` and `student_compliance` (the last four
  added 2026-09-07, additively). It depends on `academics` for the session, class and section it
  points at — the first real cross-module dependency between two feature modules, and it goes
  through the named interface.
- Every field on `student`, `guardian`, `student_contact` and `student_transfer` is **Confidential**
  or lower under ADR-0014: names, dates of birth, addresses and phone numbers. None may be logged,
  and none may appear in an error message. The audit log records field names only, which it already
  does. `student_medical` and `student_compliance` mix Confidential fields with **Restricted** ones —
  see §2, amended 2026-09-07 now that [ADR-0022](0022-encryption-at-rest.md) exists.
- Documents, photographs, sibling links, houses, clubs and promotion records are all deliberately
  out. Each is additive. Medical profiles and transfer certificates, named here as future work when
  this ADR was first written, are no longer out — see §2 for medical (FR-034) and `student_transfer`
  for previous school and transfer certificate details (FR-033).
