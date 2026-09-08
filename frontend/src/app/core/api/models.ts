/**
 * The API models, derived from the generated contract.
 *
 * Every type below is an alias for a schema in `contracts/api-types.ts`, which is generated from
 * `contracts/openapi.json`, which is exported from the running backend on every build. Adding a
 * field to a response record and forgetting to regenerate fails CI; changing one under the
 * frontend's feet fails the frontend build. That is the whole point — see `contracts/README.md`.
 *
 * **Do not hand-write a shape here.** An alias is a claim the compiler checks; an interface is a
 * claim nobody checks, and the two drifting apart silently is what this file exists to prevent.
 * The exceptions are marked where they appear, and each says why it cannot be derived.
 *
 * **The prose is not decoration.** A schema can say `changedFields` is an array of strings. It
 * cannot say that the strings are field NAMES and never values, that recording a value would make
 * the audit log an unencrypted copy of every student record, or that `size` is the page size that
 * was *requested* rather than the number of rows returned. That reasoning is why these comments
 * survived the move to generated types, and why they are kept here on the aliases rather than
 * deleted as duplication of the schema. A generator can regenerate the shapes; nothing regenerates
 * the reasons.
 *
 * Field-level notes that used to sit on an interface member are listed under the type they belong
 * to, because an alias has no members to hang a comment on.
 *
 * **Optional means absent, not null.** `spring.jackson.default-property-inclusion=non_null` drops
 * a null field rather than sending `null`, so the backend marks such fields nullable and they
 * arrive here as `x?: T`. Read them with truthiness or `??`, never `=== null`.
 */
import type { components } from '@contracts/api-types';

type Schemas = components['schemas'];

export type Board = Schemas['SchoolResponse']['board'];

/** One school on the platform register. `SchoolResponse` on the backend. */
export type School = Schemas['SchoolResponse'];

export type CreateSchoolRequest = Schemas['CreateSchoolRequest'];

/* ── Documents (/api/documents) — w3-documents ────────────────────────────
 * A student's certificates, photo, signature and other documents (ADR-0025). Placed here, right
 * after the top-of-file type aliases, rather than appended at the end of the file: several lanes
 * land features in the same wave, and a block that only ever grows at EOF is a block every one of
 * them collides on. Nothing below depends on anything declared after it.
 */

/** What kind of document this is. `DocumentType` on the backend; see its own Javadoc for why every
 * value here is Confidential and none is Restricted (ADR-0025). */
export type DocumentType = Schemas['DocumentSummary']['documentType'];

/** Whether the office has checked a document against the original. */
export type VerificationStatus = Schemas['DocumentSummary']['verificationStatus'];

/**
 * One document's metadata — never its bytes, which is what `GET /api/documents/{id}/content` is
 * for. Confidential (ADR-0014): the type and the original filename can be enough on their own to
 * identify a child, so neither this record nor anything derived from it belongs in a page title,
 * a route, or a log.
 */
export type DocumentSummary = Schemas['DocumentSummary'];

/** A document's metadata, corrected or verified — never the file itself; a new file is a new upload. */
export type UpdateDocumentRequest = Schemas['UpdateDocumentRequest'];

/* ── School profile (GET/PUT /api/school/profile) ────────────────────────── */

/**
 * The current school, as its own administrators see it. `SchoolProfileResponse` on the backend.
 *
 * No id here and none in the URL: the tenant is the school (ADR-0011), so *which* school is
 * answered by the session. `code` and `schemaName` address that tenant and cannot be edited — the
 * backend refuses an update that changes either rather than quietly ignoring it.
 *
 * - `configured` — false when the school has never saved one: the other fields are seeds, not
 *   saved answers.
 * - `timezone` — an IANA zone id, e.g. `Asia/Kolkata`. Never absent, even when `configured` is
 *   false: the registry seeds it the same way it seeds `board`, `city` and `state` (ADR-0032).
 */
export type SchoolProfile = Schemas['SchoolProfileResponse'];

/**
 * A full replacement of the profile — every editable field, every time.
 *
 * `code` and `schemaName` go back unchanged so that an attempt to change them is refused loudly
 * instead of dropped silently.
 */
export type UpdateSchoolProfileRequest = Schemas['UpdateSchoolProfileRequest'];

/**
 * The envelope every `/api` response uses. Exactly one of `data` and `error` is present.
 * See ADR-0007.
 *
 * **Not aliased, because there is no single schema to alias.** `ApiResponse<T>` is generic in
 * Java, and springdoc resolves generics by emitting one flat schema per instantiation —
 * `ApiResponseStudentDetail`, `ApiResponsePageResponseStudentSummary`, and twenty-three more.
 * TypeScript cannot recover the type parameter from those. So the envelope's own fields are taken
 * from the payload-free instantiation and only `data` is re-typed: a field added to `ApiResponse`
 * in Java still lands here without anybody editing this file, which is the property that matters.
 */
export type ApiResponse<T> = Omit<Schemas['ApiResponseVoid'], 'data'> & {
  readonly data?: T;
};

/**
 * The error half of an `ApiResponse`.
 *
 * - `code` — stable machine-readable code, e.g. `VAL_001`. Branch on this, never on `message`.
 * - `message` — a sentence safe to show a user as-is.
 * - `details` — field name to reason, present on validation failures.
 */
export type ApiError = Schemas['ApiError'];

