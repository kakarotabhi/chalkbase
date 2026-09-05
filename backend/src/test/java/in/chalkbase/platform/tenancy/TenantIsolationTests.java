package in.chalkbase.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import in.chalkbase.school.domain.AcademicSession;
import in.chalkbase.school.infrastructure.AcademicSessionRepository;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The tests that make schema-per-tenant trustworthy.
 *
 * <p>Two tenants, the same entity, the same repository call — and each must see only its own rows.
 * The third test covers the failure this design is most likely to develop in practice: a pooled
 * connection carrying one school's {@code search_path} to the next request.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantIsolationTests {

    private static final String GREENFIELD = "greenfield";
    private static final String SUNRISE = "sunrise";

    @Autowired
    SchoolProvisioning provisioning;

    @Autowired
    AcademicSessionRepository sessions;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SchemaMultiTenantConnectionProvider provider;

    @BeforeEach
    void provisionBothSchools() {
        provisioning.provision(GREENFIELD);
        provisioning.provision(SUNRISE);
        for (String schema : List.of(GREENFIELD, SUNRISE)) {
            inTenant(schema, () -> {
                sessions.deleteAll();
                return null;
            });
        }
    }

    @Test
    void eachSchoolSeesOnlyItsOwnRows() throws Exception {
        inTenant(GREENFIELD, () -> sessions.save(session("2026-27")));
        inTenant(GREENFIELD, () -> sessions.save(session("2027-28")));
        inTenant(SUNRISE, () -> sessions.save(session("2026-27")));

        assertThat(inTenant(GREENFIELD, () -> sessions.findAll()).stream().map(AcademicSession::getName))
                .containsExactlyInAnyOrder("2026-27", "2027-28");
        assertThat(inTenant(SUNRISE, () -> sessions.findAll()).stream().map(AcademicSession::getName))
                .containsExactly("2026-27");
    }

    /**
     * The same name is unique per school, not globally — which is only true because the constraint
     * lives in each school's own schema.
     */
    @Test
    void theSameSessionNameMayExistInBothSchools() throws Exception {
        inTenant(GREENFIELD, () -> sessions.save(session("2026-27")));
        inTenant(SUNRISE, () -> sessions.save(session("2026-27")));

        assertThat(inTenant(GREENFIELD, () -> sessions.count())).isEqualTo(1);
        assertThat(inTenant(SUNRISE, () -> sessions.count())).isEqualTo(1);
    }

    /**
     * A connection must never carry a tenant's search_path back into the pool. Without the reset in
     * {@code releaseConnection}, the next caller reads the previous school's rows — silently.
     */
    @Test
    void aReleasedConnectionCarriesNoTenantBackToThePool() throws Exception {
        inTenant(SUNRISE, () -> sessions.save(session("2026-27")));

        // Take a connection straight from the pool, as the next unrelated request would.
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rs = statement.executeQuery("show search_path")) {
            rs.next();
            assertThat(rs.getString(1)).doesNotContain(SUNRISE).doesNotContain(GREENFIELD);
        }
    }

    /**
     * A rejected schema must not cost a connection. Before this was fixed, each failure leaked one —
     * ten of them emptied the pool and every later request timed out after 30 seconds.
     */
    @Test
    void aRejectedSchemaDoesNotLeakAConnection() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                provider.getConnection("pg_catalog").close();
                throw new AssertionError("expected a reserved schema to be rejected");
            } catch (IllegalArgumentException expected) {
                // the point is that the pool survives this, not the message
            }
        }

        // The pool must still hand out a connection immediately.
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    /** Work with no tenant bound is legitimate — the registry lives in `public`. */
    @Test
    void theProviderServesThePlatformSchema() throws Exception {
        try (var connection = provider.getConnection(TenantContext.PLATFORM);
                var statement = connection.createStatement();
                var rs = statement.executeQuery("show search_path")) {
            rs.next();
            assertThat(rs.getString(1)).contains(TenantContext.PLATFORM);
        }
    }

    @Test
    void theRegistryIsNotInsideATenantSchema() {
        Boolean inPublic = jdbc.sql("select exists (select 1 from information_schema.tables"
                        + " where table_schema = 'public' and table_name = 'school')")
                .query(Boolean.class)
                .single();
        Boolean inTenantSchema = jdbc.sql("select exists (select 1 from information_schema.tables"
                        + " where table_schema = ? and table_name = 'school')")
                .param(GREENFIELD)
                .query(Boolean.class)
                .single();

        assertThat(inPublic).as("registry lives in public").isTrue();
        assertThat(inTenantSchema).as("registry is not duplicated per tenant").isFalse();
    }

    private static AcademicSession session(String name) {
        return new AcademicSession(name, LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
    }

    private <T> T inTenant(String schema, java.util.concurrent.Callable<T> work) {
        try {
            return TenantContext.callWith(schema, work);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
