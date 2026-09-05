package in.chalkbase.platform.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the school (tenant) the current request acts on.
 *
 * <p>Every tenant-scoped query goes through this rather than reading a school id off a request
 * parameter — see docs/architecture/adr/0002-multi-tenancy-strategy.md. Today the value is set by a
 * request filter; if a school is later moved onto its own database, the routing DataSource reads
 * the same context and no calling code changes.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<UUID> currentSchoolId() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireSchoolId() {
        return currentSchoolId().orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }

    public static void set(UUID schoolId) {
        CURRENT.set(schoolId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
