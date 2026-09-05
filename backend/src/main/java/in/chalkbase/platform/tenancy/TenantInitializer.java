package in.chalkbase.platform.tenancy;

/**
 * Work a module needs done inside a school's schema once its migrations are current.
 *
 * <p>Flyway creates the tables; this fills the ones whose contents are shipped with the release
 * rather than written by a user — the permission catalogue and the role templates of ADR-0005.
 * That could not be a migration: the catalogue changes whenever a module declares a permission, and
 * a migration is immutable once merged.
 *
 * <p>It is an SPI in {@code platform} for the same reason {@code ConstraintMappingProvider} is: the
 * fan-out over tenants belongs to the shared kernel, the knowledge of what to seed belongs to the
 * module. The shared kernel must not import {@code identity}.
 *
 * <p><strong>Implementations must be idempotent</strong> — this runs at every startup for every
 * school, not only the first time — and must reach the schema through explicit qualification rather
 * than {@link TenantContext}. It runs before the entity manager factory exists, so JPA is not
 * available to it; use {@code JdbcClient} and schema-qualify with {@link SchemaName#requireValid}.
 *
 * <p>Order matters and is expressed with {@code @Order}: a role template cannot reference a
 * permission that has not been seeded yet.
 */
@FunctionalInterface
public interface TenantInitializer {

    void initialize(String schema);
}
