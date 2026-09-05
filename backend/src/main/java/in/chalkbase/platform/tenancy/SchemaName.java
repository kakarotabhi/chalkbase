package in.chalkbase.platform.tenancy;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation for a tenant's schema name.
 *
 * <p>A schema name reaches PostgreSQL as part of a session setting rather than as a bind parameter
 * in the usual sense, so it is validated before it is ever used. {@link
 * SchemaMultiTenantConnectionProvider} additionally passes it through {@code set_config}, which does
 * bind it — the two together mean an injection would have to defeat both.
 *
 * <p>The pattern is mirrored by a check constraint on {@code public.school}.
 */
public final class SchemaName {

    /** PostgreSQL truncates identifiers at 63 bytes, so anything longer is silently a different schema. */
    public static final int MAX_LENGTH = 63;

    public static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,62}$");

    /** `public` plus the schemas PostgreSQL and Supabase manage. A tenant may never occupy one. */
    private static final Set<String> RESERVED = Set.of(
            "public",
            "information_schema",
            "auth",
            "storage",
            "realtime",
            "graphql",
            "graphql_public",
            "vault",
            "extensions",
            "cron",
            "net",
            "pgsodium",
            "supabase_functions");

    private SchemaName() {}

    /**
     * @throws IllegalArgumentException if the name could not safely or unambiguously be a tenant schema
     */
    public static String requireValid(String name) {
        if (name == null || !PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Not a usable schema name: " + name + " (expected " + PATTERN.pattern() + ")");
        }
        if (RESERVED.contains(name) || name.startsWith("pg_")) {
            throw new IllegalArgumentException("Reserved schema name: " + name);
        }
        return name;
    }

    public static boolean isValid(String name) {
        try {
            requireValid(name);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
