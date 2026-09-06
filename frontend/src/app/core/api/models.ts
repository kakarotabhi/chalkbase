/**
 * Hand-written API models.
 *
 * TODO(contracts): once the backend publishes contracts/openapi.json, this file is replaced by a
 * generated client and hand-editing it becomes a review blocker. See docs/development/api-client.md.
 */
export type Board = 'CBSE' | 'CISCE' | 'STATE' | 'IB' | 'CAIE' | 'OTHER';

export interface School {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly board: Board;
  readonly city?: string;
  readonly state?: string;
  readonly active: boolean;
}

export interface CreateSchoolRequest {
  readonly code: string;
  readonly name: string;
  readonly board: Board;
  readonly city?: string;
  readonly state?: string;
}

/* ── School profile (GET/PUT /api/school/profile) ────────────────────────── */

/**
 * The current school, as its own administrators see it.
 *
 * No id here and none in the URL: the tenant is the school (ADR-0011), so *which* school is
 * answered by the session. `code` and `schemaName` address that tenant and cannot be edited — the
 * backend refuses an update that changes either rather than quietly ignoring it.
 */
export interface SchoolProfile {
  readonly code: string;
  readonly schemaName: string;
  readonly name: string;
  readonly board: Board;
  readonly addressLine1?: string;
  readonly addressLine2?: string;
  readonly city?: string;
  readonly state?: string;
  readonly pincode?: string;
  readonly principalName?: string;
  readonly phone?: string;
  readonly email?: string;
  readonly website?: string;
  readonly affiliationNumber?: string;
  /** False when the school has never saved one: the fields above are seeds, not saved answers. */
  readonly configured: boolean;
  readonly updatedAt?: string;
}

/**
 * A full replacement of the profile — every editable field, every time.
 *
 * `code` and `schemaName` go back unchanged so that an attempt to change them is refused loudly
 * instead of dropped silently.
 */
export interface UpdateSchoolProfileRequest {
  readonly code: string;
  readonly schemaName: string;
  readonly name: string;
  readonly board: Board;
  readonly addressLine1: string;
  readonly addressLine2: string;
  readonly city: string;
  readonly state: string;
  readonly pincode: string;
  readonly principalName: string;
  readonly phone: string;
  readonly email: string;
  readonly website: string;
  readonly affiliationNumber: string;
}

/**
 * The envelope every `/api` response uses. Exactly one of `data` and `error` is present.
 * See ADR-0007.
 */
export interface ApiResponse<T> {
  readonly success: boolean;
  readonly data?: T;
  readonly error?: ApiError;
  readonly timestamp: string;
  readonly traceId?: string;
}

export interface ApiError {
  /** Stable machine-readable code, e.g. `VAL_001`. Branch on this, never on `message`. */
  readonly code: string;
  /** Sentence safe to show a user as-is. */
  readonly message: string;
  /** Field name to reason, present on validation failures. */
  readonly details?: Readonly<Record<string, string>>;
}

/* ── Auth (POST /api/auth/*) ─────────────────────────────────────────────── */

export interface LoginRequest {
  readonly schoolCode: string;
  readonly username: string;
  readonly password: string;
  /**
   * Lengthens the session from the 8-hour default to 7 days. Sent only when the parent ticks the
   * box, so a shared machine at the school counter keeps the short session.
   */
  readonly rememberMe: boolean;
}

/** The school a successful sign-in resolves to, as echoed back by the login endpoint. */
export interface LoginSchool {
  readonly code: string;
  readonly name: string;
}

export interface LoginResponse {
  readonly userId: string;
  readonly displayName: string;
  /** True when the school issued a temporary password that must be replaced before continuing. */
  readonly mustChangePassword: boolean;
  readonly school: LoginSchool;
  /**
   * The user's effective permissions, resolved once at sign-in.
   *
   * For deciding what to SHOW — a menu item, a button. Never for deciding what is allowed: the
   * server enforces every permission independently, and a client that hides a control is a
   * convenience, not a boundary (ADR-0005).
   */
  readonly permissions: readonly string[];
}

