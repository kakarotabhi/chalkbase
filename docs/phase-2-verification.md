# Every screen, every action, driven in a browser

Run on 2026-09-09 against `https://chalkbase-web.onrender.com`, school `DEMO-001`, as `principal`
and then as `auditor`. Unlike [phase-1-verification.md](phase-1-verification.md), which exercised the
API with `curl` and sampled the screens, this pass drove **the controls themselves** — pressing the
buttons a clerk presses, in the order a clerk presses them, and reading what came back.

**Why the distinction earned its keep.** The Phase 1 pass reported the phase verified on the strength
of `/api/me`. Driving the app afterwards found a navigation tree that never rendered its children.
This pass found the same class of thing three more times: a screen that shows a different answer from
the API it called, a button that closes the form it opens, and forms that refuse to save and say
nothing. None of these are visible from the API, and none had a test.

## Result

Twelve findings stand. Two mean a feature does not work at all. Two more were mine and are withdrawn
below, because a verification report that only lists hits is not a verification report.

|       | Finding                                                                                                                | Where          | Severity                 |
| ----- | ---------------------------------------------------------------------------------------------------------------------- | -------------- | ------------------------ |
| **K** | "Add a document" closes the form it opens — upload, edit, download and delete are unreachable                          | student record | **feature dead**         |
| **J** | The record header shows the newest enrolment, not the current one, and disagrees with the list, the export and the API | student record | **wrong data on screen** |
| **O** | One unconfirmed click stops a class and silently takes its enrolled children off the register                          | classes        | **high**                 |
| **I** | 73 of 132 form fields never bind `[error]`; a refused save says nothing                                                | 12 screens     | high                     |
| **C** | The correction workflow cannot be reached — the seed writes no attendance history                                      | attendance     | blocks verification      |
| **L** | Spring Boot's auto-configured in-memory user is still created in production                                            | backend config | hygiene                  |
| **A** | The fee structure Save is disabled with no stated reason (a due date is required)                                      | fees           | medium                   |
| **B** | The leave screens print raw ISO dates and a nanosecond timestamp                                                       | attendance     | medium                   |
| **E** | No list screen puts its filters in the URL, so a filtered view cannot be linked or recovered                           | 7 screens      | medium                   |
| **M** | Overlapping academic sessions are accepted with no check and no error code                                             | academics      | product call             |
| **G** | The dev API restarts silently every few idle minutes and takes six to come back                                        | Render         | operational              |
| **D** | The locked-date banner points at a control that is not on the page                                                     | attendance     | copy                     |
| ~~F~~ | ~~Restricted fields are never masked~~ — **withdrawn: the masking is correct**                                         | —              | —                        |
| ~~H~~ | ~~The API publishes its whole surface unauthenticated~~ — **withdrawn: a documented decision**                         | —              | —                        |

Fixed and awaiting merge: **A, B, D** in #91, **C** in #90, **J** in #92, **K** in #93, **L** in #94.

## What the product does well, stated plainly

Worth naming, because it is the standard the findings fall short of — and because in every case the
product already solves the problem somewhere else.

**The import screen never leaves you guessing.** Every disabled control says why: "Choose a file above
first", "Check the file first. Import stays unavailable until a check on the file you have chosen
comes back with nothing wrong." Given five deliberately broken rows it caught all five, grouped them
by row _and_ by column, and when a class name did not match it listed the class names the school
actually has. Finding A and Finding I are, in effect, "the other screens are not this screen".

**The guardian removal dialog names every consequence before you commit** — that the guardian record
survives, that their other children keep them, and, computed rather than boilerplate, "They are the
main contact for this student, so this student will be left without one." Finding O is the same
product asking nothing at all before a more consequential act.

**The audit log holds the line ADR-0018 draws.** Field names, never values — in the list, and in the
detail panel where a lazy implementation would dump the payload. An export is recorded with all 27
column names, so an inspector can see exactly which columns left the building and still learn nothing
about a child.

