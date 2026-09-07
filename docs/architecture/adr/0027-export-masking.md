# ADR-0027: Export, masked by classification, with an audited unmasked permission of its own

- Status: Accepted and implemented.
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0014](0014-data-classification.md) (classification, and the export masking it asks
  for), [ADR-0022](0022-encryption-at-rest.md) (encryption at rest, which this depends on),
  [ADR-0018](0018-audit-log.md) (the audit log, and `AuditAction.DATA_EXPORTED`),
  [ADR-0020](0020-student-and-guardian-model.md) (the student and guardian model this exports),
  [ADR-0021](0021-bulk-import.md) (the CSV reader this export's writer is the sibling of)

## Context

`docs/status.md` recorded export as deliberately not built, because ADR-0014 asks for two things
neither of which existed: masked export by default, and an audited unmasked export gated on a
permission. Building an export that ignored either would have been the largest unaudited disclosure
surface in the product — a single request that could hand a whole school's caste, religion,
disability and health data to whoever asked, in one file, with no record of it happening.

Both blockers are now closed. [ADR-0022](0022-encryption-at-rest.md) built encryption at rest,
masking and read-auditing for the student module's Restricted fields, and PR #59 verified on a
running deployment that the masked response genuinely does not carry the plaintext. This ADR is the
export itself, and the two decisions the product owner made once masking was real: the masked
export is the default; the unmasked one needs a permission of its own that no shipped role holds by
default; and every unmasked export writes an audit event naming the fields.

**Scope.** Students and guardians only. They are the records a school actually asks to export, they
are the ones carrying every classification tier ADR-0014 defines, and they are the only two Phase 1
records complete enough for an export to be worth building against. Every other module's future
export follows this same shape.

**Format.** CSV, not `.xlsx`. Apache POI was put to the product owner for the bulk import
(ADR-0021) and refused there on size and CVE-surface grounds; the same argument applies here with
nothing new in favour of a dependency, and every spreadsheet application a school owns opens a CSV.

## Decisions

### 1. Masking is derived from `@Classification`, never from a second column list

A hand-written "these are the safe columns to export" list states a fact ADR-0014's
`@Classification` annotation already states once, on the DTO. The two can disagree, silently, and
whichever one the export code happens to read wins — exactly the failure mode ADR-0022 §2 already
rejected for the entity/DTO pairing, restated here for export.

`platform.export.ClassificationCsvExporter` is the mechanism: given a flat record type and a
`Mode`, it reflects over the record's components, reads each one's `Tier` from `Classified.tierOf`,
and in `Mode.MASKED` skips every component tiered `RESTRICTED`. Nothing about which columns are
"safe" is written down a second time. `StudentExportRow` (`student.api`) is the one row type used
for both modes — the same instance, built once per student with every field populated, including
the Restricted ones — and the exporter is the only thing that decides, per call, which of its
components reach the file. `ClassificationCsvExporterTests` proves the property directly: a fixture
record with a newly added `RESTRICTED` component is masked out with no change to the exporter or
its caller, which is the test the feature's own brief asked for by name.

Building the row with real values in both modes, rather than two differently-shaped rows, is safe
for the same reason `StudentRecordService#medicalSummary` already decrypts a row to compute an
`hasBloodGroup` flag without that being "a read" under ADR-0014: the entity's
`@Convert(converter = EncryptedStringConverter.class)` decrypts on load regardless of which DTO the
value ends up in, and what ADR-0014 calls a read is the value **leaving the server**, not sitting
briefly in a Java object between one row's build and its write.

### 2. What "masked" means, per tier — three shapes were on the table and one was chosen

A `RESTRICTED` column in a masked export can be (a) left out of the file entirely, (b) present with
an empty cell, or (c) present with a marker like `[restricted]`. This ADR picks (a).

An empty cell is indistinguishable from "nothing was recorded" — a different fact from "something
was recorded and this file may not say what" — and a school reading a masked export would draw the
wrong conclusion from a blank blood-group column. A marker is more honest and is itself a small
disclosure: it tells anyone who opens the file, or intercepts it, exactly which of a child's fields
are sensitive enough to hide. Omitting the column says neither thing. It also matches what ADR-0014
already means by "masked by default in the UI": `MedicalSummary` does not send an empty
`bloodGroup` field to the browser either, it sends none at all, only a presence flag. The masked CSV
follows the same shape one level further out.

`CONFIDENTIAL` and `INTERNAL` columns are never masked, in either mode. ADR-0014 only asks a
Restricted value to be masked by default; a masked export that also hid every child's _name_ would
not be a safer export, it would be a useless one nobody could act on.

### 3. Every export is audited — masked included, because it still discloses Confidential data

ADR-0014's tier table says, of `CONFIDENTIAL`: "Export is audited," with no qualifier. A masked
student export still contains a child's name and admission number, both Confidential, so it is
audited unconditionally rather than only when unmasked. This reading is also what
`AuditAction.DATA_EXPORTED`'s own Javadoc already said, before this ADR existed to act on it: "ADR-
0014 makes an export the moment **masked** data leaves the building" [emphasis added].

Every call — masked or unmasked, student or guardian — writes one `DATA_EXPORTED` row, in its own
transaction via `AuditService#recordSecurityEvent` (ADR-0018 §4: a security event survives the
surrounding request failing, the same as `RESTRICTED_DATA_REVEALED`). `changed_fields` names the
CSV columns actually written — never a value, the same `FIELD_NAME` regex `recordChange` already
enforces — so a masked export's row never names a Restricted field and an unmasked one always does.
`record_count` (ADR-0018 §2b) carries how many rows the file held.

`AuditService` gained two new `recordSecurityEvent` overloads for this — `Collection<String> fields`
plus an `int recordCount` — because the existing security-event method had no way to carry either.
No existing call site changed shape; the new overloads delegate to the same internal path the
four-argument form already used.

### 4. The unmasked export gets its own permission, not `student:student:reveal_restricted`

`student:student:export_unmasked` (`StudentPermissions`), separate from
`STUDENT_REVEAL_RESTRICTED`. Revealing one Restricted field on one child's screen and downloading
every Restricted field for every student a filter matches are different orders of consequence: the
file outlives the session, it can be re-shared, and a school's own data-protection policy would
reasonably want a distinct, deliberate grant behind it — the same reasoning
`school:school:create` already gets in `RoleTemplates`. Holding one does not imply the other in
either direction.

**No shipped role template holds it.** A school that runs a UDISE+ return grants it to whichever
role actually produces one; the shipped templates stay as conservative as
`school:school:create` and `student:student:reveal_restricted` already are. Guardians have no
Restricted field, so their export has no unmasked variant and no permission of its own beyond the
existing `student:guardian:read`.

### 5. Streaming, not buffering — and not `StreamingResponseBody`

The controller writes directly into `HttpServletResponse#getOutputStream()`, row by row, and
nothing builds the CSV as a `String` or a `byte[]` first. A school of a few thousand students
loading as JPA entities is an ordinary, bounded query — the same order of magnitude
`StudentService#list` already loads for one page's related data, just without the page limit — but
materialising the _file's text_ as a second, larger allocation on top of that is exactly the kind of
spike that kills a 512 MB free-tier instance with no stack trace to show for it (see
`docs/operations/render-free-tier.md`). `ClassificationCsvExporter.write` takes a `Stream<T>` and
writes-then-discards one row at a time for the same reason `CsvReader` reads one record at a time on
the way in.

**`ResponseEntity<StreamingResponseBody>` was the first attempt, and it does not work here.**
Spring dispatches a `StreamingResponseBody`'s write callback on a container async thread, not the
thread that handled the request — and this deployment binds the request's tenant schema
(ADR-0011) to that original thread, in a filter (`SessionTenantFilter`) that runs before the
controller. Writing from the async thread found no schema bound and queried `public`, where none of
the tenant's tables exist — `relation "guardian" does not exist`, caught by
`StudentExportApiTests` before this ever reached a real deployment. Writing synchronously into the
response, on the same thread the whole request already runs on, keeps the tenant binding valid for
the entire write and avoids the class of bug outright, at the cost of holding a request-handling
thread for as long as the file takes to write — an acceptable trade at the school sizes this targets.

### 6. The filename names no child

The route and the filename both end up in a browser's own download history — the same reasoning
`app.routes.ts` already gives for a student's own route using a UUID and the page title reading
"Student record" rather than a name. An export's filename is generic and constant —
`students.csv`, `students-unmasked.csv`, `guardians.csv` — carrying no filter, no date and no
count, because none of that is needed to use the file and a filter term could itself be a searched
name.

## Consequences

**Easier.** A future module's export is the same three pieces: a flat row record with
`@Classification` on every component, a call to `ClassificationCsvExporter.write`, and one
`recordSecurityEvent(DATA_EXPORTED, …)` call per export. Nothing about masking needs deciding again.

**Harder.** `StudentExportRow` is the one DTO in the codebase that legitimately holds a Restricted
value in a plain Java field rather than behind a `*Detail` endpoint gated on
`reveal_restricted` — a reviewer has to know why that is safe (see decision 1) rather than assuming
every Restricted value in a Java object is already a disclosure.

**To revisit.** The row is flattened by hand for readability — guardians as one semicolon-joined
column, for instance — which is a second, smaller place a column's shape is decided outside the
`@Classification` mechanism. That is a readability choice about non-sensitive structure, not a
masking decision, and does not carry the risk decision 1 addresses. A future module with a similar
export should still ask whether its own flattening hides something rather than merely reordering it.