/* ── Auth (POST /api/auth/*) ─────────────────────────────────────────────── */

/**
 * Signing in.
 *
 * - `rememberMe` — lengthens the session from the 8-hour default to 7 days. Sent only when the
 *   parent ticks the box, so a shared machine at the school counter keeps the short session.
 */
export type LoginRequest = Schemas['LoginRequest'];

/**
 * The school a successful sign-in resolves to, as echoed back by the login endpoint.
 *
 * The backend answers this and `MeSchool` with one type, `SchoolSummary`, because it is the same
 * three fields answering the same question. Both names are kept because both readings are useful
 * at their call sites.
 *
 * - `timezone` — an IANA zone id, e.g. `Asia/Kolkata` (ADR-0032). Resolved once at login and
 *   carried on the session for its whole lifetime, the same staleness contract `code` and `name`
 *   already have: a school that changes it mid-session is corrected at the next login, not by a
 *   background refresh. `SessionStore.schoolTimezone()` is how a screen reads it.
 */
export type LoginSchool = Schemas['SchoolSummary'];

/**
 * What a client learns from signing in.
 *
 * - `mustChangePassword` — true when the school issued a temporary password that must be replaced
 *   before continuing.
 * - `permissions` — the user's effective permissions, resolved once at sign-in. For deciding what
 *   to SHOW — a menu item, a button. Never for deciding what is allowed: the server enforces every
 *   permission independently, and a client that hides a control is a convenience, not a boundary
 *   (ADR-0005).
 */
export type LoginResponse = Schemas['LoginResponse'];

export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];

/* ── Bootstrap (GET /api/me) ─────────────────────────────────────────────── */

/**
 * One entry in the menu, exactly as the server sends it (ADR-0008).
 *
 * A plain alias, and it is worth saying why that is notable. This used to intersect an extra
 * `label` for a school's own renaming of an item — "Fees & Dues" rather than "Fees" — and the
 * server has never sent such a field. `navigation-store` read it, and a spec mocked it in and
 * asserted the override won, so an unimplemented capability looked both present and tested. The
 * generated contract types are what surfaced it.
 *
 * Per-school renaming is Tier-2 configuration (ADR-0006) and will arrive on the wire before it
 * arrives here. `navLabel` keeps its optional override parameter as the seam it lands in.
 */
export type NavigationItem = Schemas['NavigationItem'];

export type MeUser = Schemas['MeUser'];

/** See `LoginSchool`: the backend answers both with `SchoolSummary`. */
export type MeSchool = Schemas['SchoolSummary'];

/**
 * The single bootstrap call: who is signed in, where, what they may see, and the menu.
 *
 * One call rather than five, because the alternative is a request waterfall on every page load,
 * on a school's broadband (ADR-0008).
 *
 * - `permissionsVersion` — changes when the user's effective permissions are recomputed. Effective
 *   permissions are resolved once per session, so this is what tells a long-lived tab its view has
 *   gone stale.
 * - `permissions` — for deciding what to SHOW. Never for deciding what is allowed: the server
 *   enforces every permission independently (ADR-0005).
 */
export type MeResponse = Schemas['MeResponse'];

/* ── Paging ──────────────────────────────────────────────────────────────── */

/**
 * One page of a list endpoint, carried as the `data` of an `ApiResponse`.
 *
 * Offset pagination — `?page=0&size=25&sort=occurredAt,desc` — because every list in a school ERP
 * is a bounded, admin-facing table where the user wants "page 7 of 12" and a total, and a cursor
 * cannot answer "how many are there".
 *
 * Four numbers and a list, deliberately: the backend does not serialise Spring Data's own `Page`,
 * so nothing here is coupled to a Spring Data implementation detail.
 *
 * `size` is the size that was *requested*, not the number of rows returned — the last page is
 * shorter. Count `content.length` when you mean "rows on this page".
 *
 * **Generic, so derived rather than aliased**, for the same reason as `ApiResponse<T>`: springdoc
 * emits `PageResponseStudentSummary`, `PageResponseGuardianSummary` and
 * `PageResponseAuditEventResponse` as three flat schemas, and TypeScript cannot recover the type
 * parameter. The four numbers come from one of them so that a fifth field added to `PageResponse`
 * in Java arrives here on its own; only `content` is re-typed.
 */
export type PageResponse<T> = Omit<Schemas['PageResponseStudentSummary'], 'content'> & {
  readonly content: readonly T[];
};

/* ── Audit log (GET /api/audit) ──────────────────────────────────────────── */

/** How an audited attempt ended. A closed set on the backend, so a union here. */
export type AuditOutcome = Schemas['AuditEventResponse']['outcome'];