**The retired-name trap is handled, twice.** Reusing a retired subject's code is refused with "…kept
its code when it was retired. **Reinstate it rather than adding a second one.**" The fee heads screen
does the same. Both name the offending record instead of returning a bare conflict.

## Findings in detail

### K — you cannot add a document to a student

Pressing **Add a document** flashes "Loading documents…" and returns to "No documents yet". Every
time, with no console error. The cause is an effect subscribed to the view it destroys, in
`student-documents.ts`:

```ts
private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');   // ~129

constructor() {
  effect(() => {
    const studentId = this.studentId();
    this.closeAdd();          // reads fileInput()
    this.editingId.set(null);
    this.removing.set(null);
    this.load(studentId);
  });
}
```

`#fileInput` lives inside `@if (adding())`. `startAdd()` sets `adding = true`, the form renders,
`fileInput()` goes from undefined to an element, that re-runs the effect, and the effect's
`closeAdd()` sets `adding = false` again. `editingId.set(null)` is in the same effect, so **Edit is
dead for the same reason**. ADR-0025's storage adapter is consequently unverified from the UI.

**How it shipped:** none of the seven cards on the student record had a spec — medical, compliance,
contact, previous-school, enrolments, guardians and documents were all untested. A spec that pressed
the button and asserted the form appeared would have failed in CI.

### J — the record header shows the wrong class

Add an enrolment for a later academic year and the record header reads `LKG · B / VERIFY-2099-01`,
while the student list, the CSV export and `GET /api/students/{id}` all say `Nursery · A / 2026-27`.
The list is right. The API computes it properly — `StudentService` filters the history on the current
session id and returns `currentEnrolment` — and the screen ignores that field
(`student-detail.ts:132`):

```ts
const current = (student.enrolments ?? []).find((enrolment) => enrolment.active) ?? null;
```

The history is newest-first, so this is "the newest active enrolment", not "the current one".

**Why it matters:** schools do next year's promotions in February, before the session turns over. From
that moment until the year rolls, every student record page names a class the child is not in yet and
contradicts the list it was opened from.

### O — one unconfirmed click takes six children off the register

"Stop running Nursery" fires immediately: no dialog, no count, no mention that anyone is enrolled. Six
students are. The moment it is off, both its sections vanish from **Mark attendance** — 23 sections
become 21 — so those six children cannot be marked present or absent by anyone, and nothing on screen
said that would happen.

They are not lost: they stay on the student list, still reading `Nursery · A`, and one click restores
everything. The damage is that a school would not know to make that click. The register simply has
fewer classes in it the next morning.

Smaller, same screen: a stopped class still reports `2 of 2 sections running`, so the row contradicts
itself.

### I — a form refuses to save and says nothing

With `not-an-email` in the Contact card, Save does nothing: the form is `ng-submitted`, the control is
`ng-invalid`, and no message appears, no `aria-invalid` is set, nothing is announced. The shared
`cb-form-field` already renders the error with `role="alert"` and wires `aria-describedby` — callers
simply do not pass `[error]`:

```
132 cb-form-field usages across the features; 59 bind [error]; 73 do not.
```

Twelve screens bind it nowhere: student-medical (9 fields), student-documents (8), fee-heads (6),
student-previous-school (5), leave-request-form (5), circular-list (4), attendance-mark (4),
enquiry-detail (4), student-contact (3), student-import (2), fee-structure (2), circular-detail (1).

Finding A is the same defect wearing a different hat.

### C — the correction workflow cannot be reached

A correction can only be filed against a mark on a locked date (end of day plus 24 hours). The demo
seed creates no attendance at all, so every past date reads "Not marked" and `Request a correction` is
correctly withheld — it renders only when a row has a `markId`. The approve/reject half of the module,
and anything that reads attendance history, is unverified. #90 seeds it.

### M — overlapping academic sessions are accepted

