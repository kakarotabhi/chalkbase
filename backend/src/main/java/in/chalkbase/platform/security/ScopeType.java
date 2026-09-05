package in.chalkbase.platform.security;

/**
 * What a grant is scoped to (ADR-0005, FR-005).
 *
 * <p>Mirrors {@code ck_user_role_grant_scope} with one deliberate difference: {@link #WARD} is not
 * in that check constraint and never will be. A parent's reach is derived from the guardian-of
 * relationship, never assigned by an administrator — letting it be assignable is a data leak
 * waiting for a mis-click.
 */
public enum ScopeType {
    /** Everything in this school. */
    SCHOOL,
    CAMPUS,
    DEPARTMENT,
    CLASS,
    SECTION,
    SUBJECT,
    /** The holder's own records only. */
    SELF,
    /** The holder's children. Computed from student data; never stored in {@code user_role_grant}. */
    WARD
}