export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

/* ── Bootstrap (GET /api/me) ─────────────────────────────────────────────── */

/**
 * One node of the menu the server returns.
 *
 * There is deliberately no URL, path or component name here. The server sends a stable dotted
 * `id` (`fees.collect`) and this app maps it to a route it owns, so a backend deploy cannot break
 * navigation by naming a route the frontend does not have (ADR-0008). An id this app cannot
 * resolve is dropped and logged, never rendered as a dead link.
 */
export interface NavigationItem {
  /** Stable, dotted, e.g. `fees.collect`. The key into the frontend's route registry. */
  readonly id: string;
  /** Translation key, e.g. `nav.fees.collect`. Never a display string. */
  readonly labelKey: string;
  /**
   * A school's own renaming of the item — "Fees & Dues" rather than "Fees". Present only when a
   * school overrode the default (Tier-2 configuration, ADR-0006), and it wins over `labelKey`
   * because it is that school's data rather than a translation of ours.
   */
  readonly label?: string;
  /** A hint only. Icons are the frontend's business (ADR-0008), so the registry decides. */
  readonly icon: string | null;
  /** Ascending. Ties are broken by id so the menu never reshuffles between loads. */
  readonly order: number;
  readonly children: readonly NavigationItem[];
}

export interface MeUser {
  readonly id: string;
  readonly displayName: string;
  readonly mustChangePassword: boolean;
}

export interface MeSchool {
  readonly code: string;
  readonly name: string;
}

/**
 * The single bootstrap call: who is signed in, where, what they may see, and the menu.
 *
 * One call rather than five, because the alternative is a request waterfall on every page load,
 * on a school's broadband (ADR-0008).
 */
export interface MeResponse {
  readonly user: MeUser;
  readonly school: MeSchool;
  /**
   * Changes when the user's effective permissions are recomputed. Effective permissions are
   * resolved once per session, so this is what tells a long-lived tab its view has gone stale.
   */
  readonly permissionsVersion: string;
  /**
   * For deciding what to SHOW. Never for deciding what is allowed — the server enforces every
   * permission independently (ADR-0005).
   */
  readonly permissions: readonly string[];
  readonly navigation: readonly NavigationItem[];
}

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
 */
export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/* ── Audit log (GET /api/audit) ──────────────────────────────────────────── */

/** How an audited attempt ended. A closed set on the backend, so a union here. */
export type AuditOutcome = 'SUCCESS' | 'FAILURE' | 'DENIED';

/**
 * One row of the school's audit log (ADR-0018).
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
 * is what the actor filter narrows on; it is null when nobody was authenticated, as on a failed
 * sign-in.
 */
export interface AuditEvent {
  readonly id: string;
  /** ISO-8601 instant, UTC. Rendered in local time — never shown as the raw string. */
  readonly occurredAt: string;
  readonly actorId: string | null;
  readonly actorName: string | null;
  readonly actorRoles: readonly string[];
  readonly action: string;
  readonly outcome: AuditOutcome;
  readonly entityType: string | null;
  readonly entityId: string | null;
  /** Field names only (ADR-0014). Empty for an event that changed nothing, such as a sign-in. */
  readonly changedFields: readonly string[];
  /** Personal data under the DPDP Act. Detail, not a column. */
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  /** The same trace id the ADR-0007 envelope returns, so an error screen leads back to here. */
  readonly traceId: string | null;
}

/* ── Academics (GET/POST/PUT /api/academics/*) ───────────────────────────── */

/**
 * One academic year, and the time axis every academic record hangs off (ADR-0019).
 *
 * `startsOn` and `endsOn` are `yyyy-MM-dd` — calendar days a school picked, never instants. Do not
 * hand either to `new Date(string)`: that parses a bare date as UTC and shifts it by the offset,
 * which in India is five and a half hours of "1 April" landing on 31 March.
 *
 * Exactly one session is `current` at a time, and the server is what enforces that — making one
 * current clears the previous one in the same transaction, so there is no client-side moment where
 * two are current or none is.
 */