`VERIFY-Overlap` (1 Jun 2026 – 31 Jan 2027) sits entirely inside `2026-27` (1 Apr 2026 – 31 Mar 2027)
and saved without complaint. There is no overlap check in the academics module and no error code for
one: `AcademicsErrorCode` has `DUPLICATE_SESSION_NAME`, `SESSION_ALREADY_CURRENT` and
`INVALID_SESSION_DATES`, and nothing else about time.

Nothing breaks today, because only the _current_ session is read and a partial unique index already
guarantees exactly one of those. It matters the moment something must answer "which year does this
date belong to" — attendance across a year boundary, a fee due date, a report card, a transfer
certificate. **Whether to enforce it is a product call, not a bug fix**: a school might legitimately
want a short summer session inside a long one. Worth deciding deliberately rather than discovering.

### G — the dev API restarts on its own

Twice in twenty-five minutes — 05:55:54 and 06:20:27 — the process restarted with no shutdown log and
no exception, three to four minutes after the last request. Each recovery takes about six minutes
(`Started ChalkbaseApplication in 351.699 seconds`) on Render's `free` plan at 512 MB, with Spring
Modulith loading ArchUnit at startup. Any verification run can lose six minutes without warning, and a
demo can die mid-sentence.

Two things went right when it happened: the bounce to `/login` preserved `returnTo`, and the message
said "Chalkbase could not be reached. Check your connection and try again." rather than blaming the
password — which is the easy mistake, and the one that sends a user to reset a password that was fine.

### L — Spring Boot's default user is still created in production

Every startup under `prod` logs:

```
WARN .s.a.UserDetailsServiceAutoConfiguration :
Using generated security password: <a fresh uuid each boot>
This generated password is for development use only. Your security configuration must be
updated before running your application in production.
INFO r$InitializeUserDetailsManagerConfigurer :
Global AuthenticationManager configured with UserDetailsService bean with name
inMemoryUserDetailsManager
```

The password is random per boot and only reaches the log, so it is not a standing credential. What it
says is that `UserDetailsServiceAutoConfiguration` was never switched off, so the global
`AuthenticationManager` is Spring's in-memory default rather than anything this application owns.

Traced before being called a hole, and it is not one: `SecurityConfig` defines the only
`SecurityFilterChain` and never calls `.httpBasic()` or `.formLogin()`; `AuthenticationService`
verifies the password against `user_credential` itself and writes the token straight to the session,
never through an `AuthenticationManager`. The in-memory user is unreachable — dead configuration, on
every profile. #94 switches it off so the warning stops and nobody has to re-derive which of two
mechanisms is in charge.

### E — list filters are not in the URL

`location.search` stays empty however a list is filtered or paged, and no feature screen reads
`queryParamMap`. A filtered view cannot be linked, bookmarked, or recovered with the back button. On a
65-row demo this is a nuisance; on a real school's two thousand it is the difference between usable
and not.

## Two findings I withdrew

**F — "restricted fields are never masked".** Wrong. On a cold load the Medical card shows
`Recorded, masked` for every restricted field and offers a **Reveal** button that returns the real
values. What I saw was the component holding what the user had just typed, immediately after saving,
which is not a disclosure. The reveal control is correctly withheld when nothing restricted is
recorded — which is why it was absent on a student with an empty health record.

**H — "the API publishes its whole surface unauthenticated".** The observation was right:
`/v3/api-docs` returns 200 and Swagger UI loads with no session under the `prod` profile. The
conclusion was wrong. `application-prod.yml` says so on purpose, in a comment above the setting —
that this deployment is a personal dev environment with no real school data, that a browsable API
explorer is the point of putting it online, and that `springdoc.api-docs.enabled: false` goes in the
day it serves a real school. `SecurityConfig` permits those paths on every profile, so the two
mechanisms agree rather than one propping the other open.

