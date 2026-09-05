package in.chalkbase.platform.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Points one pooled connection at one school's schema.
 *
 * <p>There is a single {@link DataSource} for every tenant — that is the advantage of schema-per-tenant
 * over database-per-tenant, and the reason one application can serve every school without a
 * connection pool per school.
 *
 * <p><strong>The reset in {@link #releaseConnection} is the important line in this class.</strong> A
 * pooled connection keeps its {@code search_path} when it goes back to the pool, so without the
 * reset the next request — a different school — reads the previous school's rows. Silently: no
 * error, nothing in a log. This was reproduced against the real database before ADR-0011 was
 * accepted, and {@code SearchPathIsolationTests} exists to keep it fixed.
 */
@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    /**
     * {@code SET search_path TO ?} is not valid SQL — an identifier cannot be a bind parameter. Its
     * function form takes the value as a normal parameter, which removes the injection surface
     * entirely rather than relying on validation alone.
     */
    private static final String SET_SEARCH_PATH = "select set_config('search_path', ?, false)";

    private final transient DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        applySearchPath(connection, TenantContext.PLATFORM);
        connection.close();
    }

    @Override
    public Connection getConnection(String schema) throws SQLException {
        String target = targetSchema(schema);
        Connection connection = dataSource.getConnection();
        try {
            applySearchPath(connection, target);
        } catch (SQLException | RuntimeException ex) {
            // The connection is already out of the pool. Without this it never goes back, and a
            // handful of failures exhaust the pool — which is exactly how this was found.
            connection.close();
            throw ex;
        }
        return connection;
    }

    /**
     * Hibernate asks for `public` whenever the work is not tenant-scoped — onboarding a school,
     * reading the registry. That is a legitimate target even though no school may be called `public`.
     */
    private static String targetSchema(String schema) {
        return TenantContext.PLATFORM.equals(schema) ? TenantContext.PLATFORM : SchemaName.requireValid(schema);
    }

    @Override
    public void releaseConnection(String schema, Connection connection) throws SQLException {
        try {
            // Never skip this, and never make it conditional on the tenant.
            applySearchPath(connection, TenantContext.PLATFORM);
        } finally {
            // A connection that could not be reset must still go back, or a failing database
            // drains the pool instead of failing one request.
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        // Aggressive release would return the connection between statements in one transaction,
        // and the search_path would have to be re-applied each time.
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> type) {
        return MultiTenantConnectionProvider.class.equals(type)
                || SchemaMultiTenantConnectionProvider.class.equals(type);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return type.cast(this);
    }

    private void applySearchPath(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_SEARCH_PATH)) {
            statement.setString(1, schema);
            statement.execute();
        }
    }
}