/**
 * One row of the school's audit log (ADR-0018). `AuditEventResponse` on the backend.
 *
 * ## `action` is a string, not a union, and that is the contract
 *
 * The backend declares its verbs as string constants rather than an enum precisely so a module can
 * name its own action without editing shared code. A union here would quietly re-close the set and
 * make every new verb a compile error in a build that has no reason to know about it. Anything
 * rendering this must cope with a value it has never seen — see `actionLabel` in
 * `features/audit/audit-actions.ts`.
 *
 * ## `changedFields` holds field NAMES
 *
 * Never values, and there is no before/after pair to ask for: the backend rejects anything in this
 * list that is not a plain field name (ADR-0014). Nothing rendering it may imply otherwise.
 *
 * ## `actorName` and `actorRoles` are snapshots
 *
 * They read as they did when the action happened, not as they read now — which is why they are
 * plain strings and not a reference to an account. `actorId` is still the id that was acting, and
 * is what the actor filter narrows on; it is absent when nobody was authenticated, as on a failed
 * sign-in.
 *
 * ## Fields
 *
 * - `occurredAt` — ISO-8601 instant, UTC. Rendered in local time — never shown as the raw string.
 * - `changedFields` — field names only (ADR-0014). Empty for an event that changed nothing, such
 *   as a sign-in.
 * - `ipAddress` — personal data under the DPDP Act. Detail, not a column.
 * - `traceId` — the same trace id the ADR-0007 envelope returns, so an error screen leads back to
 *   here.
 * - `recordCount` — how many records a bulk action touched, absent when it touched one. An import
 *   of six hundred students is one event, and this is the number that makes it legible as one.
 *   It arrived with the generated types: the hand-written model predated the column and never had
 *   it, so nothing renders it yet.
 */
export type AuditEvent = Schemas['AuditEventResponse'];

/* ── Academics (GET/POST/PUT /api/academics/*) ───────────────────────────── */

/**
 * One academic year, and the time axis every academic record hangs off (ADR-0019).
 * `AcademicSessionResponse` on the backend.
 *
 * `startsOn` and `endsOn` are `yyyy-MM-dd` — calendar days a school picked, never instants. Do not
 * hand either to `new Date(string)`: that parses a bare date as UTC and shifts it by the offset,
 * which in India is five and a half hours of "1 April" landing on 31 March. `endsOn` is always
 * after `startsOn`.
 *
 * Exactly one session is `current` at a time, and the server is what enforces that — making one
 * current clears the previous one in the same transaction, so there is no client-side moment where
 * two are current or none is.
 */
export type AcademicSession = Schemas['AcademicSessionResponse'];

/**
 * Creating or replacing a session. `current` is deliberately absent: it is changed only through
 * `POST /api/academics/sessions/{id}/current`, so an ordinary edit cannot move the whole school on
 * to a different year as a side effect of fixing a typo.
 */
export type SaveAcademicSessionRequest = Schemas['SaveAcademicSessionRequest'];

/**
 * One section of a class — "A", "B", "Rose". `SectionResponse` on the backend.
 *
 * There is no delete, on purpose (ADR-0019): by the time anything references a section it is too
 * late to decide that removing it was right, so a section that stops running is deactivated and
 * can be brought back. `active` has been on the table since the first migration for that reason.
 */
export type Section = Schemas['SectionResponse'];

/**
 * One rung of the school's ladder — Nursery, Class 1, Class 12. `SchoolClassResponse` on the
 * backend.
 *
 * Structural, not per-session (ADR-0019): this row is a fact about the school, and the academic
 * year appears on the enrolment that puts a student in it.
 *
 * `sequence` is what orders the ladder and is unique, so "which comes first" always has an answer.
 * It is **not** editable field by field — `PUT /api/academics/classes/{id}` does not take it. The
 * whole ladder is reordered at once through `PUT /api/academics/classes/order`, which is what makes
 * swapping two classes one transaction rather than two updates that collide on the unique index.
 *
 * - `sections` — ordered by name by the server, inactive ones included and flagged.
 */
export type SchoolClass = Schemas['SchoolClassResponse'];

/** Creating a class. No position: a new class is appended to the end and moved from there. */
export type CreateClassRequest = Schemas['CreateSchoolClassRequest'];

/** Renaming a class, or switching it off and on again. Never its position — see `SchoolClass`. */
export type UpdateClassRequest = Schemas['UpdateSchoolClassRequest'];

/**
 * The whole ladder, in its new order.
 *
 * **Every class id, including the inactive ones.** The server rejects a partial list rather than
 * renumbering it, because a client that dropped one would close the gap it left and lose a rung.
 */
export type ReorderClassesRequest = Schemas['ReorderSchoolClassesRequest'];

export type CreateSectionRequest = Schemas['CreateSectionRequest'];

export type UpdateSectionRequest = Schemas['UpdateSectionRequest'];

/* ── Subjects (GET/POST/PUT /api/academics/subjects) ─────────────────────── */

/**
 * A subject this school teaches — English, Mathematics. `SubjectResponse` on the backend.
 *
 * A flat catalogue entry, unlike `SchoolClass`: a subject has no ladder position and no relation to
 * a class or a section here. Which classes teach which subjects is a subject allocation, which
 * belongs to the timetable and marks modules once they exist — not to this one.
 *
 * There is no delete, on purpose (ADR-0019, same reasoning as `Section`): by the time anything
 * references a subject it is too late to decide that removing it was right, so a subject that stops
 * being taught is deactivated and can be brought back.
 */
export type Subject = Schemas['SubjectResponse'];

/** Creating a subject. No `active`: a subject is created active, same as a class. */
export type CreateSubjectRequest = Schemas['CreateSubjectRequest'];

/** Renaming, recoding, retiring or reinstating a subject. */
export type UpdateSubjectRequest = Schemas['UpdateSubjectRequest'];

/* ── Students and guardians (ADR-0020) ───────────────────────────────────── */

/**
 * Everything in this section is **Confidential** under ADR-0014: a child's name, their date of
 * birth, their admission number, and a guardian's phone number and address. None of it may reach a
 * log, an analytics call, or a URL this app constructs. `?q=` is the one exception and it is not
 * really one — it is a search box the user typed into, and typing it was their choice.
 */