export interface AcademicSession {
  readonly id: string;
  readonly name: string;
  /** `yyyy-MM-dd`. */
  readonly startsOn: string;
  /** `yyyy-MM-dd`, after `startsOn`. */
  readonly endsOn: string;
  readonly current: boolean;
}

/**
 * Creating or replacing a session. `current` is deliberately absent: it is changed only through
 * `POST /api/academics/sessions/{id}/current`, so an ordinary edit cannot move the whole school on
 * to a different year as a side effect of fixing a typo.
 */
export interface SaveAcademicSessionRequest {
  readonly name: string;
  readonly startsOn: string;
  readonly endsOn: string;
}

/**
 * One section of a class — "A", "B", "Rose".
 *
 * There is no delete, on purpose (ADR-0019): by the time anything references a section it is too
 * late to decide that removing it was right, so a section that stops running is deactivated and
 * can be brought back. `active` has been on the table since the first migration for that reason.
 */
export interface Section {
  readonly id: string;
  readonly name: string;
  readonly active: boolean;
}

/**
 * One rung of the school's ladder — Nursery, Class 1, Class 12.
 *
 * Structural, not per-session (ADR-0019): this row is a fact about the school, and the academic
 * year appears on the enrolment that puts a student in it.
 *
 * `sequence` is what orders the ladder and is unique, so "which comes first" always has an answer.
 * It is **not** editable field by field — `PUT /api/academics/classes/{id}` does not take it. The
 * whole ladder is reordered at once through `PUT /api/academics/classes/order`, which is what makes
 * swapping two classes one transaction rather than two updates that collide on the unique index.
 */
export interface SchoolClass {
  readonly id: string;
  readonly name: string;
  readonly sequence: number;
  readonly active: boolean;
  /** Ordered by name by the server, inactive ones included and flagged. */
  readonly sections: readonly Section[];
}

/** Creating a class. No position: a new class is appended to the end and moved from there. */
export interface CreateClassRequest {
  readonly name: string;
}

/** Renaming a class, or switching it off and on again. Never its position — see `SchoolClass`. */
export interface UpdateClassRequest {
  readonly name: string;
  readonly active: boolean;
}

/**
 * The whole ladder, in its new order.
 *
 * **Every class id, including the inactive ones.** The server rejects a partial list rather than
 * renumbering it, because a client that dropped one would close the gap it left and lose a rung.
 */
export interface ReorderClassesRequest {
  readonly classIds: readonly string[];
}

export interface CreateSectionRequest {
  readonly name: string;
}

export interface UpdateSectionRequest {
  readonly name: string;
  readonly active: boolean;
}

/* ── Students and guardians (ADR-0020) ───────────────────────────────────── */

/**
 * Everything in this section is **Confidential** under ADR-0014: a child's name, their date of
 * birth, their admission number, and a guardian's phone number and address. None of it may reach a
 * log, an analytics call, or a URL this app constructs. `?q=` is the one exception and it is not
 * really one — it is a search box the user typed into, and typing it was their choice.
 */

/** As recorded on the documents the school is held to. A closed set on the backend. */
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

/**
 * Where a student stands with the school.
 *
 * This field is how a school records that somebody left (ADR-0020 §6) — there is no delete, and
 * nothing in this app may offer one. Fees, attendance and marks all point at a student, and a
 * school that removed one would leave those pointing at nothing.
 */
export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'TRANSFERRED' | 'GRADUATED' | 'WITHDRAWN';

/** What a guardian is to a student. Carried by the link, not by the guardian (ADR-0020 §5). */
export type GuardianRelation = 'FATHER' | 'MOTHER' | 'GUARDIAN' | 'LOCAL_GUARDIAN' | 'OTHER';

/**
 * Where a student sits right now — the answer to the question the office is actually asking.
 *
 * Names rather than ids, because a list row shows them and nothing on a list navigates by class.
 * Null on a student who has been admitted but not yet enrolled, which is a real state between
 * admission and the class lists settling, not a data fault.
 */
