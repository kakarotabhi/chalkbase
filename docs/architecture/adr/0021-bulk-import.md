# ADR-0021: Bulk student import

- Status: Accepted
- Date: 2026-09-06
- Deciders: Raja
- Related: [ADR-0014](0014-data-classification.md) (classification), [ADR-0020](0020-student-and-guardian-model.md) (students and guardians), [ADR-0018](0018-audit-log.md) (audit)

## Context

A school onboarding onto Chalkbase has between two hundred and two thousand students already, in a
spreadsheet. Without an import, onboarding is a week of typing and the product does not get adopted.
The roadmap names "student import completed and verified" as a Phase 1 exit criterion, and names
poor import tooling as a risk whose mitigation is "dry-run imports and validation".

The file itself is the most sensitive object the product will ever handle: several hundred children's
names, dates of birth and admission numbers in one place, uploaded over HTTP.

## Decisions

### 1. Validate first, as a separate call, always

`POST /api/students/import/validate` parses the file and answers with every problem it found —
row number, column, and what is wrong — and writes nothing. `POST /api/students/import` does the
same parse and then commits.

Two endpoints rather than one with a flag. A flag defaults to something, and the wrong default here
writes six hundred rows nobody has looked at. Two endpoints cannot be got wrong by omission.

### 2. Commit is all-or-nothing

If any row fails, nothing is imported.

The alternative — import the good rows, report the bad — sounds friendlier and is worse. A school
that uploads six hundred rows and is told "573 imported, 27 failed" now has a database in a state
nobody planned, and the obvious next act is to fix the 27 and upload again, which re-imports the 573
or fails on every one of them as duplicates. Neither outcome is what anybody wanted.

All-or-nothing means the file is either the school's data or it is not, and fixing a typo and
re-uploading is always safe.

### 3. Classes and sections are named, not identified

A spreadsheet holds `Class 5` and `A`, not a UUID. The import resolves names to the school's own
classes and sections and reports an unmatched name as a row error naming what it could not find.

This is a deliberate acceptance of ambiguity at the edge: the school must have set up its ladder
first, and the error message has to be good enough to explain why "Class V" did not match "Class 5".

### 4. Guardians are imported, matched by phone — amended 2026-09-07

This section originally said guardians are not imported: a file of six hundred students each
carrying a father's name and phone would create six hundred guardian records — including four for
one man with four children here, which is precisely the duplicate that
[ADR-0020](0020-student-and-guardian-model.md) §5 exists to prevent and that the manual flow was
just fixed to avoid. That gap is now closed, and this is the design that closes it.

**One guardian per row.** Five optional columns — `guardian_name`, `guardian_phone`,
`guardian_relation`, `guardian_email`, `guardian_primary` — either all blank (no guardian on this
row yet) or naming one person. `guardian_name` and `guardian_phone` are required together: matching
happens on the phone, so a name with no phone cannot be matched against anything and a phone with
no name cannot be told apart from anyone else's. A student needing a second guardian on record
(a mother, once the father is already in from the file) gets one the way any student does today —
`GuardiansApi` search-and-link, from that child's own record, after the import.

**Matching is on `guardian.phone_digits`, not `guardian.phone`**, reusing the exact digit-stripping
rule the guardian directory search already applies (`PhoneDigits.digitsOf`, née the private helper
`GuardianService` used to keep to itself) — see the bug that column exists to fix, described where
it is defined. For an automated, unsupervised match, equality is truncated to the last ten digits
of each number rather than left as the directory search's unanchored substring test: two numbers
that agree on their last ten digits are the same Indian mobile number with or without a `+91`, and
the substring relationship the human-facing search uses is the wrong one to reuse verbatim here — a
person confirms a search result before it does anything; an import has nobody to confirm anything,
so a match needs to be an equality, not a "contains".

**Within one file, not only against the directory.** Every row's guardian phone is grouped, in file
order, against every other row's in the same upload — before the directory is asked anything. Four
rows naming the same father with the same number, and agreeing on the name (case and spacing
forgiven, the same leniency the class-and-section names in §3 get), produce one guardian and four
links, whether or not that guardian already existed. The first row to use a number establishes what
its guardian is called; a later row on that same number joins silently if the name agrees.

**A phone match under a different name is refused, not guessed at — in either direction: within
the file, or against the directory.** [ADR-0020](0020-student-and-guardian-model.md) §5 records
that there is deliberately no server-side uniqueness on a guardian's phone, because two people
genuinely share one, and that the manual create form's answer is to warn and let a human decide
whether the match is real. An import has no human mid-file to ask that question, and both ways of
guessing on its behalf are worse than making the school look:

