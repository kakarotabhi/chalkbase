package in.chalkbase.platform.audit;

/**
 * The verbs an audit row records (ADR-0018, FR-008).
 *
 * <p><strong>String constants rather than an enum, deliberately.</strong> The column is
 * {@code varchar(60)} with no check constraint, while {@code outcome} has one — that asymmetry in
 * the migration is the decision, written down in SQL. An enum here would become the one file in the
 * shared kernel that all sixteen modules edit to add their own verbs: a permanent merge conflict,
 * and domain knowledge living in the platform, which is exactly what
 * {@code platform.security.PermissionProvider} and {@code platform.navigation.NavigationProvider}
 * exist to avoid. A module names its own action at the call site; these are the cross-cutting ones
 * every module shares.
 *
 * <p>The cost is that a typo is not a compile error. {@link AuditService} caps and rejects a blank
 * action, and the constants below are what every shipped call site uses.
 */
public final class AuditAction {

    // ── Security events. Recorded in their own transaction; see AuditService#recordSecurityEvent ──

    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    /**
     * An export of protected data. A security event rather than a data change: ADR-0014 makes an
     * export the moment masked data leaves the building, and it must be recorded whether or not the
     * request that asked for it went on to succeed.
     */
    public static final String DATA_EXPORTED = "DATA_EXPORTED";

    // ── Data changes. Recorded in the caller's transaction; see AuditService#recordChange ─────────

    public static final String ENTITY_CREATED = "ENTITY_CREATED";
    public static final String ENTITY_UPDATED = "ENTITY_UPDATED";
    public static final String ENTITY_DELETED = "ENTITY_DELETED";

    /** Longest an action may be: {@code audit_event.action} is {@code varchar(60)}. */
    public static final int MAX_LENGTH = 60;

    private AuditAction() {}
}