/** As recorded on the documents the school is held to. A closed set on the backend. */
export type Gender = Schemas['StudentSummary']['gender'];

/**
 * Where a student stands with the school.
 *
 * This field is how a school records that somebody left (ADR-0020 §6) — there is no delete, and
 * nothing in this app may offer one. Fees, attendance and marks all point at a student, and a
 * school that removed one would leave those pointing at nothing.
 */
export type StudentStatus = Schemas['StudentSummary']['status'];

/** What a guardian is to a student. Carried by the link, not by the guardian (ADR-0020 §5). */
export type GuardianRelation = Schemas['StudentGuardian']['relation'];

/**
 * Where a student sits right now — the answer to the question the office is actually asking.
 *
 * Names rather than ids, because a list row shows them and nothing on a list navigates by class.
 * Absent on a student who has been admitted but not yet enrolled, which is a real state between
 * admission and the class lists settling, not a data fault.
 *
 * - `rollNumber` — assigned after admission, so absent for longer than you would think
 *   (ADR-0020 §4).
 */
export type CurrentEnrolment = Schemas['CurrentEnrolment'];

/**
 * One row of the student list.
 *
 * ## One name field, not three
 *
 * `fullName` is the name as it appears on the records the school will be held to, and there is no
 * first/middle/last split to reassemble (ADR-0020 §1). A great many Indian students have no
 * surname; a required "Last name" box forces a clerk to invent one, and what they invent goes on
 * the certificate. Nothing in this app may sort by, group by, or display a surname, because there
 * is not one.
 *
 * - `currentEnrolment` — absent for a student who has been admitted but is not in a class yet, a
 *   child who has left, or a school that has not yet said which year it is in. A real state.
 */
export type StudentSummary = Schemas['StudentSummary'];

/**
 * One guardian as seen from a student — the link and the person in one shape.
 *
 * `linkId` addresses the link and `guardianId` addresses the person, and the difference is the
 * whole of ADR-0020 §5. Ending the link detaches this guardian from this child; the guardian
 * record survives, because their other children still point at it.
 *
 * - `linkId` — the `student_guardian` row. What `PUT`/`DELETE …/guardians/{linkId}` addresses.
 * - `guardianId` — the person. Shared with every sibling at this school.
 * - `primary` — at most one per student. The server clears the previous one when a new one is set.
 */
export type StudentGuardian = Schemas['StudentGuardian'];

/**
 * One year of a student's schooling.
 *
 * Its own record, and it is what carries the session (ADR-0020 §4) — so promotion is a new row
 * rather than an edit, and a student's history is readable without an audit log. At most one
 * enrolment per session is `active`.
 *
 * - `active` — false for a placement that has ended. A student may have an ended enrolment and a
 *   live one in the same year — moved section mid-term.
 * - `rollNumber` — absent until assigned, which is often after the class list settles
 *   (ADR-0020 §4).
 * - `enrolledOn` — `yyyy-MM-dd`.
 * - `sessionName`, `classId`, `className`, `sectionName` — optional, and the reason is in
 *   `Enrolment.of`: they are resolved from `academics` through its named interface, and a DTO that
 *   threw when a retired-then-repaired academic structure failed to resolve would turn a child's
 *   history into a 500 on a screen that only wanted to show it. The row comes back with the name
 *   missing instead. It cannot happen through the API — both are foreign keys, both resolved
 *   before the row was written — but the contract is honest about it, so treat a missing name as
 *   "unknown" rather than as a bug in the screen.
 */
export type Enrolment = Schemas['Enrolment'];

/**
 * The whole record: the summary fields, the two dates, the guardians and the enrolment history.
 *
 * - `dateOfBirth` — `yyyy-MM-dd`, in the past. Confidential — never a URL parameter, never a log
 *   line.
 * - `admittedOn` — `yyyy-MM-dd`. Nullable in the database, so absent on a record where nobody
 *   recorded it.
 * - `guardians`, `enrolments` — always present. The hand-written model had these optional and told
 *   readers to use `?? []`, on the grounds that the cost of being wrong was a `TypeError` from
 *   `.map` on the one record that disagreed. The contract now settles it: the record's compact
 *   constructor replaces a null list with an empty one, so the field is required and an empty list
 *   serialises as `[]`. The `?? []` already written is harmless and can stay.
 *
 * Structurally a superset of `StudentSummary` rather than an extension of it, because the backend
 * declares the two records independently. Assigning a `StudentDetail` where a `StudentSummary` is
 * wanted still works.
 *
 * - `contact`, `previousSchool` — absent when nothing has been entered for that section. Not a
 *   fault; an ordinary first admission has no previous school.
 * - `medical`, `compliance` — always present, and always **masked**: see `MedicalSummary` and
 *   `ComplianceSummary`. Fetching this record is never the audited read ADR-0014 asks for.
 */
export type StudentDetail = Schemas['StudentDetail'];

/**
 * Creating or replacing a student.
 *
 * Deliberately not here: guardians and enrolments. Both are their own records with their own
 * endpoints, and folding them into this body would make "fix a spelling in a name" a request that
 * could also silently move a child to a different class.
 *
 * - `admissionNumber` — ≤ 40, required, unique within this school (ADR-0020 §3).
 * - `fullName` — ≤ 200, required. One field — see `StudentSummary`.
 * - `dateOfBirth` — `yyyy-MM-dd`, required, in the past.
 * - `admittedOn` — `yyyy-MM-dd`. Optional: it is nullable, so an unknown admission date is simply
 *   not sent.
 */
