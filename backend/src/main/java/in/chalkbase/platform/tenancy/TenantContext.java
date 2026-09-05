package in.chalkbase.platform.tenancy;

import java.util.Optional;

/**
 * The schema the current request works in.
 *
 * <p>Set once per request from the authenticated session, never from a request parameter (ADR-0011).
 * Feature code does not read this: it is consumed by {@link TenantIdentifierResolver}, so entities
 * and repositories carry no tenancy at all.
 *
 * <p>Unset means {@link #PLATFORM} — the {@code public} schema, where the tenant registry lives.
 * Onboarding a school and listing schools legitimately run without a tenant.
 */
public final class TenantContext {

    /** The `public` schema: the registry and shared reference data, not any school's data. */
    public static final String PLATFORM = "public";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<String> currentSchema() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current tenant's schema, or {@link #PLATFORM} when the work is not tenant-scoped. */
    public static String currentSchemaOrPlatform() {
        return CURRENT.get() == null ? PLATFORM : CURRENT.get();
    }

    public static void set(String schema) {
        CURRENT.set(SchemaName.requireValid(schema));
    }

    /** Runs {@code work} against one tenant, restoring whatever was bound before. */
    public static <T> T callWith(String schema, java.util.concurrent.Callable<T> work) throws Exception {
        String previous = CURRENT.get();
        set(schema);
        try {
            return work.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}
