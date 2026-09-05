package in.chalkbase.platform.tenancy;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Which schools exist, and which schema each one lives in.
 *
 * <p>Reads {@code public.school} with plain JDBC rather than JPA, because the migration
 * orchestrator needs this before the entity manager is built.
 */
@Component
public class TenantRegistry {

    private final JdbcClient jdbc;

    public TenantRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Schemas of every active school, in a stable order so a failed run is reproducible. */
    public List<String> activeSchemas() {
        return jdbc.sql("select schema_name from public.school where active order by schema_name")
                .query(String.class)
                .list();
    }

    public boolean schemaExists(String schema) {
        return jdbc.sql("select exists (select 1 from information_schema.schemata where schema_name = ?)")
                .param(SchemaName.requireValid(schema))
                .query(Boolean.class)
                .single();
    }
}