export type SaveStudentRequest = Schemas['SaveStudentRequest'];

/**
 * Putting a student into a section for a session. Roll number is optional and often unknown.
 *
 * The generated type says `rollNumber?: string` where the hand-written one said
 * `rollNumber: string | null`. Both reach the same server behaviour — the field carries no
 * `@NotNull`, so an absent value and an explicit `null` are both "no roll number yet" — and the
 * call sites that send `null` still compile. The schema describes the field the backend declares,
 * which is a `String` with a length bound and no null semantics of its own.
 */
export type CreateEnrolmentRequest = Schemas['CreateEnrolmentRequest'];

/**
 * Correcting an enrolment: the section, the roll number, whether it still stands.
 *
 * The session is absent on purpose — it is what the enrolment *is*, so changing it would be a
 * different enrolment. Moving a student to the next year is a new record (ADR-0020 §4).
 */
export type UpdateEnrolmentRequest = Schemas['UpdateEnrolmentRequest'];

/**
 * One guardian as a person, with how many children at this school point at them.
 *
 * `linkedStudentCount` is the number that makes the shared model visible: a father with four
 * children here is one record showing "4", and correcting his phone number corrects it for all
 * four. A screen that let someone create a fifth copy of him would silently lose that.
 */
export type GuardianSummary = Schemas['GuardianSummary'];

/**
 * One child, as seen from a guardian's record — the other direction of `StudentGuardian`.
 *
 * This is what `linkedStudentCount` cannot answer. The count says the shared model is working;
 * somebody staring at two records that both read "Suresh Kulkarni, linked to 2 students" is asking
 * *which* two, and a number cannot tell them. Without the names the safest-looking move is a third
 * record, which is the duplication ADR-0020 §5 exists to prevent.
 *
 * `GET /api/guardians/{id}/students` is guarded by `student:student:read`, **not** by the guardian
 * read. It answers with children's names and admission numbers, so it is gated like every other
 * piece of student data — someone who may see the directory but not the roll gets the count and no
 * names, and a screen showing this must cope with a 403 while the rest of the row still works.
 *
 * - `primary` — whether this guardian is the school's first call for this child.
 * - `currentEnrolment` — absent for a child admitted but not yet placed, or when no session is
 *   current.
 */
export type GuardianStudent = Schemas['GuardianStudent'];

/** Creating or replacing a guardian record. The relationship is not here — it is on the link. */
export type SaveGuardianRequest = Schemas['SaveGuardianRequest'];

/**
 * Linking a guardian that already exists to a student.
 *
 * There is no "create a guardian and link them" endpoint, and that is the model working as
 * intended: the guardian has to be found or created as a person first, so siblings end up sharing
 * one record instead of each holding a copy (ADR-0020 §5).
 *
 * - `primary` — setting this clears the previous primary for this student, server-side, in one
 *   transaction.
 */
export type LinkGuardianRequest = Schemas['LinkGuardianRequest'];

/** Correcting a link: what this person is to this child, and whether they are the main contact. */
export type UpdateStudentGuardianRequest = Schemas['UpdateStudentGuardianRequest'];

/* ── Contact, medical, previous school and compliance (FR-028, FR-029, FR-033, FR-034) ────── */
//
// Four more sections of `StudentDetail`. `medical` and `compliance` are the masked shapes
// (`MedicalSummary`, `ComplianceSummary`) — every Restricted field on them is a `hasX: boolean`,
// never the value. `MedicalDetail` and `ComplianceDetail` are what `GET …/medical/restricted` and
// `GET …/compliance/restricted` answer with instead: the real values, and nothing else fetches
// them. Calling either is an audited read (ADR-0014) — see `StudentsApi.revealMedical` and
// `StudentsApi.revealCompliance`.

/** A student's own address, phone and email. Confidential, not masked — same tier as a guardian's. */
export type ContactDetail = Schemas['ContactDetail'];

/** Correcting a student's contact details. Every field optional. */
export type SaveContactRequest = Schemas['SaveContactRequest'];

/**
 * Where a student came from, and their transfer certificate.
 *
 * Confidential, not masked. Absent on `StudentDetail` for a student with no previous school on
 * file — an ordinary first admission, not a gap.
 */
export type PreviousSchoolDetail = Schemas['PreviousSchoolDetail'];

/** Correcting a student's previous school and transfer certificate. Every field optional. */
export type SavePreviousSchoolRequest = Schemas['SavePreviousSchoolRequest'];

/**
 * A student's health record, **masked**.
 *
 * `hasBloodGroup`, `hasCwsnStatus`, `hasDisabilityDetails`, `hasAllergies`,
 * `hasChronicConditions`, `hasMedication` — whether each Restricted field has been recorded, never
 * the value. The emergency contact fields are Confidential, not Restricted, and are sent here in
 * full: a name and a phone number, not a health fact.
 */
export type MedicalSummary = Schemas['MedicalSummary'];

/**
 * A student's health record, **unmasked**: the real values of the six Restricted fields. Only
 * `GET /api/students/{id}/medical/restricted` returns this.
 */
export type MedicalDetail = Schemas['MedicalDetail'];

