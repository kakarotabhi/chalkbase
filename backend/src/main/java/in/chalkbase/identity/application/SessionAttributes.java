package in.chalkbase.identity.application;

/**
 * The names identity stores on the HTTP session.
 *
 * <p>The session lives in {@code public} and carries no personal data — a schema name and a user
 * id, nothing else (ADR-0017). {@code SCHEMA} is what breaks the circular dependency: the cookie
 * has to resolve to a school before any tenant-scoped table can be read.
 */
public final class SessionAttributes {

    /** The tenant schema this session is bound to. Read on every request before anything else. */
    public static final String SCHEMA = "chalkbase.schema";

    /** The authenticated {@code user_account.id}, within that schema. */
    public static final String USER_ID = "chalkbase.userId";

    private SessionAttributes() {}
}