export interface CurrentEnrolment {
  readonly sessionName: string;
  readonly className: string;
  readonly sectionName: string;
  /** Assigned after admission, so absent for longer than you would think (ADR-0020 §4). */
  readonly rollNumber?: string;
}

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
 */
export interface StudentSummary {
  readonly id: string;
  readonly admissionNumber: string;
  readonly fullName: string;
  readonly gender: Gender;
  readonly status: StudentStatus;
  /** Absent for a student who has been admitted but is not in a class yet — a real state. */
  readonly currentEnrolment?: CurrentEnrolment;
}

/**
 * One guardian as seen from a student — the link and the person in one shape.
 *
 * `linkId` addresses the link and `guardianId` addresses the person, and the difference is the
 * whole of ADR-0020 §5. Ending the link detaches this guardian from this child; the guardian
 * record survives, because their other children still point at it.
 */
export interface StudentGuardian {
  /** The `student_guardian` row. What `PUT`/`DELETE …/guardians/{linkId}` addresses. */
  readonly linkId: string;
  /** The person. Shared with every sibling at this school. */
  readonly guardianId: string;
  readonly fullName: string;
  readonly relation: GuardianRelation;
  readonly phone?: string;
  readonly email?: string;
  readonly occupation?: string;
  /** At most one per student. The server clears the previous one when a new one is set. */
  readonly primary: boolean;
}

/**
 * One year of a student's schooling.
 *
 * Its own record, and it is what carries the session (ADR-0020 §4) — so promotion is a new row
 * rather than an edit, and a student's history is readable without an audit log. At most one
 * enrolment per session is `active`.
 */
export interface Enrolment {
  readonly id: string;
  readonly sessionId: string;
  readonly sessionName: string;
  readonly classId: string;
  readonly className: string;
  readonly sectionId: string;
  readonly sectionName: string;
  /** Absent until assigned, which is often after the class list settles (ADR-0020 §4). */
  readonly rollNumber?: string;
  readonly active: boolean;
  /** `yyyy-MM-dd`. */
  readonly enrolledOn: string;
}

/** The whole record: the summary, the two dates, the guardians and the enrolment history. */
export interface StudentDetail extends StudentSummary {
  /** `yyyy-MM-dd`, in the past. Confidential — never a URL parameter, never a log line. */
  readonly dateOfBirth: string;
  /** `yyyy-MM-dd`. Nullable in the database, so absent on a record where nobody recorded it. */
  readonly admittedOn?: string;
  /**
   * Read these with `?? []`.
   *
   * An empty list serialises as `[]` rather than being dropped, so in practice they are here — but
   * the cost of being wrong is a `TypeError` from `.map` on the one record that disagrees, and the
   * cost of being careful is two characters.
   */
  readonly guardians?: readonly StudentGuardian[];
  readonly enrolments?: readonly Enrolment[];
}

/**
 * Creating or replacing a student.
 *
 * Deliberately not here: guardians and enrolments. Both are their own records with their own
 * endpoints, and folding them into this body would make "fix a spelling in a name" a request that
 * could also silently move a child to a different class.
 */
export interface SaveStudentRequest {
  /** ≤ 40, required, unique within this school (ADR-0020 §3). */
  readonly admissionNumber: string;
  /** ≤ 200, required. One field — see `StudentSummary`. */
  readonly fullName: string;
  /** `yyyy-MM-dd`, required, in the past. */
  readonly dateOfBirth: string;
  readonly gender: Gender;
  readonly status: StudentStatus;
  /** `yyyy-MM-dd`. Optional: it is nullable, so an unknown admission date is simply not sent. */
  readonly admittedOn?: string;
}

/** Putting a student into a section for a session. Roll number is optional and often unknown. */
export interface CreateEnrolmentRequest {
  readonly academicSessionId: string;
  readonly sectionId: string;
  readonly rollNumber: string | null;
}

/**
 * Correcting an enrolment: the section, the roll number, whether it still stands.
 *
 * The session is absent on purpose — it is what the enrolment *is*, so changing it would be a
 * different enrolment. Moving a student to the next year is a new record (ADR-0020 §4).
 */
