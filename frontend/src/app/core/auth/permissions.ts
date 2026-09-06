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

  /** Seeing the student list and a child's record. `StudentPermissions.STUDENT_READ`. */
  STUDENT_READ: 'student:student:read',
  /**
   * Admitting a child, correcting their record, enrolling them, and the bulk import.
   *
   * One permission covers all four because the backend gates all four on it — see the Javadoc on
   * `StudentController.enrol` for why an enrolment is not a permission of its own.
   */
  STUDENT_MANAGE: 'student:student:manage',
  /** Seeing the guardian directory and the guardians on a child's record. */
  GUARDIAN_READ: 'student:guardian:read',
  /** Adding and correcting guardians, and attaching or detaching them from a child. */
  GUARDIAN_MANAGE: 'student:guardian:manage',

  /** Seeing who holds an account at this school. */
  USER_READ: 'identity:user:read',
  /** Reading the permission catalogue and this school's roles, and deciding who holds them. */
  ROLE_MANAGE: 'identity:role:manage',

  /** Reading this school's audit log. `AuditPermissions.AUDIT_READ`. */
  AUDIT_READ: 'platform:audit:read',
} as const;

/** One of the codes above. Nothing else may be passed to a permission check. */
export type Permission = (typeof Permissions)[keyof typeof Permissions];