I filed it without reading the config. Actuator being closed while springdoc is open is not an
inconsistency — it is two decisions, both deliberate. Carry forward only as a reminder: closing
springdoc belongs in the same change that onboards the first real school.

## Open questions for the owner

Neither of these is a defect. Both are decisions nobody has made yet, and both will be more expensive
to decide later.

**Nothing can be deleted, and only some of that is on purpose.** Classes, sections and subjects
document the rule in their own ledes — "Nothing here is ever deleted… it is switched off, stays in
place, and can be brought back" — which is the right answer for records with history hanging off
them, and it is stated where a user will read it. **Academic sessions and roles have no delete and no
stated reason.** A school that mistypes a session name during setup is stuck with it forever, and this
demo school now carries `VERIFY-Overlap`, `VERIFY-2099-01` and a role whose own description reads
"Synthetic role for verification, to be deleted". The role question was already open before this pass;
sessions are the same question wearing a different hat, and the answer should probably be the same one
for both.

**Should overlapping academic sessions be refused?** See [Finding M](#m--overlapping-academic-sessions-are-accepted). Nothing breaks today. It becomes
unanswerable the first time something has to map a date to a year.

## Every action, screen by screen

Each row is an action driven through the browser, not an API call.

### Fees

| Action                       | Result                                                                                                                                                                                                                                                                |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Add a fee head               | **pass** — "VERIFY Tuition Fee added."                                                                                                                                                                                                                                |
| Add a duplicate fee head     | **pass** — refused `409 FEE_001`, shown as a sentence: "A fee head with that name already exists. A head kept its name even while retired — check the list below, including any switched off." It anticipates the retired-name trap that caught classes and subjects. |
| Set up a class's structure   | **pass** — fee head, amount, frequency, due date                                                                                                                                                                                                                      |
| Save the structure           | **pass** — "Nursery's structure saved as version 1", row reads `Set up · v1  ₹12,000.00`                                                                                                                                                                              |
| Versioning is visible        | **pass** — `v1` on the row; money as `₹12,000.00`, two decimals                                                                                                                                                                                                       |
| Copy from a previous session | present, and states it never overwrites a class that already has one                                                                                                                                                                                                  |

Not a defect in behaviour — the requirement is reasonable — but the screen fails the same test the
rest of this product passes: it does not say what it wants.

### Enquiries — capture

| Action                         | Result                                                               |
| ------------------------------ | -------------------------------------------------------------------- |
| Create an enquiry              | **pass** — child, parent, phone, Walk-in source, assigned counsellor |
| Appears in the follow-up queue | **pass** — "Due today · 9 Sept 2026"                                 |

### Circulars

| Action                                            | Result                                                     |
| ------------------------------------------------- | ---------------------------------------------------------- |
| Compose, acknowledgement required, target a class | **pass**                                                   |
| Save as draft                                     | **pass**                                                   |
| Publish                                           | **pass** — "was published to 6 student(s)"                 |
| Recipient count on the row                        | **pass** — `1 target(s) · 6 recipient(s) · 0 acknowledged` |

### Leave requests

| Action                                        | Result                                                                                 |
| --------------------------------------------- | -------------------------------------------------------------------------------------- |
| New request: section → student list populates | **pass** — student dropdown filled from the chosen section                             |
| Send request                                  | **pass** — lands on the request, status `Pending`                                      |
| Approve with a note                           | **pass** — "Leave request approved.", status flips to `Approved`                       |
| Appears in the list                           | **pass** — one row, `Approved`                                                         |
| Status filter                                 | **pass** — `Pending` gives "No leave requests match this filter."                      |
| Lede warns about the past                     | **pass** — "For a day that already happened, mark it and request a correction instead" |

### Attendance — marking

| Action                                       | Result                                                                                                                                                      |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Choose a section                             | **pass** — register loads, 22 sections listed                                                                                                               |
| Mark all present                             | **pass** — every row flips to `P`                                                                                                                           |
| Change one student to Absent                 | **pass**                                                                                                                                                    |
| Add a per-student note                       | **pass**                                                                                                                                                    |
| Save                                         | **pass** — "Saved attendance for 3 student(s)."                                                                                                             |
| Reload and re-open the same section and date | **pass** — statuses and the note came back exactly                                                                                                          |
| A locked date refuses editing                | **pass** — "This date is locked. It is more than a day old, so a mark can no longer be changed directly." Status buttons are replaced by a read-only badge. |
| Corrections queue                            | **pass (empty)** — "Nothing is waiting on a decision."                                                                                                      |
| File a correction                            | **not reachable** — see Finding C                                                                                                                           |

### Students — list and record

| Action                      | Result                                                                                              |
| --------------------------- | --------------------------------------------------------------------------------------------------- |
| List loads                  | **pass** — 63 students, "Showing 1–25 of 63", Previous disabled on page 1                           |
| Search by name              | **pass** — "Kulkarni" → 3                                                                           |
| Search by admission number  | **pass** — "2026/0045" → 1                                                                          |
| Filter by class and section | **pass** — Class 5 · A → 5                                                                          |
| Filter by status            | **pass** — Active → 3 of those 5                                                                    |
| Open a student record       | **pass** — details, guardians, enrolments, contact, previous school, medical, compliance, documents |
| Edit the medical record     | **pass** — "The health record was saved."                                                           |

| Action                           | Result                                          |
| -------------------------------- | ----------------------------------------------- |
| Restricted fields masked on load | **pass** — `Recorded, masked`, six fields       |
| Reveal                           | **pass** — real values shown, button disappears |
| Emergency contact shown unmasked | **pass** — and the card says so, in the lede    |

### Guardians on a student — pass, and the best-written screen in the product

| Action                                      | Result                                                                                                                                                                                                       |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Open "Add a guardian"                       | **pass** — searches existing guardians first, and says why                                                                                                                                                   |
| Search by phone number                      | **pass** — `98450 10003` → one match                                                                                                                                                                         |
| "Not one of these? Add a new guardian"      | appears once the search narrows                                                                                                                                                                              |
| Attach an existing guardian with a relation | **pass** — "Amitava Bose was attached to this student."                                                                                                                                                      |
| Make main contact                           | **pass** — "…is now the main contact", and it moved off the other guardian                                                                                                                                   |
| Change relation                             | **pass** — "Amitava Bose was saved."                                                                                                                                                                         |
| Remove from this student                    | **pass** — confirmation names the consequence: the guardian record survives, their other children keep them, **and** "They are the main contact for this student, so this student will be left without one." |
| After removing the main contact             | **pass** — the card says "Nobody is the main contact for this student. Choose one below."                                                                                                                    |

### Enrolments

| Action                                            | Result                                               |
| ------------------------------------------------- | ---------------------------------------------------- |
| Add an enrolment (year → class → section cascade) | **pass** — sections repopulate from the chosen class |
| Roll number optional, and says so                 | **pass**                                             |
| History, newest first                             | **pass**                                             |
| The header agrees with it                         | **FAIL — Finding J**                                 |

### Documents — FAIL, the feature is unreachable

| Action                                | Result                                        |
| ------------------------------------- | --------------------------------------------- |
| Open "Add a document"                 | **FAIL — Finding K.** The form never appears. |
| Upload / download / delete a document | **not reachable**                             |

The mechanism is written up under [Finding K](#k--you-cannot-add-a-document-to-a-student) above.

### Import from a spreadsheet — pass, and the best screen in the product

| Action                                             | Result                                                                                                                                                |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen states what it will and will not do         | **pass** — "nothing is written until you say so … if one row is wrong, nothing is imported at all"                                                    |
| "Check the file" disabled before a file is chosen  | **pass — and it says why**: "Choose a file above first."                                                                                              |
| Import disabled before a check                     | **pass — and it says why**: "Check the file first. Import stays unavailable until a check on the file you have chosen comes back with nothing wrong." |
| Check a file with five deliberate faults           | **pass — all five caught, each with an actionable message**                                                                                           |
| → duplicate admission number                       | "a student at this school already has this admission number"                                                                                          |
| → bad date                                         | "is not a date in yyyy-MM-dd form, for example 2015-06-14"                                                                                            |
| → class the school does not have                   | names every class the school **does** have, and says matching ignores capitals and spacing                                                            |
| → invalid gender                                   | "has to be one of MALE, FEMALE or OTHER"                                                                                                              |
| → guardian named with no phone                     | "is required and is empty"                                                                                                                            |
| Group problems by row / by column                  | **pass** — the by-column view explains why it exists: "One column wrong in many rows is usually one mistake"                                          |
| Choosing a new file invalidates the previous check | **pass** — result resets to "Nothing checked yet."                                                                                                    |
| Check a clean file                                 | **pass** — "2 rows checked · 2 rows ready", "Still nothing written. The import below is what writes it."                                              |
| Import                                             | **pass** — "2 students imported into 2026-27 … 1 new guardian added"                                                                                  |
| Two rows sharing one guardian phone                | **pass** — one guardian created, not two, and both children linked to it                                                                              |
| The students appear on the list                    | **pass** — `VERIFY-G-101` and `VERIFY-G-102`, Class 6 · A                                                                                             |

### Export — pass

| Action                             | Result                                                                                                                                                                                                  |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Export                             | **pass** — downloads `students.csv`                                                                                                                                                                     |
| Honours the active filter          | **pass** — with "Verify Import" in the search box, exactly the four matching rows were exported                                                                                                         |
| UTF-8 BOM for Excel                | **pass**                                                                                                                                                                                                |
| Masked by default                  | **pass** — no caste, religion, EWS/RTE, APAAR id or medical column at all. Only what the record shows in full anyway (emergency contact, PEN/UDISE) is present.                                         |
| The unmasked export is not offered | **pass, by design** — `student:student:export_unmasked` is held by no shipped role, so the button is absent for a principal. The unmasked path is therefore **unverified**, which is the correct trade. |

### Signed out by a backend outage

While opening `/academics/sessions` the API was down again and the app bounced to
`/login?returnTo=%2Facademics%2Fsessions`. Two things went right and are worth recording as passes:

| Action                                          | Result                                                                                                                                                                                                                                          |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Where you were trying to go survives the bounce | **pass** — `returnTo` is preserved in the URL                                                                                                                                                                                                   |
| The failure is described honestly               | **pass** — "Could not sign you in. **Chalkbase could not be reached. Check your connection and try again.**" It does not say the password was wrong, which is the easy mistake and the one that sends a user to reset a password that was fine. |

Also seen in the startup log during this outage: the auto-configured in-memory user of
[Finding L](#l--spring-boots-default-user-is-still-created-in-production).

### Dashboard — pass

| Action                                           | Result                                                                                                                                                                               |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Tiles load                                       | **pass** — academic session, 65 students enrolled broken down by class, and two data-quality counts: "7 student(s) with no guardian on record", "2 guardian(s) linked to no student" |
| The enrolment total agrees with the student list | **pass** — 65 both places, after the import                                                                                                                                          |

Nit: the dashboard writes `Started 01 Apr 2026` where every other screen writes `1 Apr 2026`. It is
not going through `formatDay`.

### Academic sessions — mostly pass, one gap

| Action                                      | Result                                                                                 |
| ------------------------------------------- | -------------------------------------------------------------------------------------- |
| List, with the current one marked           | **pass** — "Current session: 2026-27", and only that row lacks a "Make current" button |
| The lede states the stakes                  | **pass** — "changing which one changes what everybody at the school is looking at"     |
| Add a session                               | **pass** — "VERIFY-Overlap added."                                                     |
| Edit with the end before the start          | **pass — refused, and says why**: "A session has to end after it starts."              |
| Add a session that overlaps an existing one | **accepted — Finding M**                                                               |
| Delete a session                            | **there is no way to** — see "Open questions" below                                    |

### Classes and sections — pass, with one sharp edge

| Action                                                | Result                                                                                                                                                                        |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| The lede states the no-delete rule and why            | **pass** — "Nothing here is ever deleted — a class or section that stops running is switched off, stays in place, and can be brought back. It keeps its name while it is off" |
| Every control has a unique accessible name            | **pass** — "Rename A of Nursery", "Stop running B of Class 3", "Move LKG up". Not one bare "Edit" on a screen with a hundred buttons.                                         |
| Stop running a class                                  | works — "Nursery is no longer running. It stays in the ladder and can be brought back."                                                                                       |
| Start running it again                                | **pass** — "Nursery is running again.", fully restored                                                                                                                        |
| A stopped class disappears from the attendance picker | works — 21 sections instead of 23, no Nursery                                                                                                                                 |
| Its enrolled students stay on the student list        | **pass** — all six still listed, still Nursery · A                                                                                                                            |
| Stopping a class that has students enrolled           | **no confirmation at all — Finding O**                                                                                                                                        |

### Subjects — pass

| Action                                             | Result                                                                                                                                                                                                                                             |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| The lede explains why there is no reordering       | **pass** — "A flat catalogue, unlike the class ladder — English does not come before Mathematics"                                                                                                                                                  |
| Add a subject reusing a **retired** subject's code | **pass — refused, and tells you what to do instead**: "That code is already used by a subject that has stopped being taught. VERIFY-Subject-A-Renamed (VFYA) kept its code when it was retired. **Reinstate it rather than adding a second one.**" |

### Roles and access — pass, including the escalation guard

| Action                                                      | Result                                                                                                                                                                                                |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| List roles with their permission counts and template origin | **pass** — 14 roles, each showing "Started from ACCOUNTANT" etc. (ADR-0031 reconciliation, live)                                                                                                      |
| Edit a role's permissions                                   | **pass** — 38 permissions offered                                                                                                                                                                     |
| Grant a permission the actor does not hold                  | **pass — refused (AUTH_011)**: "You cannot grant \"Export unmasked student data\" because you do not hold it yourself. Ask someone who holds it to add it to this role, or leave that box unchecked." |
| The role is unchanged after the refusal                     | **pass** — still 2 permissions, no partial write                                                                                                                                                      |
| Delete a role                                               | **there is no way to** — the open question, and `VERIFY-TestRole`'s own description reads "Synthetic role for verification, to be deleted"                                                            |

### User accounts — pass

| Action                                                     | Result                                                                                                                                                                        |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| List accounts with per-row actions                         | **pass** — Manage roles, Deactivate/Reactivate, Clear lockout, Reset password, each with a unique accessible name                                                             |
| Reset a password                                           | **pass** — the dialog states it ends every session on every device and that the password is shown once                                                                        |
| The new password is shown once, with handling instructions | **pass** — "This will not be shown again… hand it to Farhan Siddiqui directly — **never by email or chat**", a Copy button, and an "I've saved this password" acknowledgement |

### Audit log, signed in as `auditor` — pass, and this is the part that matters most

| Action                                            | Result                                                                                                                        |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Navigation is cut to what the role holds          | **pass** — Dashboard, Settings, User accounts, Audit log. No Students, no Fees, no Attendance.                                |
| The dashboard is a different screen for this role | **pass** — a "Recent activity" feed rather than enrolment tiles                                                               |
| The lede names the time zone                      | **pass** — "Times are shown in this school's time zone (Asia/Kolkata), not yours" (the #78 fix, live)                         |
| Times are converted                               | **pass** — 06:37 UTC renders as 12:07 IST                                                                                     |
| **ADR-0018: field names, never values**           | **pass** — "Fields changed: Active"; "Ends on / Name / Starts on". Not one value anywhere.                                    |
| The detail panel does not leak either             | **pass** — exact time, roles at the time, IP, user agent, trace id, event id. No payload, no values.                          |
| An export is audited with its full column list    | **pass** — all 27 column names recorded, so an inspector can see exactly which columns left the building, and still no values |
| Filter by action                                  | **pass**                                                                                                                      |
| A permission-denied attempt is recorded           | **pass** — my AUTH_011 attempt is in the log as "Permission denied"                                                           |
| The auditor cannot reach students                 | **pass** — "You do not have permission to view students. Ask your principal to add \"View students\" to your role."           |

Also worth noting: a `POST /api/auth/logout` issued as a bare `fetch` was **refused and audited as a
permission denial**, which is CSRF protection doing its job on a cross-origin-shaped request.

Third place the date format diverges: the audit list writes `09 Sept 2026`, `formatDay` writes
`9 Sept 2026`, and the dashboard writes `01 Apr 2026`. The shared `instantFormat` added in #91 uses
`day: 'numeric'`, so merging it settles the audit list; the dashboard still needs doing.

### Enquiry follow-up — pass

| Action                                                          | Result                                                                                                       |
| --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| List with search and three filters (status, source, counsellor) | **pass**                                                                                                     |
| Open an enquiry                                                 | **pass** — child and family, source, captured-by, counsellor, next follow-up                                 |
| Log a follow-up and move the status                             | **pass** — "Follow-up logged.", badge moved `New` → `In progress`                                            |
| Follow-up history                                               | **pass** — "Nandini Apte · 9 Sept 2026 · moved to In progress", the note, and "Next follow-up: 14 Sept 2026" |
| The note field says what it is for                              | **pass** — "Leave as is, or clear it if you are closing the enquiry"                                         |

### School profile — pass

| Action                                                                      | Result                                                                                              |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Load, with every field labelled and grouped (Identity / Location / Contact) | **pass**                                                                                            |
| School code is read-only, and says why                                      | **pass** — "Fixed when the school was onboarded. Ask Chalkbase support if it is wrong."             |
| Time zone field explains itself                                             | **pass** — "An IANA time zone, for example Asia/Kolkata. Almost every school leaves this as it is." |
| All 36 states and union territories offered                                 | **pass**                                                                                            |
| Save                                                                        | **pass** — "School profile saved. Everyone at this school sees the new details."                    |
| Persisted after a reload                                                    | **pass** — the affiliation number came back                                                         |

I briefly caught two "Check this field." messages mid-submit while polling the DOM. The save then
succeeded and the value persisted, so this was a transient state I sampled, **not a defect** — noting
it only so it is not mistaken for one later.

## Not verified, and why

| Area                                             | Why not                                                                                                                                                                                      |
| ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Attendance corrections: file, approve, reject    | No mark exists on a locked date (Finding C). #90 seeds the history that makes this reachable.                                                                                                |
| Documents: upload, download, delete              | The button is broken (Finding K). Fix in #93. ADR-0025's storage adapter is therefore unexercised from the UI.                                                                               |
| Unmasked student export                          | Held by no shipped role, deliberately. Correct trade; leaves the path unexercised.                                                                                                           |
| Circular acknowledgement by a recipient          | Needs a parent account, which is a deferred decision.                                                                                                                                        |
| Converting an enquiry to an admission            | The status moves to `Converted`; whether that should create a student is Phase 2 scope, not a defect.                                                                                        |
| Fee structure "copy from a previous session"     | Present and documented on screen; not driven, because it needs a second session with a structure.                                                                                            |
| `AUTH_010`, deactivating the last access manager | Verified by API in the Phase 1 pass. Not repeated here: the only account to test it with is the one this session is signed in as, and a wrong answer locks the session out mid-verification. |
