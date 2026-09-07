package in.chalkbase.platform.tenancy;

/**
 * Creates the one account a freshly provisioned school needs before anyone can sign in to it
 * (ADR-0024).
 *
 * <p>An SPI in {@code platform} for the same reason {@link TenantInitializer} is: the shared kernel
 * must not import {@code identity}, but bootstrapping a school is not complete until identity has
 * done its part — {@link SchoolProvisioning} fills a schema's tables with what a release ships,
 * this fills the one row a release cannot: a person. {@code identity} provides the implementation
 * bean; {@code school} is the only caller, and reaches it only through this interface.
 *
 * <p><strong>Refuses rather than duplicating.</strong> A schema that already has a {@code
 * user_account} row has already been bootstrapped, by this call or by a school administrator
 * creating further accounts since — either way, creating a second "first" administrator here would
 * be a silent backdoor into every school. Implementations must check this before writing anything,
 * inside the same transaction as the check, and throw rather than proceed.
 *
 * <p>The caller must have the tenant bound ({@link TenantContext}) before calling this — the same
 * requirement {@code identity.application.AuthenticationService} documents for its own
 * tenant-scoped collaborators, and for the same reason: the account this creates lives inside the
 * school's schema, and Hibernate picks the tenant when the transaction opens, not per statement.
 */
public interface FirstAdminProvisioner {

    /**
     * @param schema the tenant schema, already migrated and holding its role templates
     *     ({@link SchoolProvisioning#provision})
     * @param username the username the administrator will sign in with
     * @param displayName the administrator's name
     * @return the created account and its one-time temporary password
     * @throws in.chalkbase.platform.error.ChalkbaseException if {@code schema} already has an
     *     account
     */
    FirstAdminAccount provisionFirstAdmin(String schema, String username, String displayName);
}