export interface UpdateEnrolmentRequest {
  readonly sectionId: string;
  readonly rollNumber: string | null;
  readonly active: boolean;
}

/**
 * One guardian as a person, with how many children at this school point at them.
 *
 * `linkedStudentCount` is the number that makes the shared model visible: a father with four
 * children here is one record showing "4", and correcting his phone number corrects it for all
 * four. A screen that let someone create a fifth copy of him would silently lose that.
 */
export interface GuardianSummary {
  readonly id: string;
  readonly fullName: string;
  readonly phone?: string;
  readonly email?: string;
  readonly occupation?: string;
  readonly linkedStudentCount: number;
}

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
 */
export interface GuardianStudent {
  readonly studentId: string;
  readonly fullName: string;
  readonly admissionNumber: string;
  readonly relation: GuardianRelation;
  /** Whether this guardian is the school's first call for this child. */
  readonly primary: boolean;
  /** Absent for a child admitted but not yet placed, or when no session is current. */
  readonly currentEnrolment?: CurrentEnrolment;
}

/** Creating or replacing a guardian record. The relationship is not here — it is on the link. */
export interface SaveGuardianRequest {
  readonly fullName: string;
  readonly phone: string;
  readonly email: string;
  readonly occupation: string;
}

/**
 * Linking a guardian that already exists to a student.
 *
 * There is no "create a guardian and link them" endpoint, and that is the model working as
 * intended: the guardian has to be found or created as a person first, so siblings end up sharing
 * one record instead of each holding a copy (ADR-0020 §5).
 */
export interface LinkGuardianRequest {
  readonly guardianId: string;
  readonly relation: GuardianRelation;
  /** Setting this clears the previous primary for this student, server-side, in one transaction. */
  readonly primary: boolean;
}

/** Correcting a link: what this person is to this child, and whether they are the main contact. */
export interface UpdateStudentGuardianRequest {
  readonly relation: GuardianRelation;
  readonly primary: boolean;
}

/* ── Bulk import (ADR-0021) ────────────────────────────────────────────── */

/**
 * One thing wrong with one cell of the uploaded file.
 *
 * **`message` never quotes the cell** (ADR-0021 §6, ADR-0014). The row and the column are enough
 * to find the mistake in the spreadsheet, and the value is a child's name or date of birth — which
 * would otherwise travel back over HTTP, into this app, and into whatever screenshot of this
 * screen ends up in a support ticket. Nothing on this screen may reconstruct it either.
 */
export interface ImportError {
  /**
   * The spreadsheet line number the person sees: the header is row 1, so the first data row is 2.
   *
   * Not a zero-based index into the parsed rows. The whole value of this number is that it matches
   * what the row is called in the tool the user is about to go and fix it in.
   */
  readonly row: number;
  /** The CSV column name, as it appears in the header. */
  readonly column: string;
  /** What is wrong, said without the value. Displayed as-is — this one is user-facing copy. */
  readonly message: string;
}

/**
 * What both import endpoints answer with.
 *
 * The same shape from `validate` and from the commit, deliberately, so the screen reads one
 * answer rather than two — the only difference is that `imported` is always 0 from `validate`,
 * which writes nothing.
 *
 * `imported` is 0 or `validRows` and never anything between, because the commit is all-or-nothing
 * (ADR-0021 §2): one bad row and the whole file is rejected.
 */
export interface ImportReport {
  /** Data rows found in the file, not counting the header. */
  readonly totalRows: number;
  /** How many of them would import cleanly. */
  readonly validRows: number;
  /** How many were actually written. Always 0 from `validate`. */
  readonly imported: number;
  /**
   * How many problems were found in total.
   *
   * `errors` carries at most 200 of them, so a pathological file cannot make the response — or this
   * page — unusable. Compare the two to know whether the list is complete, and say so if it is not:
   * a screen that silently shows 200 of 1,800 problems sends a school round the fix-and-re-upload
   * loop believing it is nearly done.
   */
  readonly errorCount: number;
  readonly errors: readonly ImportError[];
}