- Matching anyway risks attaching a child to a stranger's guardian on the strength of a shared
  landline or a data-entry slip — the wrong kind of wrong, because it is silent and it is about a
  child.
- Creating a second guardian on a shared number recreates the exact duplicate this slice exists to
  prevent, every time a family's number legitimately repeats.

So every row that shares a phone number with a different name — whether the other name is earlier
in this same file or already in the directory — is refused as a row error naming what happened,
never the name itself (ADR-0014). The whole file is then not imported, per §2: the office corrects
the spelling if it was one, or resolves the two people through the ordinary guardian directory
(search, and either link the existing one or create a second), and re-uploads. This is the same
shape §3 already uses for an unmatched class name — ambiguity at the edge is a row error with a
clear reason, not a guess.

**The report says what happened, not what a clean file would go on to do.** `guardiansCreated`,
`guardiansMatched`, `guardianLinksCreated` and `studentsLinkedToExistingGuardians` all follow
`imported`'s own rule: zero from `validate`, and zero from a commit that found anything wrong,
because in both cases nothing was written. "Created 12 guardians, linked 47 students to existing
guardians" is the sentence this exists to make possible.

**`guardian.phone` is `varchar(20)`.** A CSV value that does not fit is a row error on
`guardian_phone` naming the problem, exactly like an overlong `full_name` or `admission_number` —
never a 500 from a database refusing the insert.

**One new audit verb, `GUARDIANS_IMPORTED`**, alongside `STUDENTS_IMPORTED` rather than folded into
it: writing a `guardian` row and writing a `student` row are different facts, and a reader asking
"how many new guardians did this import create" should not have to know the answer is hiding inside
a student-shaped event. Written only when at least one guardian was actually created — a file whose
guardians all matched the directory gets no such row, because an audit row for zero would be worse
than none at all.

### 5. CSV, and a note about Excel

The requirement says "import student records from Excel". This imports **CSV**, which every version
of Excel and every Indian school's Tally-adjacent workflow can produce with *Save As*.

Reading `.xlsx` directly needs Apache POI — several megabytes of dependency, a real CVE surface, and
`AGENTS.md` rule 8 says ask before adding one. **This is an open question for the product owner**, not
a settled decision: if "Save as CSV" turns out to be a genuine barrier for school offices, POI is the
answer and it is a small change behind the same endpoint.

### 6. The file is never stored, and never logged

Parsed in a stream, held only as long as the request, and discarded. It is not written to disk, not
put in object storage, and not attached to the audit event.

**"Not written to disk" needs one line of configuration to be true, and it was missed the first
time.** Spring's `spring.servlet.multipart.file-size-threshold` defaults to `0B`, which means every
multipart upload is spooled to a temp file. For most uploads that is unremarkable; for this one it
puts several hundred children's names, dates of birth and admission numbers unencrypted into the
container's temp directory — where they stay if the process dies mid-request. The threshold is set
above `max-file-size` so the file is always held in memory instead, and a test asserts the
*relationship* between the two rather than the number, because raising the size limit without
raising the threshold would silently undo it.

It is worth recording how this was found: it was not. The ADR said the file is not written to disk,
the code did nothing to make that so, and the gap survived a build, a full test suite, a live run
against the real database and a green CI. It turned up only because the merge was paused and the
slice read again. A parse error names the row and the
column and never quotes the cell, because the cell is a child's name
([ADR-0014](0014-data-classification.md)).

The row cap is 2,000 — above any single Indian school's intake and low enough that a malicious or
mistaken upload cannot exhaust memory.

### 7. One audit row per import, not one per student

`STUDENTS_IMPORTED`, with the count and the session, against the import as an event. Six hundred
`ENTITY_CREATED` rows would bury every other thing that happened that day in the one log a principal
reads to find out what happened that day.

The individual students are still recoverable — they exist, with their `created_at` — and the import
row says when and by whom.

## Consequences

- The import needs `student:student:manage`. Validation needs it too: telling a caller which
  admission numbers already exist is a read of the student register by another name.
- A school must set up its academic session and class ladder before importing. That is the right
  order anyway, and the error messages say so.
- **Export is not in this slice.** [ADR-0014](0014-data-classification.md) requires exports to be
  masked by classification and the unmasked export to be an audited action, and neither the masking
  nor the permission that lifts it exists. An export that ignored that would be the largest
  unaudited disclosure surface in the product.
