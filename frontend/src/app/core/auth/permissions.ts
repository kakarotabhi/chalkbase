/**
 * Every permission code this app knows how to ask about.
 *
 * ## Why these are literals, and why there is exactly one copy of them
 *
 * The backend is the source of truth: each module declares its own codes in a
 * `PermissionProvider` (`StudentPermissions`, `AcademicsPermissions`, `SchoolPermissions`,
 * `IdentityPermissions`, `AuditPermissions`), and `PermissionCatalog` refuses to start if two
 * modules claim the same one. Those constants are what `@PreAuthorize` enforces against.
 *
 * They do **not** reach the generated contract. `GET /api/me` answers `permissions` as
 * `List<String>`, and `GET /api/access/permissions` answers a list of `PermissionDefinition`
 * records whose `code` is likewise a plain string — so `contracts/api-types.ts` types both as
 * `readonly string[]` / `readonly code: string`. There is no enum on the wire and therefore no
 * union to alias, which is the one case `models.ts` cannot cover. Deriving them would mean either
 * a build step that parses Java, or a runtime fetch of the catalogue before the first button can
 * be drawn — neither of which buys anything, because a code that disappeared from the backend
 * would then simply become a permission nobody holds, and the affordance would hide itself.
 *
 * So they are literals, and the discipline that keeps them honest is that they are literals **in
 * this file only**. Nothing else in the app writes a permission string: templates read a signal, a
 * component names a constant from here. A rename on the backend needs a migration rewriting every
 * school's `role_permission` rows anyway (see the Javadoc on those classes); this file is one more
 * line in the same change.
 *
 * ## This is not authorization
 *
 * ADR-0005: the server enforces every permission independently, and hiding a control here is a
 * convenience so that a user is not made to fill in a form that ends in a 403. A client that
 * ignored this list entirely would gain nothing.
 */