/** Entering or correcting a student's health record. Every field optional. */
export type SaveMedicalRequest = Schemas['SaveMedicalRequest'];

/**
 * A student's UDISE+/board identifiers and statutory categories, **masked**.
 *
 * `penUdiseId` and `boardRegistrationNumber` are Confidential identifiers, sent in full, like an
 * admission number. `hasCasteCategory`, `hasReligion`, `hasSpecialCategory`, `hasApaarId` say only
 * whether each Restricted field has been recorded. `apaarConsentGiven`, `apaarConsentGivenBy` and
 * `apaarConsentGivenAt` are the consent record ADR-0014 asks APAAR to carry — sent in full, because
 * a recorded decision is metadata, not the APAAR id itself.
 */
export type ComplianceSummary = Schemas['ComplianceSummary'];

/**
 * A student's caste and community, religion, EWS/BPL/RTE category and APAAR id, **unmasked**. Only
 * `GET /api/students/{id}/compliance/restricted` returns this.
 */
export type ComplianceDetail = Schemas['ComplianceDetail'];

/**
 * Entering or correcting a student's UDISE+/board identifiers and statutory categories.
 *
 * `apaarId` cannot be saved with `apaarConsentGiven` false — the backend refuses it
 * (`STU_019`, `APAAR_REQUIRES_CONSENT`). `apaarConsentGivenAt` is not here: the server sets it from
 * the moment consent first becomes true.
 */
export type SaveComplianceRequest = Schemas['SaveComplianceRequest'];

/* ── Bulk import (ADR-0021) ────────────────────────────────────────────── */

/**
 * One thing wrong with one cell of the uploaded file.
 *
 * **`message` never quotes the cell** (ADR-0021 §6, ADR-0014). The row and the column are enough
 * to find the mistake in the spreadsheet, and the value is a child's name or date of birth — which
 * would otherwise travel back over HTTP, into this app, and into whatever screenshot of this
 * screen ends up in a support ticket. Nothing on this screen may reconstruct it either.
 *
 * - `row` — the spreadsheet line number the person sees: the header is row 1, so the first data
 *   row is 2. Not a zero-based index into the parsed rows. The whole value of this number is that
 *   it matches what the row is called in the tool the user is about to go and fix it in.
 * - `column` — the CSV column name, as it appears in the header. **The empty string**, never
 *   absent, when the problem belongs to the row as a whole rather than to one of its cells: a
 *   screen groups those under a heading of their own, and a field that was sometimes missing would
 *   make that grouping a null check rather than a comparison. This is the one place the "omit a
 *   null" convention is deliberately not applied, which is why the field is required here.
 * - `message` — what is wrong, said without the value. Displayed as-is: this one is user-facing
 *   copy.
 */
export type ImportError = Schemas['ImportError'];

/**
 * What both import endpoints answer with.
 *
 * The same shape from `validate` and from the commit, deliberately, so the screen reads one
 * answer rather than two — the only difference is that `imported` is always 0 from `validate`,
 * which writes nothing.
 *
 * `imported` is 0 or `validRows` and never anything between, because the commit is all-or-nothing
 * (ADR-0021 §2): one bad row and the whole file is rejected. The four guardian counts follow the
 * same rule (ADR-0021 §4): they say what was actually written, never what a clean file would go on
 * to do, so they are 0 from `validate` and 0 from a commit that found anything wrong.
 *
 * - `totalRows` — data rows found in the file, not counting the header.
 * - `validRows` — how many of them would import cleanly.
 * - `imported` — how many were actually written. Always 0 from `validate`.
 * - `guardiansCreated` — new guardian person records actually written: a phone number that matched
 *   nobody already in this school's directory and nobody else in the file either.
 * - `guardiansMatched` — distinct guardians the import's students were linked to **without**
 *   creating a new person.
 * - `guardianLinksCreated` — student-guardian links actually written, in total. Can exceed
 *   `guardiansCreated + guardiansMatched`: four rows naming one father produce one guardian and
 *   four links, so this counts rows with a guardian, not guardians.
 * - `studentsLinkedToExistingGuardians` — how many of those links pointed at a guardian that
 *   already existed before this import ran. "Created 12 guardians, linked 47 students to existing
 *   guardians" is the sentence this pair exists to make possible — the difference between trusting
 *   this screen and checking six hundred rows by hand.
 * - `errorCount` — how many problems were found in total. `errors` carries at most 200 of them, so
 *   a pathological file cannot make the response — or this page — unusable. Compare the two to
 *   know whether the list is complete, and say so if it is not: a screen that silently shows 200
 *   of 1,800 problems sends a school round the fix-and-re-upload loop believing it is nearly done.
 */
export type ImportReport = Schemas['ImportReport'];

/* ── Access: users, roles and permissions (/api/access/*, ADR-0005) ──────── */

/**
 * Enough of an account to list it, or to say who holds a role.
 *
 * `status` is a plain string on the wire (`UserAccount.status` is typed `String` on the entity, not
 * the `AccountStatus` enum) — the same reason permission codes have no union in the contract, see
 * `Permissions`. `access-shared.ts` carries the two values this build knows, `ACTIVE` and
 * `DISABLED`, and copes with a third the way `navLabel` copes with an unknown navigation id.
 *
 * **No `lockedUntil` here.** The roster this backs (`GET /api/access/users`) answers this shape for
 * every account in one call, and a lockout is not on it — only the four per-account write endpoints
 * answer `UserAccountResponse`, which does carry it. So the roster screen cannot show "locked"
 * as a fact about a row; it can only offer **Clear lockout** on every active account and say
 * afterwards, from that response, whether anything was actually cleared. Adding a per-row lockout
 * badge would mean fetching each account individually — the N+1 request list screens exist to
 * avoid — so this is a known gap, not an oversight; see `docs/status.md`.
 */
export type UserSummary = Schemas['UserSummary'];

/**
 * A new account. Username and display name only — see `IdentityErrorCode.USERNAME_TAKEN` (`AUTH_009`)
 * for the one way this is refused.
 */
export type CreateUserAccountRequest = Schemas['CreateUserAccountRequest'];

/**
 * The account just created, **with the temporary password it must be handed along with**.
 *
 * `temporaryPassword` is shown exactly once: nothing on the backend stores it, and no endpoint can
 * retrieve it again — only `POST .../reset-password`, which issues a different one. It is
 * Confidential (ADR-0014): never logged, never put in a URL or a page title, and never left on
 * screen behind a route a back button can return to.
 */
export type NewUserAccountResponse = Schemas['NewUserAccountResponse'];

/**
 * One account's own state, as confirmed after deactivating, reactivating, unlocking it, or reading
 * it back post-reset. The one shape in this section that carries `lockedUntil` — see `UserSummary`.
 */
export type UserAccountResponse = Schemas['UserAccountResponse'];

/**
 * What an admin password reset hands back. `temporaryPassword` is Confidential and shown exactly
 * once, the same as `NewUserAccountResponse.temporaryPassword` — see there for what that means for
 * this screen.
 *
 * The account's own sessions are already gone by the time this response arrives (ADR-0023): a reset
 * ends every session the target holds, immediately, including the caller's own if they reset
 * themselves. See `user-roster.ts` for what this screen does about that.
 */
export type TemporaryPasswordResponse = Schemas['TemporaryPasswordResponse'];

/**
 * One permission this build knows how to enforce, as `GET /api/access/permissions` answers it — the
 * same list at every school, because permissions are code (ADR-0005). Not to be confused with
 * `Permissions` in `core/auth/permissions.ts`, which is this app's own closed list of the codes it
 * knows how to *ask about*; this is the backend's catalogue of every code that exists, used here to
 * build a role's permission editor and to word `AUTH_011` against a label a person recognises.
 */
export type PermissionDefinition = Schemas['PermissionDefinition'];

/**
 * One of this school's roles, and the permissions it currently carries.
 *
 * `templateCode` is provenance only — the shipped template this role was copied from at onboarding,
 * or absent if the school invented it from nothing. Editing the role changes nothing at any other
 * school (ADR-0005): the template is where it started, not what it is tied to.
 */
export type RoleResponse = Schemas['RoleResponse'];

/**
 * A new, school-owned role: a name, an optional description, and the permissions it starts with.
 *
 * Every permission listed must already be held by the account creating the role — see
 * `IdentityErrorCode.CANNOT_GRANT_PERMISSION_YOU_DO_NOT_HOLD` (`AUTH_011`). No `code` field: the
 * backend derives one from the name.
 */
export type CreateRoleRequest = Schemas['CreateRoleRequest'];

/**
 * A role's complete replacement permission set, **never a delta**. `PUT` replaces the row wholesale;
 * a screen that sends only what changed silently deletes the rest. Only the permissions being
 * *added* — present here, absent from the role today — are checked against `AUTH_011`; removing one
 * is never guarded, because taking access away cannot escalate anything.
 */
export type UpdateRolePermissionsRequest = Schemas['UpdateRolePermissionsRequest'];

/** One grant a user holds: which role, over how much of the school, and for how long. */
export type GrantResponse = Schemas['GrantResponse'];

/**
 * "This user holds this role, over this much of the school, for this long."
 *
 * `scopeId` is required for every `scopeType` except `SCHOOL` and `SELF`, and `WARD` is never a
 * legal value here — a parent's reach is derived from the guardian-of relationship and is never
 * assigned. Granting hands the holder every permission the role carries, so the acting account must
 * already hold all of them itself (`AUTH_011`, same guard as `CreateRoleRequest`).
 */
export type GrantRoleRequest = Schemas['GrantRoleRequest'];

/**
 * What a grant is scoped to. Not a standalone schema on the wire — it appears only as the enum
 * inline on `GrantRoleRequest.scopeType` — so it is derived from there rather than duplicated.
 */
export type ScopeType = Schemas['GrantRoleRequest']['scopeType'];
/* ── Dashboard (GET /api/dashboard) ───────────────────────────────────────── */

/**
 * The landing screen's tiles, cut down server-side to what the caller may see (ADR-0008 applied to
 * data, not only to menus).
 *
 * **Every field is independently optional, and that is the authorization model, not an accident of
 * the shape.** Each tile is gated on the read permission the module that owns its data already
 * defined, and the backend decides which of them the caller holds before building this object. A
 * tile the caller may not see is never sent — never `null`, simply absent, the same convention
 * every other optional field in this file follows. Render only the tiles that are present; there is
 * nothing to explain about why one is missing, the same way a menu item never explains why it was
 * filtered.
 */
export type Dashboard = Schemas['DashboardResponse'];