export const Permissions = {
  /** Reading the platform register of schools. `SchoolPermissions.SCHOOL_READ`. */
  SCHOOL_READ: 'school:school:read',
  /** Onboarding a campus. Held by no shipped role template — a platform-operator action. */
  SCHOOL_CREATE: 'school:school:create',
  /** Editing this school's own profile. `SchoolPermissions.SCHOOL_UPDATE`. */
  SCHOOL_UPDATE: 'school:school:update',

  /** Seeing the school's academic years. `AcademicsPermissions.SESSION_READ`. */
  SESSION_READ: 'academics:session:read',
  /** Adding and editing academic years, and moving the school into one. */
  SESSION_MANAGE: 'academics:session:manage',
  /** Seeing the ladder of classes and their sections. */
  CLASS_READ: 'academics:class:read',
  /** Adding, renaming, reordering, retiring and reinstating classes and sections. */
  CLASS_MANAGE: 'academics:class:manage',
  /** Seeing the subject catalogue. */
  SUBJECT_READ: 'academics:subject:read',
  /** Adding, renaming, recoding, retiring and reinstating subjects. */
  SUBJECT_MANAGE: 'academics:subject:manage',

  /** Seeing the student list and a child's record. `StudentPermissions.STUDENT_READ`. */
  STUDENT_READ: 'student:student:read',
  /**
   * Admitting a child, correcting their record, enrolling them, and the bulk import.
   *
   * One permission covers all four because the backend gates all four on it — see the Javadoc on
   * `StudentController.enrol` for why an enrolment is not a permission of its own.
   */
  STUDENT_MANAGE: 'student:student:manage',
  /**
   * Seeing the real value of a Restricted field on a student's record — caste, religion, category,
   * CWSN/disability, health details, or an APAAR id (ADR-0014). Separate from `STUDENT_READ` and
   * `STUDENT_MANAGE` on purpose: masking is only real if seeing the masked value takes a permission
   * neither of those implies. `StudentPermissions.STUDENT_REVEAL_RESTRICTED`.
   */
  STUDENT_REVEAL_RESTRICTED: 'student:student:reveal_restricted',
  /**
   * Downloading a CSV of students with every Restricted field included — caste, religion, category,
   * CWSN/disability status, health details and APAAR, not just whether each is recorded. Separate
   * from `STUDENT_REVEAL_RESTRICTED`: revealing one field on one screen and downloading a bulk file
   * of everyone's are different orders of consequence. Held by no shipped role template.
   * `StudentPermissions.STUDENT_EXPORT_UNMASKED`.
   */
  STUDENT_EXPORT_UNMASKED: 'student:student:export_unmasked',
  /** Seeing the guardian directory and the guardians on a child's record. */
  GUARDIAN_READ: 'student:guardian:read',
  /** Adding and correcting guardians, and attaching or detaching them from a child. */
  GUARDIAN_MANAGE: 'student:guardian:manage',

  /** Seeing who holds an account at this school. */
  USER_READ: 'identity:user:read',
  /**
   * Creating an account, deactivating or reactivating one, clearing a lockout, and issuing an
   * admin password reset. `IdentityPermissions.USER_MANAGE`, deliberately separate from
   * `ROLE_MANAGE`: a school may want someone who runs the office roster without also handing them
   * the ability to change what any role may do.
   */
  USER_MANAGE: 'identity:user:manage',
  /** Reading the permission catalogue and this school's roles, and deciding who holds them. */
  ROLE_MANAGE: 'identity:role:manage',

  /** Reading this school's audit log. `AuditPermissions.AUDIT_READ`. */
  AUDIT_READ: 'platform:audit:read',

  /** Seeing a student's documents and downloading their content. `DocumentPermissions.DOCUMENT_READ`. */
  DOCUMENT_READ: 'document:document:read',
  /** Uploading, editing, verifying and deleting a student's documents. */
  DOCUMENT_MANAGE: 'document:document:manage',
  /** Seeing a section's attendance for a date, and a student's history. `AttendancePermissions.MARK_READ`. */
  ATTENDANCE_READ: 'attendance:mark:read',
  /**
   * Marking or editing attendance while it is still within its edit window, and filing a
   * correction request once it has locked. `AttendancePermissions.MARK_MANAGE`.
   */
  ATTENDANCE_MANAGE: 'attendance:mark:manage',
  /** Approving or rejecting a correction request. `AttendancePermissions.CORRECTION_APPROVE`. */
  ATTENDANCE_CORRECTION_APPROVE: 'attendance:correction:approve',
  /** Seeing the leave request queue and one request's own screen. `AttendancePermissions.LEAVE_READ`. */
  ATTENDANCE_LEAVE_READ: 'attendance:leave:read',
  /** Filing a leave request for a student on a section's roster. `AttendancePermissions.LEAVE_REQUEST`. */
  ATTENDANCE_LEAVE_REQUEST: 'attendance:leave:request',
  /** Approving or rejecting a leave request. `AttendancePermissions.LEAVE_APPROVE`. */
  ATTENDANCE_LEAVE_APPROVE: 'attendance:leave:approve',

  /**
   * Seeing the enquiry list, one enquiry's detail and follow-up history, and the due-date
   * follow-up queue. `AdmissionPermissions.ENQUIRY_READ`.
   */
  ADMISSION_ENQUIRY_READ: 'admission:enquiry:read',
  /**
   * Capturing an enquiry, assigning or reassigning its counsellor, and logging a follow-up.
   * `AdmissionPermissions.ENQUIRY_MANAGE`.
   */
  ADMISSION_ENQUIRY_MANAGE: 'admission:enquiry:manage',
  /** Seeing the school's catalogue of fee heads. `FeePermissions.HEAD_READ`. */
  FEE_HEAD_READ: 'fee:head:read',
  /** Adding, renaming, recategorising, capping and deactivating fee heads. */
  FEE_HEAD_MANAGE: 'fee:head:manage',
  /** Seeing the school's catalogue of concession types. `FeePermissions.CONCESSION_TYPE_READ`. */
  FEE_CONCESSION_TYPE_READ: 'fee:concession_type:read',
  /** Adding, renaming and deactivating concession types. Applying one to a student is not built yet. */
  FEE_CONCESSION_TYPE_MANAGE: 'fee:concession_type:manage',
  /** Seeing a class's fee structure for a session. `FeePermissions.STRUCTURE_READ`. */
  FEE_STRUCTURE_READ: 'fee:structure:read',
  /** Writing a new version of a class's fee structure, and copying one from a previous session. */
  FEE_STRUCTURE_MANAGE: 'fee:structure:manage',

  /**
   * Reading circulars, their targets and per-recipient status, and the target-preview count shown
   * while composing one. `CommunicationPermissions.CIRCULAR_READ` — deliberately this module's own
   * permission, never borrowed from `fee` or `attendance`, because the targeting query it gates is
   * Confidential-adjacent in its own right (ADR-0014).
   */
  COMMUNICATION_READ: 'communication:circular:read',
  /** Composing a circular, adding its targets, and publishing it. `CommunicationPermissions.CIRCULAR_MANAGE`. */
  COMMUNICATION_MANAGE: 'communication:circular:manage',
  /**
   * Recording that a recipient's family acknowledged a circular, on their behalf — there is no
   * parent login yet to do this themselves. `CommunicationPermissions.CIRCULAR_ACKNOWLEDGE`.
   */
  COMMUNICATION_ACKNOWLEDGE: 'communication:circular:acknowledge',
} as const;

/** One of the codes above. Nothing else may be passed to a permission check. */
export type Permission = (typeof Permissions)[keyof typeof Permissions];