/**
 * The current academic session, and whether the school has set one at all.
 *
 * `set` is `false`, with every other field absent, when no session has been marked current yet
 * (ADR-0019) — not omitted as a whole tile, because "no session" is itself the thing a principal
 * needs to be told, unlike a tile gated purely by permission.
 */
export type DashboardSession = Schemas['SessionTile'];

/**
 * Students enrolled this session, and how they are spread across the school's classes.
 *
 * Absent from {@link Dashboard} entirely when no session is current: "enrolled" means nothing
 * without a year to enrol into, and a zero would read as an empty roll rather than as a school that
 * has not opened its year.
 */
export type DashboardStudents = Schemas['StudentsTile'];

/** One row of `DashboardStudents.byClass`, ordered by the school's own ladder, not alphabetically. */
export type DashboardClassCount = Schemas['ClassEnrolmentCount'];

/**
 * Two data-quality gaps a school can act on directly: a child with nobody to call, and a person in
 * the guardian directory attached to nobody.
 *
 * The two fields are gated by two different permissions and may arrive independently — a caller
 * holding only one sees that field populated and the other absent, never a zero standing in for
 * "not permitted to know".
 */
export type DashboardLinkageGaps = Schemas['LinkageGapsTile'];

/**
 * The five most recent audit events, for whoever holds `platform:audit:read`. Reuses
 * {@link AuditEvent} rather than a dashboard-shaped copy of it — same rules, same redaction, same
 * "field names never values" (ADR-0018).
 */
export type DashboardRecentAudit = Schemas['RecentAuditTile'];

/* ── Reference data (GET /api/reference/states, GET /api/schools/boards) — ADR-0029 ───────── */

/**
 * One choice in a reference list — a state or a board — shaped for `cb-select`'s `SelectOption`.
 *
 * `value` is what a form submits and what the backend already stores: for a state this is the
 * name itself (`school_profile.state` is, and remains, a plain string), not an internal seeding
 * key nothing on the wire needs. For a board it is the enum constant's name, e.g. `"CBSE"`. `label`
 * is what a person reads — equal to `value` for a state, and different for a board (`CISCE` /
 * `"CISCE (ICSE / ISC)"`).
 *
 * Two endpoints share this shape rather than one: states are `platform`'s (global, seeded, one
 * table for every school) and boards are `school`'s own enum (see `Board` above) — moving both
 * behind one module would mean `platform`, the shared kernel, importing a feature module's type,
 * which runs the dependency backwards.
 */
export type ReferenceItem = Schemas['ReferenceItemResponse'];
/* ── Attendance (Phase 2, GET/POST /api/attendance/**) ───────────────────── */

/**
 * A section's daily attendance for one date — the marking screen's whole read.
 * `SectionAttendanceView` on the backend.
 *
 * `locked` is true once this date's edit window has passed (end of the attendance day plus 24
 * hours, ADR-0030). The screen disables direct editing once it is, and offers a correction request
 * per student instead.
 */
export type SectionAttendanceView = Schemas['SectionAttendanceView'];

/**
 * One student on a section's roster, and their mark for the date if one exists yet.
 * `AttendanceStudentMark` on the backend.
 *
 * `markId` and `status` are both absent until this student has been marked at all today — treat
 * that as "not yet marked", never as an implicit absence. `editable` mirrors the view's own
 * `locked`, carried per row so a row never has to be cross-referenced against its parent to know
 * whether it may still be changed directly.
 */
export type AttendanceStudentMark = Schemas['AttendanceStudentMark'];

/** One of the six marks Phase 0 decision 8 settled on. A closed set on the backend, so a union here. */
export type AttendanceStatus = Schemas['CorrectionRequestResponse']['previousStatus'];

/**
 * Marking or editing a section's attendance for one date, in one call.
 *
 * Whole and idempotent by design: every student the caller wants marked goes in `entries`, present
 * included — a class teacher's "mark all present, then change the three who are not" is one
 * request, not one per student. There is no `academicSessionId` here: the backend resolves the
 * school's current session itself, so a client cannot mark today's roll call against a stale year.
 */
export type MarkAttendanceRequest = Schemas['MarkAttendanceRequest'];

/** One student's status within a {@link MarkAttendanceRequest}. */
export type AttendanceEntryRequest = Schemas['AttendanceEntryRequest'];

/** One day of one student's attendance history. `StudentAttendanceRecord` on the backend. */
export type StudentAttendanceRecord = Schemas['StudentAttendanceRecord'];

/** A teacher's request to change a locked mark. `reason` is required — an admin decides on it. */
export type RequestCorrectionRequest = Schemas['RequestCorrectionRequest'];

/**
 * One correction request, as the admin queue and a teacher's own history both read it.
 * `CorrectionRequestResponse` on the backend.
 *
 * Carries the student's name and the attendance date directly rather than only an id, because this
 * is always read as a queue someone acts on — "whose attendance, for which day" belongs on the row.
 * `decidedAt` and `decisionNote` are both absent while `decision` is `PENDING`.
 */
export type CorrectionRequestResponse = Schemas['CorrectionRequestResponse'];

/** Where a correction request stands. A closed set on the backend, so a union here. */
export type CorrectionDecision = Schemas['CorrectionRequestResponse']['decision'];

/** An administrator's decision on a correction request: `APPROVED` or `REJECTED`, never `PENDING`. */
export type DecideCorrectionRequest = Schemas['DecideCorrectionRequest'];
